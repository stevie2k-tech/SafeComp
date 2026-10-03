package com.safecomp.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingScreen(viewModel: PermissionsViewModel, onFinished: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Permissions can change while we're in system settings, so re-check on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val step = state.step
    LaunchedEffect(step) { if (step == null) onFinished() }
    if (step == null) return

    fun canAskAgain(permission: String): Boolean =
        context.findActivity()?.shouldShowRequestPermissionRationale(permission) ?: false

    // Fine + coarse are requested together: Android 12+ rejects a fine-only request.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onPermissionResult(
            PermissionStep.PRECISE_LOCATION,
            canAskAgain(Manifest.permission.ACCESS_FINE_LOCATION),
        )
    }
    // Background location MUST be a separate request. On Android 11+ this doesn't show a
    // dialog; Android opens the app's location settings page where the user picks
    // "Allow all the time".
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            viewModel.onPermissionResult(
                PermissionStep.BACKGROUND_LOCATION,
                canAskAgain(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            )
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.onPermissionResult(
                PermissionStep.NOTIFICATIONS,
                canAskAgain(Manifest.permission.POST_NOTIFICATIONS),
            )
        }
    }

    val blocked = step in state.blockedInSettings
    val content = stepContent(step, state.status)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Step ${step.ordinal + 1} of ${PermissionStep.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { (step.ordinal + 1f) / PermissionStep.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Icon(
                content.icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(content.title, style = MaterialTheme.typography.headlineMedium)
            Text(content.why, style = MaterialTheme.typography.bodyLarge)

            content.hint?.let { hint ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(hint, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            PrivacyNote()

            Spacer(Modifier.height(8.dp))
            when {
                blocked -> {
                    Text(
                        "Android won't show this request again. Open Settings → Permissions to change it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { context.openAppSettings() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open app settings")
                    }
                }
                step == PermissionStep.PRECISE_LOCATION -> Button(
                    onClick = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow precise location") }

                step == PermissionStep.BACKGROUND_LOCATION -> Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    enabled = state.status.preciseLocation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Open location settings" else "Allow all the time")
                }

                step == PermissionStep.NOTIFICATIONS -> Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.openNotificationSettings()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow notifications") }
            }
            TextButton(
                onClick = viewModel::skip,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Not now") }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "SafeComp is not a tracking app. Your location is only compared with the office " +
                    "area on this phone. It is never uploaded, shared, or saved in any log. " +
                    "SafeComp has no internet access at all.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private class StepContent(val icon: ImageVector, val title: String, val why: String, val hint: String?)

private fun stepContent(step: PermissionStep, status: PermissionStatus): StepContent = when (step) {
    PermissionStep.PRECISE_LOCATION -> StepContent(
        icon = Icons.Outlined.MyLocation,
        title = "Precise location",
        why = "SafeComp unlocks your company files only while this phone is inside the office " +
            "area. To know that, it compares your location with the office location.",
        hint = if (status.approximateOnly) {
            "You chose \"Approximate\". Approximate location can be off by several kilometres, " +
                "which is bigger than the office area, so the vault would stay locked. " +
                "Please choose \"Precise\"."
        } else {
            "When Android asks, choose \"Precise\" and \"While using the app\"."
        },
    )
    PermissionStep.BACKGROUND_LOCATION -> StepContent(
        icon = Icons.Outlined.LocationOn,
        title = "Allow all the time",
        why = "When you leave the office, Android tells SafeComp even if the app is closed, so it " +
            "can lock the vault and notify you straight away. Without this, SafeComp still " +
            "works, but it only checks your location while the app is open.",
        hint = when {
            !status.preciseLocation -> "Allow precise location first. Android only lets apps ask for " +
                "background location after that."
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                "Android will open SafeComp's location settings. Tap \"Allow all the time\", then come back."
            else -> null
        },
    )
    PermissionStep.NOTIFICATIONS -> StepContent(
        icon = Icons.Outlined.Notifications,
        title = "Notifications",
        why = "So SafeComp can tell you when the vault locks as you leave the office, and when it " +
            "unlocks again when you return. These status messages are the only notifications it sends.",
        hint = null,
    )
}
