package com.cryptink.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockBottomSheet(
    title: String,
    subtitle: String,
    showScopeSelector: Boolean,
    isFirstTimeSetup: Boolean = false,
    isVerifying: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: CharArray, unlockAll: Boolean) -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden
    )
    val passwordState = rememberTextFieldState()
    val confirmPasswordState = rememberTextFieldState()
    var passwordVisible by remember { mutableStateOf(false) }
    var unlockAllSelected by remember { mutableStateOf(!showScopeSelector) }
    var mismatchError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun charSequencesMatch(a: CharSequence, b: CharSequence): Boolean {
        if (a.length != b.length) return false
        for (i in a.indices) {
            if (a[i] != b[i]) return false
        }
        return true
    }

    fun submitPassphrase() {
        val text = passwordState.text
        if (text.isEmpty() || isVerifying) return

        if (isFirstTimeSetup && !charSequencesMatch(text, confirmPasswordState.text)) {
            mismatchError = "Passphrases do not match."
            return
        }

        mismatchError = null
        val chars = CharArray(text.length) { index -> text[index] }
        passwordState.clearText()
        confirmPasswordState.clearText()
        onConfirm(chars, unlockAllSelected)
    }

    val activeError = mismatchError ?: errorMessage
    val isSubmitEnabled = if (isFirstTimeSetup) {
        passwordState.text.isNotEmpty() && confirmPasswordState.text.isNotEmpty() && !isVerifying
    } else {
        passwordState.text.isNotEmpty() && !isVerifying
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isVerifying) {
                passwordState.clearText()
                confirmPasswordState.clearText()
                onDismiss()
            }
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedSecureTextField(
                state = passwordState,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text("Secret Passphrase") },
                enabled = !isVerifying,
                isError = activeError != null,
                textObfuscationMode = if (passwordVisible) {
                    TextObfuscationMode.Visible
                } else {
                    TextObfuscationMode.RevealLastTyped
                },
                onKeyboardAction = {
                    if (!isFirstTimeSetup) submitPassphrase()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = "Toggle password visibility"
                        )
                    }
                }
            )

            if (isFirstTimeSetup) {
                OutlinedSecureTextField(
                    state = confirmPasswordState,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm Secret Passphrase") },
                    enabled = !isVerifying,
                    isError = activeError != null,
                    textObfuscationMode = if (passwordVisible) {
                        TextObfuscationMode.Visible
                    } else {
                        TextObfuscationMode.RevealLastTyped
                    },
                    onKeyboardAction = { submitPassphrase() }
                )
            }

            if (activeError != null) {
                Text(
                    text = activeError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (showScopeSelector) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !unlockAllSelected,
                        onClick = { unlockAllSelected = false },
                        label = { Text("This Entry Only") },
                        enabled = !isVerifying
                    )
                    FilterChip(
                        selected = unlockAllSelected,
                        onClick = { unlockAllSelected = true },
                        label = { Text("Unlock All Entries") },
                        enabled = !isVerifying
                    )
                }
            }

            Button(
                onClick = { submitPassphrase() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = isSubmitEnabled
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        when {
                            isFirstTimeSetup -> "Set Passphrase & Start"
                            unlockAllSelected -> "Decrypt All"
                            else -> "Decrypt"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}