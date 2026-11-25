package com.cryptink.app.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cryptink.app.core.crypto.KeySessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockObserver @Inject constructor(
    private val sessionManager: KeySessionManager
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        // Wipe passphrase from RAM whenever the app moves to the background
        sessionManager.lockAll()
    }
}