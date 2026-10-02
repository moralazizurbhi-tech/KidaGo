package com.afede.kidago.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/** One line of the current list. `seq` is the insertion sequence; `code` is unique (one line per barcode). */
@Entity(tableName = "list_entry", indices = [Index("code", unique = true)])
data class ListEntry(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val code: String,
    val quantity: Int,
)

/** Append-only safety copy of a list that was reset. */
@Entity(tableName = "history_record")
data class HistoryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val archivedAt: Long,
    val reason: ArchiveReason,
)

@Entity(
    tableName = "history_entry",
    foreignKeys = [ForeignKey(HistoryRecord::class, ["id"], ["recordId"])],
    indices = [Index("recordId")],
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val code: String,
    val quantity: Int,
    @ColumnInfo(defaultValue = "0") val position: Int,
)

enum class ArchiveReason { EXPORT, MANUAL }

data class AddResult(val quantityBefore: Int, val quantityAfter: Int, val wasExisting: Boolean)

@Dao
interface TodaysListDao {
    @Query("SELECT * FROM list_entry ORDER BY seq")
    fun observeEntries(): Flow<List<ListEntry>>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM list_entry")
    fun observeTotal(): Flow<Int>

    @Query("SELECT * FROM list_entry ORDER BY seq")
    suspend fun entries(): List<ListEntry>

    @Query("SELECT * FROM list_entry WHERE code = :code")
    suspend fun find(code: String): ListEntry?

    @Insert
    suspend fun insert(entry: ListEntry)

    @Query("UPDATE list_entry SET quantity = :quantity WHERE code = :code")
    suspend fun setQuantity(code: String, quantity: Int): Int

    @Query("DELETE FROM list_entry WHERE code = :code")
    suspend fun delete(code: String)

    @Query("DELETE FROM list_entry")
    suspend fun deleteAll()

    @Insert
    suspend fun insertRecord(record: HistoryRecord): Long

    @Insert
    suspend fun insertHistoryEntries(entries: List<HistoryEntry>)

    @Query("SELECT * FROM history_record ORDER BY id")
    suspend fun history(): List<HistoryRecord>

    @Query("SELECT * FROM history_entry WHERE recordId = :recordId ORDER BY position")
    suspend fun historyEntries(recordId: Long): List<HistoryEntry>
}

/** Owns the current list and its history (FEAT-002 TD). Every multi-step operation is one transaction. */
class TodaysListEntryStore(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.todaysListDao()

    /** Ordered current list, insertion order. */
    val entries: Flow<List<ListEntry>> = dao.observeEntries()

    /** Sum of all quantities; 0 for an empty list. */
    val total: Flow<Int> = dao.observeTotal()

    /** Add 1 to the code's line, or create it at 1. The code is matched exactly as captured. */
    suspend fun addOrAccumulate(code: String): AddResult = db.withTransaction {
        val existing = dao.find(code)
        if (existing == null) {
            dao.insert(ListEntry(code = code, quantity = 1))
            AddResult(0, 1, wasExisting = false)
        } else {
            dao.setQuantity(code, existing.quantity + 1)
            AddResult(existing.quantity, existing.quantity + 1, wasExisting = true)
        }
    }

    /**
     * Cantidad: set the line to quantityBefore + n (replaces the scan's +1, so resubmitting replaces, never adds).
     * The 1..99 range is validated by CaptureCoordinator. Returns false if the line no longer exists.
     */
    suspend fun applyQuantity(code: String, quantityBefore: Int, n: Int): Boolean =
        dao.setQuantity(code, quantityBefore + n) > 0

    suspend fun remove(code: String) = dao.delete(code)

    /** Copy the whole list into a new history record and empty it, atomically. False (no record) when empty. */
    suspend fun archiveAndClear(reason: ArchiveReason): Boolean = db.withTransaction {
        val current = dao.entries()
        if (current.isEmpty()) return@withTransaction false
        val recordId = dao.insertRecord(HistoryRecord(archivedAt = clock(), reason = reason))
        dao.insertHistoryEntries(
            current.mapIndexed { i, e -> HistoryEntry(recordId = recordId, code = e.code, quantity = e.quantity, position = i) },
        )
        dao.deleteAll()
        true
    }

    suspend fun history(): List<HistoryRecord> = dao.history()

    suspend fun historyEntries(recordId: Long): List<HistoryEntry> = dao.historyEntries(recordId)
}
