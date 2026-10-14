package com.safecomp.ui.home

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.location.GeofenceRegistration
import com.safecomp.security.SecurityState
import com.safecomp.security.StateReason
import com.safecomp.ui.components.CircleIconButton
import com.safecomp.ui.components.GlassCard
import com.safecomp.ui.components.IconBadge
import com.safecomp.ui.components.PrimaryButton
import com.safecomp.ui.components.ScTopBar
import com.safecomp.ui.components.ScreenBackground
import com.safecomp.ui.components.SecondaryButton
import com.safecomp.ui.components.SectionLabel
import com.safecomp.ui.components.SecureDialog
import com.safecomp.ui.components.StatusPill
import com.safecomp.ui.components.color
import com.safecomp.ui.components.description
import com.safecomp.ui.permissions.BackgroundAdvice
import com.safecomp.ui.permissions.PermissionStatus
import com.safecomp.ui.theme.Sc
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onReviewPermissions: () -> Unit,
    onEditOffice: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenFocus: () -> Unit,
    demoPanel: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSecurityInfo by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val snapshot = state.snapshot
    val stateColor = snapshot.state.color()

    ScreenBackground(glow = stateColor) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
        ) {
            ScTopBar(
                title = "SafeComp",
                subtitle = "LOCATION-LOCKED VAULT",
                actions = {
                    CircleIconButton(Icons.Outlined.History, "Event log", onOpenLog)
                    CircleIconButton(Icons.Outlined.Info, "How this screen is protected", { showSecurityInfo = true })
                },
            )

            // --- Hero: the status ring ---
            val ring = ringModel(state)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                StatusRing(
                    color = stateColor,
                    mode = ring.mode,
                    progress = ring.progress,
                    icon = if (snapshot.isUnlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    label = snapshot.state.name,
                    caption = ring.caption,
                )
            }
            Text(
                snapshot.reason.description(),
                style = MaterialTheme.typography.bodyLarge,
                color = Sc.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            )

            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PrimaryButton(
                    text = if (snapshot.isUnlocked) "Open vault" else "Vault",
                    onClick = onOpenVault,
                    icon = if (snapshot.isUnlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    trailing = if (state.loaded) "${state.fileCount} FILE${if (state.fileCount == 1) "" else "S"}" else null,
                    modifier = Modifier.fillMaxWidth(),
                )

                PermissionWarnings(state.permissions, onReviewPermissions)

                AnimatedVisibility(visible = state.loaded, enter = fadeIn()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Tile(
                            icon = Icons.Outlined.Business,
                            tint = Sc.Sky,
                            label = "Office",
                            value = state.config?.let { "${it.radiusMeters.roundToInt()} m" } ?: "Set up",
                            detail = state.config?.let { "${it.dwellSeconds}s in · ${it.graceSeconds}s out" } ?: "Not configured",
                            onClick = onEditOffice,
                            modifier = Modifier.weight(1f),
                        )
                        Tile(
                            icon = Icons.Outlined.History,
                            tint = Sc.Violet,
                            label = "Activity",
                            value = "${state.eventsToday}",
                            detail = "events today",
                            onClick = onOpenLog,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                AnimatedVisibility(visible = state.loaded, enter = fadeIn()) {
                    FocusCard(
                        status = state.focus,
                        workApps = state.workApps,
                        onOpenSettings = onOpenFocus,
                        onStart = viewModel::startFocus,
                        onStop = viewModel::stopFocus,
                        onLaunch = viewModel::launchWorkApp,
                    )
                }

                MonitoringRow(state.registration, onRecheck = viewModel::recheckNow)
                demoPanel()
            }
        }
    }

    if (showSecurityInfo) {
        SecureDialog(
            title = "Screen protection",
            text = "SafeComp blocks screenshots and screen recording, and hides its preview in " +
                "the recent-apps screen, so company files can't be captured from the display. " +
                "This dialog is protected too.",
            onDismiss = { showSecurityInfo = false },
        )
    }
}

private class RingModel(val mode: RingMode, val progress: Float, val caption: String)

private fun ringModel(state: HomeUiState): RingModel {
    val s = state.snapshot
    val config = state.config
    val pending = s.pendingExit
    val enterSince = s.enterEvidenceSince
    return when {
        pending != null && config != null -> {
            val grace = config.graceSeconds * 1000L
            val left = (grace - (state.nowMillis - pending.sinceMillis)).coerceAtLeast(0)
            RingModel(RingMode.DRAINING, left.toFloat() / grace, "LOCKS IN ${left / 1000} S")
        }
        s.reason == StateReason.CONFIRMING_INSIDE && enterSince != null && config != null -> {
            val dwell = config.dwellSeconds * 1000L
            val done = (state.nowMillis - enterSince).coerceIn(0, dwell)
            RingModel(RingMode.FILLING, done.toFloat() / dwell, "UNLOCKS IN ${(dwell - done) / 1000} S")
        }
        s.reason == StateReason.CHECKING -> RingModel(RingMode.CHECKING, 0f, "CHECKING…")
        s.isUnlocked -> RingModel(RingMode.STEADY, 1f, "VAULT OPEN")
        else -> RingModel(RingMode.STEADY, 1f, "VAULT LOCKED")
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 38.dp, iconSize = 19.dp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Sc.TextTertiary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel(label)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Sc.TextPrimary)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Sc.TextSecondary)
    }
}

@Composable
private fun MonitoringRow(registration: GeofenceRegistration, onRecheck: () -> Unit) {
    val (text, color) = when (registration) {
        GeofenceRegistration.Active -> "BACKGROUND MONITORING ON" to Sc.Inside
        GeofenceRegistration.ForegroundOnly -> "CHECKS ONLY WHILE OPEN" to Sc.Unknown
        GeofenceRegistration.NotConfigured -> "GEOFENCE NOT REGISTERED" to Sc.TextTertiary
        is GeofenceRegistration.Failed -> "GEOFENCE ERROR" to Sc.Outside
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Radar, null, tint = Sc.TextTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        StatusPill(text, color)
        Spacer(Modifier.size(10.dp))
        CircleIconButton(Icons.Outlined.Refresh, "Check location now", onRecheck)
    }
}

@Composable
private fun PermissionWarnings(status: PermissionStatus, onReviewPermissions: () -> Unit) {
    val problems = buildList {
        if (!status.preciseLocation) add("Precise location is off, so the vault stays locked.")
        if (status.preciseLocation && !status.backgroundLocation) add("Background location is off: lock alerts may be late.")
        if (!status.notifications) add("Notifications are off: you won't be told when the vault locks.")
        if (status.backgroundLocation && !status.batteryUnrestricted) {
            add("Battery saving can delay lock alerts while SafeComp is closed.")
        }
    }
    val brandTip = if (status.backgroundLocation && !status.batteryUnrestricted) {
        BackgroundAdvice.forManufacturer(Build.MANUFACTURER)
    } else {
        null
    }
    if (problems.isEmpty()) return
    GlassCard(tint = Sc.Unknown) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Outlined.WarningAmber, Sc.Unknown, size = 38.dp, iconSize = 19.dp)
            Text("Needs your attention", style = MaterialTheme.typography.titleMedium, color = Sc.TextPrimary)
        }
        Spacer(Modifier.height(12.dp))
        problems.forEach {
            Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = Sc.TextSecondary)
        }
        brandTip?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Sc.TextTertiary)
        }
        Spacer(Modifier.height(14.dp))
        SecondaryButton("Review permissions", onReviewPermissions, icon = Icons.Outlined.Shield)
    }
}
