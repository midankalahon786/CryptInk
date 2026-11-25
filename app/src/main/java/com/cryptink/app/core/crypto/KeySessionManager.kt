package com.cryptink.app.core.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

data class VaultSessionState(
    val activePassword: CharArray? = null,
    val isGlobalUnlock: Boolean = false,
    val unlockedEntryIds: Set<Long> = emptySet()
) {
    val isSessionAuthenticated: Boolean
        get() = activePassword != null

    fun isEntryUnlocked(entryId: Long): Boolean {
        return isSessionAuthenticated && (isGlobalUnlock || entryId in unlockedEntryIds)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultSessionState) return false
        return activePassword.contentEquals(other.activePassword) &&
                isGlobalUnlock == other.isGlobalUnlock &&
                unlockedEntryIds == other.unlockedEntryIds
    }

    override fun hashCode(): Int {
        var result = activePassword?.contentHashCode() ?: 0
        result = 31 * result + isGlobalUnlock.hashCode()
        result = 31 * result + unlockedEntryIds.hashCode()
        return result
    }
}

@Singleton
class KeySessionManager @Inject constructor() {

    private val _sessionState = MutableStateFlow(VaultSessionState())
    val sessionState: StateFlow<VaultSessionState> = _sessionState.asStateFlow()

    /**
     * Unlocks the entire journal feed for the active session.
     */
    fun unlockAll(password: CharArray) {
        val copy = password.copyOf()
        _sessionState.update { current ->
            current.activePassword?.fill('\u0000')
            VaultSessionState(
                activePassword = copy,
                isGlobalUnlock = true,
                unlockedEntryIds = emptySet()
            )
        }
    }

    /**
     * Unlocks only a specific entry ID while keeping all other entries scrambled.
     */
    fun unlockEntry(entryId: Long, password: CharArray) {
        val copy = password.copyOf()
        _sessionState.update { current ->
            val updatedIds = current.unlockedEntryIds + entryId
            current.activePassword?.fill('\u0000')
            current.copy(
                activePassword = copy,
                unlockedEntryIds = updatedIds
            )
        }
    }

    /**
     * Reveals an additional entry when the session already holds a verified password in RAM.
     */
    fun revealAdditionalEntry(entryId: Long) {
        _sessionState.update { current ->
            if (!current.isSessionAuthenticated) return@update current
            current.copy(unlockedEntryIds = current.unlockedEntryIds + entryId)
        }
    }

    /**
     * Hides a single previously unlocked entry without locking the whole session.
     */
    fun lockEntry(entryId: Long) {
        _sessionState.update { current ->
            val remaining = current.unlockedEntryIds - entryId
            if (!current.isGlobalUnlock && remaining.isEmpty()) {
                current.activePassword?.fill('\u0000')
                VaultSessionState()
            } else {
                current.copy(unlockedEntryIds = remaining)
            }
        }
    }

    /**
     * Immediately zeroes out the in-memory passphrase and resets all unlock states.
     */
    fun lockAll() {
        _sessionState.update { current ->
            current.activePassword?.fill('\u0000')
            VaultSessionState()
        }
    }
}