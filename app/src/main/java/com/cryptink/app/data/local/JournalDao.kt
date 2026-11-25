package com.cryptink.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC")
    fun observeAllEntries(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): JournalEntity?

    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestEntry(): JournalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntity): Long

    @Update
    suspend fun updateEntry(entry: JournalEntity)

    @Delete
    suspend fun deleteEntry(entry: JournalEntity)
}