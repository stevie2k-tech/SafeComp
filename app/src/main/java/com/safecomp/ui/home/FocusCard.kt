package com.safecomp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safecomp.focus.FocusMode
import com.safecomp.focus.FocusStatus
import com.safecomp.ui.components.CircleIconButton
import com.safecomp.ui.components.GlassCard
import com.safecomp.ui.components.IconBadge
import com.safecomp.ui.components.SecondaryButton
import com.safecomp.ui.components.SectionLabel
import com.safecomp.ui.components.StatusPill
import com.safecomp.ui.focus.AppIcon
import com.safecomp.ui.focus.LaunchableApp
import com.safecomp.ui.focus.narration
import com.safecomp.ui.focus.pill
import com.safecomp.ui.focus.title
import com.safecomp.ui.theme.Sc

/** Home's Focus Mode card: what's happening right now, in plain words, and the way out. */
@Composable
fun FocusCard(
    status: FocusStatus,
    workApps: List<LaunchableApp>,
    onOpenSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLaunch: (String) -> Unit,
) {
    val (pillText, pillColor) = status.pill()
    GlassCard(tint = if (status.active) Sc.Violet else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Outlined.CenterFocusStrong, Sc.Violet, size = 38.dp, iconSize = 19.dp)
            Column(Modifier.weight(1f)) {
                Text("Focus Mode", style = MaterialTheme.typography.titleMedium, color = Sc.TextPrimary)
                SectionLabel(status.mode.title())
            }
            CircleIconButton(Icons.Outlined.Tune, "Focus Mode settings", onOpenSettings)
        }
        Spacer(Modifier.height(12.dp))
        StatusPill(pillText, pillColor)
        Spacer(Modifier.height(10.dp))
        Text(status.narration(workApps.size), style = MaterialTheme.typography.bodyMedium, color = Sc.TextSecondary)

        // During real Lock Task, the work apps are one tap away (other apps won't open).
        if (status.active && status.mode != FocusMode.PINNING && workApps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                workApps.forEach { app ->
                    Column(
                        Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLaunch(app.packageName) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app, size = 44)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            app.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Sc.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        when {
            status.active || status.starting -> {
                Spacer(Modifier.height(14.dp))
                SecondaryButton("Stop Focus Mode", onStop, icon = Icons.Outlined.Stop, modifier = Modifier.fillMaxWidth())
            }
            status.inside -> {
                Spacer(Modifier.height(14.dp))
                SecondaryButton("Start Focus Mode", onStart, icon = Icons.Outlined.PlayArrow, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
