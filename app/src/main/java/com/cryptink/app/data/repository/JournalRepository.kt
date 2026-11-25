package com.cryptink.app.data.repository

import com.cryptink.app.core.crypto.CryptoManager
import com.cryptink.app.core.crypto.KeySessionManager
import com.cryptink.app.core.crypto.VaultSessionState
import com.cryptink.app.data.local.JournalDao
import com.cryptink.app.data.local.JournalEntity
import com.cryptink.app.domain.model.JournalEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val journalDao: JournalDao,
    private val cryptoManager: CryptoManager,
    private val sessionManager: KeySessionManager
) {

    /**
     * Emits journal entries in either scrambled Base64 form or decrypted plaintext
     * depending on the current in-memory session state.
     */
    fun observeJournalFeed(): Flow<List<JournalEntry>> {
        return combine(
            journalDao.observeAllEntries(),
            sessionManager.sessionState
        ) { entities, session ->
            mapEntitiesToUiModels(entities, session)
        }
    }

    private suspend fun mapEntitiesToUiModels(
        entities: List<JournalEntity>,
        session: VaultSessionState
    ): List<JournalEntry> = withContext(Dispatchers.Default) {
        val password =
            session.activePassword ?: return@withContext entities.map { it.toLockedModel() }

        coroutineScope {
            entities.map { entity ->
                async {
                    if (session.isEntryUnlocked(entity.id)) {
                        decryptEntityOrFallback(entity, password)
                    } else {
                        entity.toLockedModel()
                    }
                }
            }.awaitAll()
        }
    }

    private fun decryptEntityOrFallback(
        entity: JournalEntity,
        password: CharArray
    ): JournalEntry {
        return try {
            val saltBytes = cryptoManager.decodeBase64(entity.salt)
            val key = cryptoManager.deriveKey(password, saltBytes)
            val plainTitle = cryptoManager.decrypt(entity.encryptedTitle, entity.titleIv, key)
            val plainBody = cryptoManager.decrypt(entity.encryptedBody, entity.bodyIv, key)

            JournalEntry(
                id = entity.id,
                title = plainTitle,
                body = plainBody,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isDecrypted = true
            )
        } catch (e: Exception) {
            // If an entry was encrypted with a different passphrase, keep it safely scrambled
            entity.toLockedModel()
        }
    }

    /**
     * Verifies the passphrase against existing entries and unlocks either a single entry
     * or the entire feed.
     */
    suspend fun verifyAndUnlock(
        password: CharArray,
        targetEntryId: Long?,
        unlockAll: Boolean
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val verificationEntity = if (targetEntryId != null) {
                journalDao.getEntryById(targetEntryId)
            } else {
                journalDao.getLatestEntry()
            }

            if (verificationEntity != null) {
                val saltBytes = cryptoManager.decodeBase64(verificationEntity.salt)
                val key = cryptoManager.deriveKey(password, saltBytes)
                // Throws AEADBadTagException if the passphrase is incorrect
                cryptoManager.decrypt(
                    verificationEntity.encryptedTitle,
                    verificationEntity.titleIv,
                    key
                )
            }

            if (unlockAll) {
                sessionManager.unlockAll(password)
            } else if (targetEntryId != null) {
                sessionManager.unlockEntry(targetEntryId, password)
            } else {
                sessionManager.unlockAll(password)
            }

            Result.success(Unit)
        } catch (e: AEADBadTagException) {
            Result.failure(IllegalArgumentException("Incorrect passphrase", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Encrypts and inserts a new journal entry. Automatically reveals the newly created entry
     * in the current session.
     */
    suspend fun createEntry(
        title: String,
        body: String,
        password: CharArray
    ): Result<Long> = withContext(Dispatchers.Default) {
        try {
            val now = System.currentTimeMillis()
            val salt = cryptoManager.generateSalt()
            val key = cryptoManager.deriveKey(password, salt)

            val encryptedTitlePayload = cryptoManager.encrypt(title, key, salt)
            val encryptedBodyPayload = cryptoManager.encrypt(body, key, salt)

            val entity = JournalEntity(
                createdAt = now,
                updatedAt = now,
                salt = encryptedTitlePayload.salt,
                titleIv = encryptedTitlePayload.iv,
                encryptedTitle = encryptedTitlePayload.cipherText,
                bodyIv = encryptedBodyPayload.iv,
                encryptedBody = encryptedBodyPayload.cipherText
            )

            val newId = journalDao.insertEntry(entity)

            if (!sessionManager.sessionState.value.isSessionAuthenticated) {
                sessionManager.unlockEntry(newId, password)
            } else {
                sessionManager.revealAdditionalEntry(newId)
            }

            Result.success(newId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Re-encrypts an edited entry with a fresh salt and fresh IVs.
     */
    suspend fun updateEntry(
        entryId: Long,
        newTitle: String,
        newBody: String,
        password: CharArray
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val existing = journalDao.getEntryById(entryId)
                ?: return@withContext Result.failure(NoSuchElementException("Entry not found"))

            val salt = cryptoManager.generateSalt()
            val key = cryptoManager.deriveKey(password, salt)

            val encryptedTitlePayload = cryptoManager.encrypt(newTitle, key, salt)
            val encryptedBodyPayload = cryptoManager.encrypt(newBody, key, salt)

            val updatedEntity = existing.copy(
                updatedAt = System.currentTimeMillis(),
                salt = encryptedTitlePayload.salt,
                titleIv = encryptedTitlePayload.iv,
                encryptedTitle = encryptedTitlePayload.cipherText,
                bodyIv = encryptedBodyPayload.iv,
                encryptedBody = encryptedBodyPayload.cipherText
            )

            journalDao.updateEntry(updatedEntity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDecryptedEntry(entryId: Long): JournalEntry? = withContext(Dispatchers.Default) {
        val entity = journalDao.getEntryById(entryId) ?: return@withContext null
        val password = sessionManager.sessionState.value.activePassword
        if (password != null && sessionManager.sessionState.value.isEntryUnlocked(entryId)) {
            decryptEntityOrFallback(entity, password)
        } else {
            entity.toLockedModel()
        }
    }

    suspend fun deleteEntry(entryId: Long) {
        val entity = journalDao.getEntryById(entryId) ?: return
        journalDao.deleteEntry(entity)
        sessionManager.lockEntry(entryId)
    }

    private fun JournalEntity.toLockedModel(): JournalEntry {
        return JournalEntry(
            id = id,
            title = encryptedTitle,
            body = encryptedBody,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isDecrypted = false
        )
    }
}