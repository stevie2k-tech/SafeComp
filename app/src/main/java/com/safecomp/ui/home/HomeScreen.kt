package com.safecomp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.data.GeofenceConfig
import com.safecomp.location.GeofenceRegistration
import com.safecomp.security.SecuritySnapshot
import com.safecomp.security.SecurityState
import com.safecomp.security.StateReason
import com.safecomp.ui.components.SecureDialog
import com.safecomp.ui.components.description
import com.safecomp.ui.permissions.PermissionStatus
import com.safecomp.ui.theme.InsideGreen
import com.safecomp.ui.theme.OutsideRed
import com.safecomp.ui.theme.UnknownAmber
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onReviewPermissions: () -> Unit,
    onEditOffice: () -> Unit,
    onOpenVault: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSecurityInfo by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SafeComp") },
                actions = {
                    IconButton(onClick = { showSecurityInfo = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "How this screen is protected")
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
            StateCard(state.snapshot, state.nowMillis, state.config)
            Button(onClick = onOpenVault, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FolderZip, contentDescription = null)
                Text("  Open vault")
            }
            PermissionBanners(state.permissions, onReviewPermissions)
            OfficeCard(state.config, state.registration, onEditOffice, viewModel::recheckNow)
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

@Composable
private fun StateCard(snapshot: SecuritySnapshot, now: Long, config: GeofenceConfig?) {
    val (color, icon) = when (snapshot.state) {
        SecurityState.INSIDE -> InsideGreen to Icons.Outlined.LockOpen
        SecurityState.OUTSIDE -> OutsideRed to Icons.Outlined.Lock
        SecurityState.UNKNOWN -> UnknownAmber to Icons.AutoMirrored.Outlined.HelpOutline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color, contentColor = Color.White),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Text(
                    if (snapshot.isUnlocked) "Vault unlocked" else "Vault locked",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(snapshot.state.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(snapshot.reason.description(), style = MaterialTheme.typography.bodyMedium)

            // Countdowns. These are display only: the state machine makes the real decision.
            snapshot.pendingExit?.let { pending ->
                val graceMillis = (config?.graceSeconds ?: 0) * 1000L
                val left = ((graceMillis - (now - pending.sinceMillis)) / 1000).coerceAtLeast(0)
                Text(
                    "Leaving the office area. Locking in $left s unless you're back inside.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            val enterSince = snapshot.enterEvidenceSince
            if (snapshot.reason == StateReason.CONFIRMING_INSIDE && enterSince != null && config != null) {
                val left = ((config.dwellSeconds * 1000L - (now - enterSince)) / 1000).coerceAtLeast(0)
                Text(
                    "Unlocking in about $left s if you stay inside.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun OfficeCard(
    config: GeofenceConfig?,
    registration: GeofenceRegistration,
    onEditOffice: () -> Unit,
    onRecheck: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Business, contentDescription = null)
                Text("Office", style = MaterialTheme.typography.titleMedium)
            }
            if (config == null) {
                Text("Not set up yet.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onEditOffice) { Text("Set up office location") }
                return@Column
            }
            Text(
                String.format(Locale.US, "%.5f, %.5f", config.latitude, config.longitude) +
                    " · radius ${config.radiusMeters.roundToInt()} m",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Unlocks after ${config.dwellSeconds} s inside · locks after ${config.graceSeconds} s outside",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(registration.description(), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRecheck) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text(" Check now")
                }
                OutlinedButton(onClick = onEditOffice) { Text("Edit") }
            }
        }
    }
}

private fun GeofenceRegistration.description(): String = when (this) {
    GeofenceRegistration.Active -> "Background monitoring: on"
    GeofenceRegistration.ForegroundOnly -> "Background monitoring: off (checks only while SafeComp is open)"
    GeofenceRegistration.NotConfigured -> "Background monitoring: not registered"
    is GeofenceRegistration.Failed -> "Background monitoring failed: $message"
}

@Composable
private fun PermissionBanners(status: PermissionStatus, onReviewPermissions: () -> Unit) {
    val problems = buildList {
        if (!status.preciseLocation) {
            add("Precise location is off. SafeComp can't confirm you're in the office, so the vault stays locked.")
        }
        if (status.preciseLocation && !status.backgroundLocation) {
            add("Background location is off. SafeComp only checks your location while it's open, so lock notifications may be late.")
        }
        if (!status.notifications) {
            add("Notifications are off. You won't be told when the vault locks or unlocks.")
        }
    }
    if (problems.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                Text("Missing permissions", style = MaterialTheme.typography.titleMedium)
            }
            problems.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton(onClick = onReviewPermissions) { Text("Review permissions") }
        }
    }
}
