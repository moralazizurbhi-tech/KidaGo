package com.afede.kidago.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Shared database for the catalog and the list.
@Database(
    entities = [CatalogItem::class, ListEntry::class, HistoryRecord::class, HistoryEntry::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun todaysListDao(): TodaysListDao

    companion object {
        /** Adds the optional price; the stored catalog, the list and its history stay as they are (FEAT-001 C27). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catalog_item ADD COLUMN priceCents INTEGER")
            }
        }
    }
}
