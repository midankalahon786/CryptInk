package com.cryptink.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
    val salt: String,           // Base64 (16 bytes) - unique per entry
    val titleIv: String,        // Base64 (12 bytes) - unique IV for title
    val encryptedTitle: String, // Base64 ciphertext
    val bodyIv: String,         // Base64 (12 bytes) - unique IV for body
    val encryptedBody: String   // Base64 ciphertext
)