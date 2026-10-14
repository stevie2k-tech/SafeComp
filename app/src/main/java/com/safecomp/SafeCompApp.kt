package com.safecomp

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class SafeCompApp : Application() {

    /** Manual dependency injection: one container, created once per process. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Check location whenever the app becomes visible, and stop when it's hidden.
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.foregroundMonitor)
        // Close open files when the app goes to the background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.vaultSession)
        // Focus Mode may only start while a SafeComp screen is visible.
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.focusController)
        // Going to the background ends the fingerprint/PIN session (Phase 7).
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.authSession)
    }
}
