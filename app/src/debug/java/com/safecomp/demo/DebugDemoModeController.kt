package com.safecomp.demo

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.security.SecurityEvent
import com.safecomp.security.SecurityStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY. This file is in src/debug, so it isn't compiled into release builds.
 *
 * While a position is simulated, a fake fix is sent to the security state machine every
 * 2 seconds, marked `simulated = true`. The state machine itself is untouched: dwell time,
 * grace period, auto-close, notifications and the event log all behave exactly as they would
 * with real GPS. That's what makes the demo honest.
 */
internal class DebugDemoModeController(
    context: Context,
    private val securityState: SecurityStateRepository,
    private val scope: CoroutineScope,
    private val onSimulationEnded: suspend () -> Unit,
) : DemoModeController {

    private val prefs = context.getSharedPreferences("safecomp_demo", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        DemoSettings(
            simulation = prefs.getString(KEY_SIMULATION, null)?.let { runCatching { Simulation.valueOf(it) }.getOrNull() },
            allowMockLocations = prefs.getBoolean(KEY_ALLOW_MOCK, false),
        ),
    )
    override val settings: StateFlow<DemoSettings> = _settings.asStateFlow()
    override val isAvailable = true

    init {
        scope.launch {
            _settings.map { it.simulation }.distinctUntilChanged().collectLatest { simulation ->
                if (simulation == null) return@collectLatest
                while (true) {
                    securityState.dispatch(simulatedFix(simulation), simulated = true)
                    delay(FIX_INTERVAL_MILLIS)
                }
            }
        }
    }

    private fun simulatedFix(simulation: Simulation) = SecurityEvent.LocationFix(
        // "Inside" = at the office centre; "outside" = 100 km away. Good accuracy, not mocked.
        distanceMeters = if (simulation == Simulation.INSIDE) 0f else 100_000f,
        accuracyMeters = 5f,
        isMock = false,
    )

    private fun setSimulation(simulation: Simulation?) {
        val previous = _settings.value.simulation
        update(_settings.value.copy(simulation = simulation))
        if (previous != null && simulation == null) {
            // Back to real location: throw away the simulated evidence and check for real.
            scope.launch {
                securityState.dispatch(SecurityEvent.ConfigChanged)
                onSimulationEnded()
            }
        }
    }

    private fun setAllowMock(allow: Boolean) = update(_settings.value.copy(allowMockLocations = allow))

    private fun update(new: DemoSettings) {
        _settings.value = new
        prefs.edit {
            putString(KEY_SIMULATION, new.simulation?.name)
            putBoolean(KEY_ALLOW_MOCK, new.allowMockLocations)
        }
    }

    @Composable
    override fun ControlPanel(modifier: Modifier) {
        val current by settings.collectAsStateWithLifecycle()
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DemoAmber.copy(alpha = 0.15f)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Science, contentDescription = null)
                    Text("Demo mode (debug build only)", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Simulates the phone's position for presentations. The real dwell time, grace " +
                        "period, notifications and event log still apply. Release builds don't contain this.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val options = listOf(null to "Real GPS", Simulation.INSIDE to "Inside", Simulation.OUTSIDE to "Outside")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = current.simulation == value,
                            onClick = { setSimulation(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(label) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Accept mock locations (emulator, fake GPS apps)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = current.allowMockLocations, onCheckedChange = ::setAllowMock)
                }
            }
        }
    }

    @Composable
    override fun Badge(modifier: Modifier) {
        val current by settings.collectAsStateWithLifecycle()
        if (!current.active) return
        val what = when (current.simulation) {
            Simulation.INSIDE -> "simulating INSIDE"
            Simulation.OUTSIDE -> "simulating OUTSIDE"
            null -> "mock locations accepted"
        }
        Text(
            "DEMO MODE · $what",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = modifier
                .background(DemoAmber, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }

    private companion object {
        const val KEY_SIMULATION = "simulation"
        const val KEY_ALLOW_MOCK = "allow_mock"
        const val FIX_INTERVAL_MILLIS = 2_000L
        val DemoAmber = Color(0xFFFFC107)
    }
}
