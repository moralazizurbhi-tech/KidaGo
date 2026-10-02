package com.afede.kidago.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction

/** One catalog record. Barcode only in this version; the primary key makes lookups indexed. */
@Entity(tableName = "catalog_item")
data class CatalogItem(@PrimaryKey val barcode: String)

@Dao
interface CatalogDao {
    @Query("SELECT EXISTS(SELECT 1 FROM catalog_item WHERE barcode = :barcode)")
    suspend fun contains(barcode: String): Boolean

    @Query("DELETE FROM catalog_item")
    suspend fun deleteAll()

    // IGNORE: a repeated barcode in a file is harmless for a barcode-only catalog.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CatalogItem>)
}

/** The currently applied catalog and its barcode lookups (FEAT-001 TD). */
class LocalCatalogStore(private val db: AppDatabase) {
    private val dao = db.catalogDao()

    /** Found / not found against the current catalog, barcode matched exactly. */
    suspend fun contains(barcode: String): Boolean = dao.contains(barcode)

    /**
     * Atomic full replace: readers see the old catalog or the new one, never a mix. If [barcodes] throws
     * part-way, the transaction rolls back and the previous catalog stays.
     */
    suspend fun replaceAll(barcodes: Iterable<String>) = db.withTransaction {
        dao.deleteAll()
        barcodes.chunked(INSERT_CHUNK).forEach { chunk -> dao.insertAll(chunk.map(::CatalogItem)) }
    }

    private companion object {
        const val INSERT_CHUNK = 5000
    }
}
