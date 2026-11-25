package com.cryptink.app.core.crypto

data class EncryptedPayload(
    val cipherText: String, // Base64 encoded
    val iv: String,         // Base64 encoded (12 bytes)
    val salt: String        // Base64 encoded (16 bytes)
)