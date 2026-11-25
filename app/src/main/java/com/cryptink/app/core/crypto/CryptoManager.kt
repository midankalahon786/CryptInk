package com.cryptink.app.core.crypto

import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManager @Inject constructor() {

    companion object {
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"

        private const val ITERATIONS = 310_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun generateIv(): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Derives a 256-bit AES key from a password and salt.
     * Callers should clear the returned SecretKey's encoded bytes when locking if possible.
     */
    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return try {
            SecretKeySpec(keyBytes, KEY_ALGORITHM)
        } finally {
            spec.clearPassword()
            Arrays.fill(keyBytes, 0.toByte())
        }
    }

    /**
     * Encrypts plaintext using a freshly generated 12-byte IV.
     */
    fun encrypt(plainText: String, secretKey: SecretKey, salt: ByteArray): EncryptedPayload {
        val iv = generateIv()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedPayload(
            cipherText = Base64.getEncoder().encodeToString(cipherBytes),
            iv = Base64.getEncoder().encodeToString(iv),
            salt = Base64.getEncoder().encodeToString(salt)
        )
    }

    /**
     * Encrypts plaintext using a specific pre-existing IV (useful when encrypting title + body
     * separately or testing deterministic vectors; prefer separate IVs per field in production).
     */
    fun encryptWithIv(plainText: String, secretKey: SecretKey, iv: ByteArray): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipherBytes)
    }

    /**
     * Decrypts a Base64-encoded ciphertext using the provided SecretKey and Base64 IV.
     * Throws AEADBadTagException if the key is wrong or data is corrupted.
     */
    fun decrypt(cipherTextBase64: String, ivBase64: String, secretKey: SecretKey): String {
        val cipherBytes = Base64.getDecoder().decode(cipherTextBase64)
        val iv = Base64.getDecoder().decode(ivBase64)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeBase64(base64: String): ByteArray = Base64.getDecoder().decode(base64)
}