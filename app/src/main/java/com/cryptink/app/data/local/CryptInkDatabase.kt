package com.cryptink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [JournalEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CryptInkDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
}