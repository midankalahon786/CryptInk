package com.cryptink.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.cryptink.app.core.security.AppLockObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CryptInkApp : Application() {

    @Inject
    lateinit var appLockObserver: AppLockObserver

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockObserver)
    }
}