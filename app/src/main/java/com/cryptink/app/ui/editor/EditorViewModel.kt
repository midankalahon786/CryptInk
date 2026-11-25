package com.cryptink.app.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptink.app.core.crypto.KeySessionManager
import com.cryptink.app.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val entryId: Long? = null,
    val title: String = "",
    val body: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: JournalRepository,
    private val sessionManager: KeySessionManager
) : ViewModel() {

    private val entryIdArg: Long? = savedStateHandle.get<Long>("entryId")?.takeIf { it > 0L }

    private val _uiState = MutableStateFlow(EditorUiState(entryId = entryIdArg))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        if (entryIdArg != null) {
            viewModelScope.launch {
                val existing = repository.getDecryptedEntry(entryIdArg)
                if (existing != null && existing.isDecrypted) {
                    _uiState.update {
                        it.copy(title = existing.title, body = existing.body)
                    }
                }
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun onBodyChanged(newBody: String) {
        _uiState.update { it.copy(body = newBody) }
    }

    fun saveEntry(onSaved: () -> Unit) {
        val current = _uiState.value
        val activePassword = sessionManager.sessionState.value.activePassword

        if (activePassword == null) {
            _uiState.update { it.copy(error = "Session locked. Re-authenticate to save.") }
            return
        }

        if (current.title.isBlank() && current.body.isBlank()) {
            onSaved()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = if (current.entryId == null) {
                repository.createEntry(current.title, current.body, activePassword)
                    .map { Unit }
            } else {
                repository.updateEntry(current.entryId, current.title, current.body, activePassword)
            }

            result.fold(
                onSuccess = { onSaved() },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSaving = false, error = e.message ?: "Failed to encrypt entry")
                    }
                }
            )
        }
    }

    fun deleteEntry(onDeleted: () -> Unit) {
        val id = _uiState.value.entryId ?: return
        viewModelScope.launch {
            repository.deleteEntry(id)
            onDeleted()
        }
    }
}