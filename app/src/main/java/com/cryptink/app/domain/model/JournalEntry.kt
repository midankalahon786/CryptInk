package com.cryptink.app.domain.model

data class JournalEntry(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDecrypted: Boolean
)