package com.cryptink.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoManagerTest {

    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setup() {
        cryptoManager = CryptoManager()
    }

    @Test
    fun `encrypt and decrypt round trip returns original plaintext`() {
        val password = "CorrectHorseBatteryStaple!".toCharArray()
        val salt = cryptoManager.generateSalt()
        val key = cryptoManager.deriveKey(password, salt)
        val originalText = "Secret diary entry: Today I started building CryptInk."

        val encrypted = cryptoManager.encrypt(originalText, key, salt)
        val decrypted = cryptoManager.decrypt(encrypted.cipherText, encrypted.iv, key)

        assertNotEquals(originalText, encrypted.cipherText)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun `encrypting identical plaintext twice produces different ciphertexts due to random IV`() {
        val password = "MySecretPassword123".toCharArray()
        val salt = cryptoManager.generateSalt()
        val key = cryptoManager.deriveKey(password, salt)
        val text = "Same secret message"

        val first = cryptoManager.encrypt(text, key, salt)
        val second = cryptoManager.encrypt(text, key, salt)

        assertNotEquals(first.iv, second.iv)
        assertNotEquals(first.cipherText, second.cipherText)
    }

    @Test
    fun `decrypting with wrong password throws AEADBadTagException`() {
        val correctPassword = "RightPassword".toCharArray()
        val wrongPassword = "WrongPassword".toCharArray()
        val salt = cryptoManager.generateSalt()

        val correctKey = cryptoManager.deriveKey(correctPassword, salt)
        val wrongKey = cryptoManager.deriveKey(wrongPassword, salt)

        val encrypted = cryptoManager.encrypt("Top secret notes", correctKey, salt)

        assertThrows(AEADBadTagException::class.java) {
            cryptoManager.decrypt(encrypted.cipherText, encrypted.iv, wrongKey)
        }
    }
}