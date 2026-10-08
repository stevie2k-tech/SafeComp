package com.safecomp.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.ui.components.GlassCard
import com.safecomp.ui.components.IconBadge
import com.safecomp.ui.components.PrimaryButton
import com.safecomp.ui.components.ScreenBackground
import com.safecomp.ui.components.SectionLabel
import com.safecomp.ui.theme.Sc

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

    ScreenBackground(glow = Sc.Accent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            // Segmented progress.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PermissionStep.entries.forEach { s ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(if (s.ordinal <= step.ordinal) Sc.Accent else Sc.Hairline),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Step ${step.ordinal + 1} of ${PermissionStep.entries.size}")
            Spacer(Modifier.height(40.dp))

            Box(Modifier.align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(150.dp)
                        .background(Brush.radialGradient(listOf(Sc.Accent.copy(alpha = 0.25f), Color.Transparent))),
                )
                IconBadge(content.icon, Sc.Accent, size = 88.dp, iconSize = 40.dp)
            }
            Spacer(Modifier.height(28.dp))
            Text(content.title, style = MaterialTheme.typography.headlineMedium, color = Sc.TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(content.why, style = MaterialTheme.typography.bodyLarge, color = Sc.TextSecondary)

            content.hint?.let { hint ->
                Spacer(Modifier.height(18.dp))
                GlassCard(tint = Sc.Unknown, contentPadding = PaddingValues(16.dp)) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = Sc.TextPrimary)
                }
            }

            Spacer(Modifier.height(14.dp))
            PrivacyNote()
            Spacer(Modifier.height(28.dp))

            when {
                blocked -> {
                    Text(
                        "Android won't show this request again. Open Settings → Permissions to change it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sc.Outside,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Open app settings", { context.openAppSettings() }, Modifier.fillMaxWidth())
                }
                step == PermissionStep.PRECISE_LOCATION -> PrimaryButton(
                    "Allow precise location",
                    {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    Modifier.fillMaxWidth(),
                )
                step == PermissionStep.BACKGROUND_LOCATION -> PrimaryButton(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Open location settings" else "Allow all the time",
                    {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = state.status.preciseLocation,
                )
                step == PermissionStep.NOTIFICATIONS -> PrimaryButton(
                    "Allow notifications",
                    {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.openNotificationSettings()
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = viewModel::skip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Not now", color = Sc.TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    GlassCard(tint = Sc.Inside, contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Shield, Sc.Inside, size = 36.dp, iconSize = 18.dp)
            Text(
                "Not a tracking app. Your location is only compared with the office on this phone: " +
                    "never uploaded, shared or logged. SafeComp has no internet access at all.",
                style = MaterialTheme.typography.bodySmall,
                color = Sc.TextSecondary,
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
