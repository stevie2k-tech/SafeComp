package com.safecomp.ui.geofence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.data.GeofenceConfig
import com.safecomp.ui.components.GlassCard
import com.safecomp.ui.components.IconBadge
import com.safecomp.ui.components.PrimaryButton
import com.safecomp.ui.components.ScTopBar
import com.safecomp.ui.components.ScreenBackground
import com.safecomp.ui.components.SecondaryButton
import com.safecomp.ui.components.SectionLabel
import com.safecomp.ui.components.SecureDialog
import com.safecomp.ui.theme.MonoFamily
import com.safecomp.ui.theme.Sc
import kotlin.math.roundToInt

@Composable
fun GeofenceSetupScreen(viewModel: GeofenceSetupViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    ScreenBackground(glow = Sc.Sky) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            ScTopBar(
                title = if (state.isFirstSetup) "Set up office" else "Office",
                subtitle = "GEOFENCE",
                onBack = onDone,
            )
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!state.canEdit) {
                    GlassCard(tint = Sc.Unknown) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Outlined.Lock, Sc.Unknown, size = 40.dp, iconSize = 20.dp)
                            Text(
                                "The office can only be changed while you're inside it. Otherwise anyone " +
                                    "holding this phone could move it to where they are and unlock the vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Sc.TextSecondary,
                            )
                        }
                    }
                }

                Section(Icons.Outlined.Place, "Location") {
                    SecondaryButton(
                        text = if (state.locating) "Getting a precise fix…" else "Use my current location",
                        onClick = viewModel::useCurrentLocation,
                        icon = if (state.locating) null else Icons.Outlined.MyLocation,
                        enabled = state.canEdit && !state.locating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.locating) {
                        CircularProgressIndicator(color = Sc.Accent, modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
                    }
                    state.locationMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Sc.TextSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoordinateField("Latitude", state.latitude, viewModel::onLatitudeChange, state.canEdit, Modifier.weight(1f))
                        CoordinateField("Longitude", state.longitude, viewModel::onLongitudeChange, state.canEdit, Modifier.weight(1f))
                    }
                }

                Section(Icons.Outlined.TripOrigin, "Boundary") {
                    ValueSlider(
                        title = "Radius",
                        value = "${state.radiusMeters.roundToInt()} M",
                        help = "At least ${GeofenceConfig.MIN_RADIUS_METERS.roundToInt()} m: phone GPS is often off by 20–50 m, so a smaller area would flicker.",
                        current = state.radiusMeters,
                        onChange = { viewModel.onRadiusChange((it / 10).roundToInt() * 10f) },
                        range = GeofenceConfig.MIN_RADIUS_METERS..GeofenceConfig.MAX_RADIUS_METERS,
                        enabled = state.canEdit,
                    )
                }

                Section(Icons.Outlined.Timer, "Timing") {
                    ValueSlider(
                        title = "Unlock after",
                        value = "${state.dwellSeconds} S IN",
                        help = "How long the phone must stay inside before the vault unlocks.",
                        current = state.dwellSeconds.toFloat(),
                        onChange = { viewModel.onDwellChange(it.roundToInt()) },
                        range = GeofenceConfig.MIN_DWELL_SECONDS.toFloat()..GeofenceConfig.MAX_DWELL_SECONDS.toFloat(),
                        enabled = state.canEdit,
                    )
                    Spacer(Modifier.height(8.dp))
                    ValueSlider(
                        title = "Lock after",
                        value = "${state.graceSeconds} S OUT",
                        help = "Grace period, so a single bad GPS reading at the edge doesn't lock you out.",
                        current = state.graceSeconds.toFloat(),
                        onChange = { viewModel.onGraceChange((it / 5).roundToInt() * 5) },
                        range = GeofenceConfig.MIN_GRACE_SECONDS.toFloat()..GeofenceConfig.MAX_GRACE_SECONDS.toFloat(),
                        enabled = state.canEdit,
                    )
                }

                state.error?.let {
                    Text(it, color = Sc.Outside, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 6.dp))
                }

                PrimaryButton(
                    text = if (state.saving) "Saving…" else "Save office",
                    onClick = viewModel::save,
                    icon = Icons.Outlined.Save,
                    enabled = state.canEdit && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!state.isFirstSetup) {
                    Spacer(Modifier.height(18.dp))
                    SectionLabel("Danger zone", Modifier.padding(start = 6.dp))
                    GlassCard(tint = Sc.Outside) {
                        Text(
                            "Office moved, or handing this phone back? Reset deletes the office, every file " +
                                "in the vault, the encryption keys and the event log.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sc.TextSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        SecondaryButton(
                            text = "Reset SafeComp",
                            onClick = { confirmReset = true },
                            icon = Icons.Outlined.RestartAlt,
                            color = Sc.Outside,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmReset) {
        SecureDialog(
            title = "Reset SafeComp?",
            text = "This deletes the office location, every file in the vault, the encryption " +
                "keys and the event log. Nothing can be recovered afterwards.",
            confirmLabel = "Reset",
            onConfirm = {
                confirmReset = false
                viewModel.reset()
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmReset = false },
            destructive = true,
        )
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon, Sc.Sky, size = 32.dp, iconSize = 16.dp)
            SectionLabel(title)
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun CoordinateField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Sc.Accent,
            unfocusedBorderColor = Sc.Hairline,
            focusedLabelColor = Sc.Accent,
            unfocusedLabelColor = Sc.TextTertiary,
            cursorColor = Sc.Accent,
            focusedTextColor = Sc.TextPrimary,
            unfocusedTextColor = Sc.TextPrimary,
            disabledTextColor = Sc.TextSecondary,
            disabledBorderColor = Sc.Hairline,
        ),
        modifier = modifier,
    )
}

@Composable
private fun ValueSlider(
    title: String,
    value: String,
    help: String,
    current: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Sc.TextPrimary, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = Sc.Accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Sc.Accent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Sc.Accent,
                activeTrackColor = Sc.Accent,
                inactiveTrackColor = Sc.Hairline,
            ),
        )
        Text(help, style = MaterialTheme.typography.bodySmall, color = Sc.TextTertiary)
    }
}
