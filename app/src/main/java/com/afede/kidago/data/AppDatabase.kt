package com.afede.kidago.data

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

// Implementation placeholder: Room requires at least one entity. The catalog (TASK-008) and
// list (TASK-009) entities replace this one when they add their tables to the shared database.
@Entity(tableName = "schema_info")
data class SchemaInfo(@PrimaryKey val id: Int = 1)

@Database(entities = [SchemaInfo::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase()
