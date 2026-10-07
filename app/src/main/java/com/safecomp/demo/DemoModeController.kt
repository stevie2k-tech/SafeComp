package com.safecomp.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

enum class Simulation { INSIDE, OUTSIDE }

data class DemoSettings(
    /** Non-null: real location input is ignored and this position is simulated instead. */
    val simulation: Simulation? = null,
    /** Accept locations from mock-location apps / the emulator (normally treated as UNKNOWN). */
    val allowMockLocations: Boolean = false,
) {
    val active: Boolean get() = simulation != null || allowMockLocations
}

/**
 * Demo mode, for presenting SafeComp when GPS indoors is unreliable.
 *
 * Only this interface lives in `main`. The working implementation and all of its UI live in
 * the `debug` source set. The `release` source set has a stub that is always off and draws
 * nothing. Release builds therefore don't just hide demo mode, they don't contain it.
 * Both source sets provide `DemoModes.create(...)`, and Gradle compiles only the one that
 * matches the build type.
 */
interface DemoModeController {
    val isAvailable: Boolean
    val settings: StateFlow<DemoSettings>

    /** The demo switches (debug builds only; empty in release). */
    @Composable
    fun ControlPanel(modifier: Modifier)

    /** A persistent "DEMO MODE" badge while demo mode is on (empty in release). */
    @Composable
    fun Badge(modifier: Modifier)
}
