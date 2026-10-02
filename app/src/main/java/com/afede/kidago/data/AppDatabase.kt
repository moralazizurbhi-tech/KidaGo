package com.afede.kidago.data

import androidx.room.Database
import androidx.room.RoomDatabase

// Shared database for the catalog and the list.
@Database(
    entities = [CatalogItem::class, ListEntry::class, HistoryRecord::class, HistoryEntry::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun todaysListDao(): TodaysListDao
}
