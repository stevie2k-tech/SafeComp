package com.safecomp.ui.geofence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.data.GeofenceConfig
import com.safecomp.ui.components.SecureDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceSetupScreen(viewModel: GeofenceSetupViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isFirstSetup) "Set up office" else "Office location") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.canEdit) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Text(
                            "The office location can only be changed while you're inside it. " +
                                "Otherwise anyone holding this phone could move the office to " +
                                "where they are and unlock the vault.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Text("Office centre", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = viewModel::useCurrentLocation,
                enabled = state.canEdit && !state.locating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.locating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null)
                }
                Spacer(Modifier.size(8.dp))
                Text(if (state.locating) "Getting location…" else "Use my current location")
            }
            state.locationMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.latitude,
                    onValueChange = viewModel::onLatitudeChange,
                    label = { Text("Latitude") },
                    placeholder = { Text("41.311081") },
                    singleLine = true,
                    enabled = state.canEdit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.longitude,
                    onValueChange = viewModel::onLongitudeChange,
                    label = { Text("Longitude") },
                    placeholder = { Text("69.240562") },
                    singleLine = true,
                    enabled = state.canEdit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()

            LabeledSlider(
                title = "Radius",
                valueText = "${state.radiusMeters.roundToInt()} m",
                help = "At least ${GeofenceConfig.MIN_RADIUS_METERS.roundToInt()} m, because phone GPS " +
                    "is often off by 20–50 m. A smaller area would make the vault flicker.",
                value = state.radiusMeters,
                onValueChange = { viewModel.onRadiusChange((it / 10).roundToInt() * 10f) },
                range = GeofenceConfig.MIN_RADIUS_METERS..GeofenceConfig.MAX_RADIUS_METERS,
                enabled = state.canEdit,
            )
            LabeledSlider(
                title = "Unlock after",
                valueText = "${state.dwellSeconds} s inside",
                help = "How long the phone must stay inside before the vault unlocks.",
                value = state.dwellSeconds.toFloat(),
                onValueChange = { viewModel.onDwellChange(it.roundToInt()) },
                range = GeofenceConfig.MIN_DWELL_SECONDS.toFloat()..GeofenceConfig.MAX_DWELL_SECONDS.toFloat(),
                enabled = state.canEdit,
            )
            LabeledSlider(
                title = "Lock after",
                valueText = "${state.graceSeconds} s outside",
                help = "Grace period: how long the phone can be outside before the vault locks. " +
                    "Stops a single bad GPS reading near the edge from locking you out.",
                value = state.graceSeconds.toFloat(),
                onValueChange = { viewModel.onGraceChange((it / 5).roundToInt() * 5) },
                range = GeofenceConfig.MIN_GRACE_SECONDS.toFloat()..GeofenceConfig.MAX_GRACE_SECONDS.toFloat(),
                enabled = state.canEdit,
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canEdit && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.saving) "Saving…" else "Save office location") }

            if (!state.isFirstSetup) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Text(
                    "Office moved, or handing this phone back? Reset removes the office location, " +
                        "every file in the vault, and the encryption keys.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Reset SafeComp", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmReset) {
        SecureDialog(
            title = "Reset SafeComp?",
            text = "This deletes the office location, every file in the vault, and the " +
                "encryption keys. Nothing can be recovered afterwards.",
            confirmLabel = "Reset",
            onConfirm = {
                confirmReset = false
                viewModel.reset()
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueText: String,
    help: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(valueText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled)
        Text(help, style = MaterialTheme.typography.bodySmall)
    }
}
