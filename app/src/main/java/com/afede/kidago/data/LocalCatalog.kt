package com.afede.kidago.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction

/** One catalog record: the barcode and, when the file gives one, its price in cents (FEAT-001 C24). */
@Entity(tableName = "catalog_item")
data class CatalogItem(@PrimaryKey val barcode: String, val priceCents: Long? = null)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_item WHERE barcode = :barcode")
    suspend fun find(barcode: String): CatalogItem?

    @Query("SELECT EXISTS(SELECT 1 FROM catalog_item)")
    suspend fun hasAny(): Boolean

    @Query("DELETE FROM catalog_item")
    suspend fun deleteAll()

    // IGNORE: a code repeated in a file keeps its first record, price included (C26).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CatalogItem>)
}

/** The currently applied catalog and its barcode lookups (FEAT-001 TD). */
class LocalCatalogStore(private val db: AppDatabase) {
    private val dao = db.catalogDao()

    /** The record for [barcode], matched exactly, or null when it is not in the current catalog. */
    suspend fun find(barcode: String): CatalogItem? = dao.find(barcode)

    /** True once a catalog has been imported; an empty import never succeeds, so empty means none (C22). */
    suspend fun hasCatalog(): Boolean = dao.hasAny()

    /**
     * Atomic full replace: readers see the old catalog or the new one, never a mix. If [items] throws
     * part-way, the transaction rolls back and the previous catalog stays.
     */
    suspend fun replaceAll(items: Iterable<CatalogItem>) = db.withTransaction {
        dao.deleteAll()
        items.chunked(INSERT_CHUNK).forEach { dao.insertAll(it) }
    }

    private companion object {
        const val INSERT_CHUNK = 5000
    }
}
