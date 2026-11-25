package com.cryptink.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cryptink.app.ui.components.JournalCard
import com.cryptink.app.ui.components.UnlockBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateToEditor: (Long?) -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CryptInk") },
                actions = {
                    if (state.isSessionAuthenticated) {
                        IconButton(onClick = viewModel::onLockAllTapped) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock Vault",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (state.entries.isNotEmpty()) {
                        IconButton(onClick = viewModel::onGlobalUnlockTapped) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Unlock All Entries"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.onNewEntryTapped { onNavigateToEditor(null) }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Encrypted Entry")
            }
        }
    ) { innerPadding ->
        if (state.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No entries yet. Tap + to write your first encrypted note.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = state.entries,
                    key = { it.id }
                ) { entry ->
                    JournalCard(
                        entry = entry,
                        onCardClick = { tapped ->
                            viewModel.onEntryTapped(tapped) { id ->
                                onNavigateToEditor(id)
                            }
                        },
                        onLockEntryClick = viewModel::onLockSingleEntryTapped
                    )
                }
            }
        }

        state.promptTarget?.let { target ->
            val isFirstSetup = (target as? UnlockPromptTarget.NewEntryCreation)?.isFirstTimeSetup == true

            val (sheetTitle, sheetSubtitle, showScope) = when (target) {
                is UnlockPromptTarget.SingleEntry -> Triple(
                    "Decrypt Entry",
                    "Enter your passphrase to reveal this entry or unlock the full journal.",
                    true
                )
                UnlockPromptTarget.GlobalSession -> Triple(
                    "Unlock CryptInk",
                    "Enter your passphrase to reveal all entries for this session.",
                    false
                )
                is UnlockPromptTarget.NewEntryCreation -> if (target.isFirstTimeSetup) {
                    Triple(
                        "Create Master Passphrase",
                        "There is no password recovery in CryptInk. Confirm your passphrase carefully—losing it means your entries remain permanently encrypted.",
                        false
                    )
                } else {
                    Triple(
                        "Start Encrypted Session",
                        "Enter your master passphrase to encrypt and save your new entry.",
                        false
                    )
                }
            }

            UnlockBottomSheet(
                title = sheetTitle,
                subtitle = sheetSubtitle,
                showScopeSelector = showScope,
                isFirstTimeSetup = isFirstSetup,
                isVerifying = state.isVerifying,
                errorMessage = state.promptError,
                onDismiss = viewModel::dismissPrompt,
                onConfirm = { password, unlockAll ->
                    viewModel.submitPassword(
                        password = password,
                        unlockAll = unlockAll,
                        onNewEntryAuthorized = { onNavigateToEditor(null) }
                    )
                }
            )
        }
    }
}