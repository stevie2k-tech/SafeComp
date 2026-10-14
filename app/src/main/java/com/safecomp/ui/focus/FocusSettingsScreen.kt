package com.safecomp.ui.focus

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.focus.FocusMode
import com.safecomp.ui.auth.rememberAuthGate
import com.safecomp.ui.components.GlassCard
import com.safecomp.ui.components.IconBadge
import com.safecomp.ui.components.ScTopBar
import com.safecomp.ui.components.ScreenBackground
import com.safecomp.ui.components.SecondaryButton
import com.safecomp.ui.components.SectionLabel
import com.safecomp.ui.components.StatusPill
import com.safecomp.ui.theme.MonoFamily
import com.safecomp.ui.theme.Sc

@Composable
fun FocusSettingsScreen(viewModel: FocusSettingsViewModel, onBack: () -> Unit) {
    // Every change here needs the owner's fingerprint/PIN (one prompt covers 5 minutes).
    val authGate = rememberAuthGate(viewModel.authSession)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val status = state.status

    ScreenBackground(glow = if (status.active) Sc.Violet else null) {
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                ScTopBar(title = "Focus Mode", subtitle = "WORK APPS ONLY, AT THE OFFICE", onBack = onBack)
            }
            item {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AutoStartCard(status.autoStart) { on -> authGate.requireAuth("Change Focus Mode") { viewModel.setAutoStart(on) } }
                    ModeCard(status.mode, state.setupCommand) { authGate.requireAuth("Remove the test device owner") { viewModel.removeTestOwner() } }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Work apps", Modifier.weight(1f))
                        Text(
                            "${state.selectedCount} CHOSEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = Sc.TextTertiary,
                        )
                    }
                    SearchField(state.query, viewModel::setQuery)
                    AppRow(
                        label = "SafeComp",
                        detail = "Always allowed, so you can always reach Stop",
                        icon = { IconBadge(Icons.Outlined.Lock, Sc.Accent, size = 40.dp, iconSize = 20.dp) },
                        checked = true,
                        enabled = false,
                        onChange = {},
                    )
                }
            }
            if (state.loadingApps) {
                item {
                    Text(
                        "Loading apps…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sc.TextSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.rows, key = { it.app.packageName }) { row ->
                Box(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                    AppRow(
                        label = row.app.label,
                        detail = row.app.packageName,
                        icon = { AppIcon(row.app) },
                        checked = row.selected,
                        enabled = true,
                        onChange = { on -> authGate.requireAuth("Change your work apps") { viewModel.setWorkApp(row.app.packageName, on) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoStartCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    GlassCard(tint = Sc.Violet) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Outlined.CenterFocusStrong, Sc.Violet, size = 40.dp, iconSize = 20.dp)
            Text(
                "Start automatically at the office",
                style = MaterialTheme.typography.titleMedium,
                color = Sc.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            ScSwitch(enabled, onChange)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "When SafeComp confirms you're inside the office, it limits the phone to work apps " +
                "and tells you with a notification. Leaving the office ends it. You can stop it " +
                "at any time: this is your choice, not a company rule.",
            style = MaterialTheme.typography.bodySmall,
            color = Sc.TextSecondary,
        )
    }
}

@Composable
private fun ModeCard(mode: FocusMode, setupCommand: String?, onRemoveTestOwner: () -> Unit) {
    val (icon, tint) = when (mode) {
        FocusMode.PINNING -> Icons.Outlined.PushPin to Sc.Sky
        FocusMode.TEST_OWNER -> Icons.Outlined.Apps to Sc.Unknown
        FocusMode.MANAGED -> Icons.Outlined.Apps to Sc.Inside
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon, tint, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f)) {
                SectionLabel("Detected on this phone")
                Text(mode.title(), style = MaterialTheme.typography.titleMedium, color = Sc.TextPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(mode.explanation(), style = MaterialTheme.typography.bodySmall, color = Sc.TextSecondary)

        if (mode == FocusMode.TEST_OWNER) {
            Spacer(Modifier.height(14.dp))
            StatusPill("DEBUG BUILD · TEST DEVICE OWNER", Sc.Unknown)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                "Remove test device owner",
                onRemoveTestOwner,
                icon = Icons.Outlined.PersonOff,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (mode == FocusMode.PINNING && setupCommand != null) {
            // Debug builds only: how to demonstrate the real allow-list on an emulator.
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Terminal, null, tint = Sc.Unknown, modifier = Modifier.size(16.dp))
                SectionLabel("Debug: demo the full allow-list")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "On an emulator with no accounts, run:",
                style = MaterialTheme.typography.bodySmall,
                color = Sc.TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                setupCommand,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                color = Sc.TextPrimary,
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Sc.TextTertiary) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Sc.Accent,
            unfocusedBorderColor = Sc.Hairline,
            cursorColor = Sc.Accent,
            focusedTextColor = Sc.TextPrimary,
            unfocusedTextColor = Sc.TextPrimary,
            focusedPlaceholderColor = Sc.TextTertiary,
            unfocusedPlaceholderColor = Sc.TextTertiary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AppRow(
    label: String,
    detail: String,
    icon: @Composable () -> Unit,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    GlassCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = Sc.TextPrimary, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Sc.TextTertiary, maxLines = 1)
            }
            ScSwitch(checked, onChange, enabled)
        }
    }
}

@Composable
internal fun AppIcon(app: LaunchableApp, size: Int = 40) {
    val icon = app.icon
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)))
    } else {
        IconBadge(Icons.Outlined.Apps, Sc.TextSecondary, size = size.dp, iconSize = (size / 2).dp)
    }
}

@Composable
private fun ScSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Sc.Bg,
            checkedTrackColor = Sc.Violet,
            uncheckedTrackColor = Sc.SurfaceHigh,
            uncheckedBorderColor = Sc.Hairline,
            disabledCheckedTrackColor = Sc.Accent.copy(alpha = 0.5f),
            disabledCheckedThumbColor = Sc.Bg,
        ),
    )
}
