package com.safecomp.demo

import android.content.Context
import com.safecomp.security.SecurityStateRepository
import kotlinx.coroutines.CoroutineScope

/** DEBUG source set: the real demo mode. (The release source set has a stub with the same API.) */
object DemoModes {
    fun create(
        context: Context,
        securityState: SecurityStateRepository,
        scope: CoroutineScope,
        onSimulationEnded: suspend () -> Unit,
    ): DemoModeController = DebugDemoModeController(context, securityState, scope, onSimulationEnded)
}
