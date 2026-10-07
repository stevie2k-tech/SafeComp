package com.safecomp.demo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.safecomp.security.SecurityStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * RELEASE source set: demo mode does not exist. This stub has the same API as the debug
 * version, but it can never be turned on, simulates nothing and draws nothing.
 */
object DemoModes {
    fun create(
        context: Context,
        securityState: SecurityStateRepository,
        scope: CoroutineScope,
        onSimulationEnded: suspend () -> Unit,
    ): DemoModeController = DisabledDemoModeController
}

private object DisabledDemoModeController : DemoModeController {
    override val isAvailable = false
    override val settings: StateFlow<DemoSettings> = MutableStateFlow(DemoSettings())

    @Composable
    override fun ControlPanel(modifier: Modifier) = Unit

    @Composable
    override fun Badge(modifier: Modifier) = Unit
}
