package com.cryptink.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptink.app.core.crypto.KeySessionManager
import com.cryptink.app.data.repository.JournalRepository
import com.cryptink.app.domain.model.JournalEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UnlockPromptTarget {
    data object GlobalSession : UnlockPromptTarget
    data class SingleEntry(val entryId: Long) : UnlockPromptTarget
    data class NewEntryCreation(val isFirstTimeSetup: Boolean) : UnlockPromptTarget
}

data class FeedUiState(
    val entries: List<JournalEntry> = emptyList(),
    val isSessionAuthenticated: Boolean = false,
    val isGlobalUnlock: Boolean = false,
    val promptTarget: UnlockPromptTarget? = null,
    val isVerifying: Boolean = false,
    val promptError: String? = null
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val sessionManager: KeySessionManager
) : ViewModel() {

    private val _promptState = MutableStateFlow(PromptInternalState())

    val uiState: StateFlow<FeedUiState> = combine(
        repository.observeJournalFeed(),
        sessionManager.sessionState,
        _promptState
    ) { entries, session, prompt ->
        FeedUiState(
            entries = entries,
            isSessionAuthenticated = session.isSessionAuthenticated,
            isGlobalUnlock = session.isGlobalUnlock,
            promptTarget = prompt.target,
            isVerifying = prompt.isVerifying,
            promptError = prompt.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FeedUiState()
    )

    fun onEntryTapped(entry: JournalEntry, onNavigateToEditor: (Long) -> Unit) {
        if (entry.isDecrypted) {
            onNavigateToEditor(entry.id)
            return
        }

        // If user already unlocked one entry in this session, reveal this card immediately
        if (sessionManager.sessionState.value.isSessionAuthenticated) {
            sessionManager.revealAdditionalEntry(entry.id)
        } else {
            _promptState.update {
                PromptInternalState(target = UnlockPromptTarget.SingleEntry(entry.id))
            }
        }
    }

    fun onGlobalUnlockTapped() {
        _promptState.update {
            PromptInternalState(target = UnlockPromptTarget.GlobalSession)
        }
    }

    fun onNewEntryTapped(onNavigateToNewEditor: () -> Unit) {
        if (sessionManager.sessionState.value.isSessionAuthenticated) {
            onNavigateToNewEditor()
        } else {
            val isVaultEmpty = uiState.value.entries.isEmpty()
            _promptState.update {
                PromptInternalState(
                    target = UnlockPromptTarget.NewEntryCreation(isFirstTimeSetup = isVaultEmpty)
                )
            }
        }
    }

    fun onLockAllTapped() {
        sessionManager.lockAll()
    }

    fun onLockSingleEntryTapped(entryId: Long) {
        sessionManager.lockEntry(entryId)
    }

    fun dismissPrompt() {
        _promptState.update { PromptInternalState() }
    }

    fun submitPassword(
        password: CharArray,
        unlockAll: Boolean,
        onNewEntryAuthorized: () -> Unit
    ) {
        val currentTarget = _promptState.value.target ?: return

        viewModelScope.launch {
            _promptState.update { it.copy(isVerifying = true, error = null) }

            val targetEntryId = (currentTarget as? UnlockPromptTarget.SingleEntry)?.entryId
            val shouldUnlockAll = when (currentTarget) {
                UnlockPromptTarget.GlobalSession -> true
                is UnlockPromptTarget.NewEntryCreation -> false
                is UnlockPromptTarget.SingleEntry -> unlockAll
            }

            val result = repository.verifyAndUnlock(
                password = password,
                targetEntryId = targetEntryId,
                unlockAll = shouldUnlockAll
            )

            // Zero out local password copy
            password.fill('\u0000')

            result.fold(
                onSuccess = {
                    _promptState.update { PromptInternalState() }
                    if (currentTarget is UnlockPromptTarget.NewEntryCreation) {
                        onNewEntryAuthorized()
                    }
                },
                onFailure = { throwable ->
                    _promptState.update {
                        it.copy(
                            isVerifying = false,
                            error = throwable.message ?: "Decryption failed"
                        )
                    }
                }
            )
        }
    }

    private data class PromptInternalState(
        val target: UnlockPromptTarget? = null,
        val isVerifying: Boolean = false,
        val error: String? = null
    )
}