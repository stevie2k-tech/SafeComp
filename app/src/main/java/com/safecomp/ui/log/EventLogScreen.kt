package com.safecomp.ui.log

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.data.EventLogEntry
import com.safecomp.ui.components.CircleIconButton
import com.safecomp.ui.components.GlassCard
import com.safecomp.ui.components.IconBadge
import com.safecomp.ui.components.ScTopBar
import com.safecomp.ui.components.ScreenBackground
import com.safecomp.ui.components.SectionLabel
import com.safecomp.ui.components.SecureDialog
import com.safecomp.ui.theme.Sc
import java.util.Calendar

@Composable
fun EventLogScreen(viewModel: EventLogViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ScreenBackground(glow = Sc.Violet) {
        Column(Modifier.fillMaxSize()) {
            ScTopBar(
                title = "Activity",
                subtitle = "LOCAL EVENT LOG · ${EventLogEntry.RETENTION_DAYS} DAYS",
                onBack = onBack,
                actions = {
                    CircleIconButton(
                        Icons.Outlined.DeleteSweep,
                        "Clear log",
                        { confirmClear = true },
                        enabled = state.unlocked && state.rows.isNotEmpty(),
                    )
                },
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogFilter.entries.forEach { f -> FilterPill(f.label, selected = state.filter == f) { viewModel.setFilter(f) } }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Sc.Accent) }
                state.rows.isEmpty() -> EmptyLog()
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)) {
                    item(key = "privacy") { PrivacyNote(state.unlocked) }
                    itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                        val previous = state.rows.getOrNull(index - 1)
                        if (previous == null || !sameDay(previous.timestamp, row.timestamp)) {
                            SectionLabel(dayLabel(context, row.timestamp), Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
                        }
                        val next = state.rows.getOrNull(index + 1)
                        TimelineRow(row, isLastOfDay = next == null || !sameDay(next.timestamp, row.timestamp))
                    }
                }
            }
        }
    }

    if (confirmClear) {
        SecureDialog(
            title = "Clear the event log?",
            text = "All entries will be deleted from this phone.",
            confirmLabel = "Clear",
            onConfirm = {
                viewModel.clear()
                confirmClear = false
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Sc.Bg else Sc.TextSecondary,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Sc.Accent else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) Sc.Accent else Sc.Hairline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun PrivacyNote(unlocked: Boolean) {
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Shield, Sc.Inside, size = 36.dp, iconSize = 18.dp)
            Text(
                "Stays on this phone. Never uploaded. No locations recorded." +
                    if (unlocked) "" else " File names are hidden outside the office.",
                style = MaterialTheme.typography.bodySmall,
                color = Sc.TextSecondary,
            )
        }
    }
}

@Composable
private fun EmptyLog() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        IconBadge(Icons.Outlined.Shield, Sc.Violet, size = 72.dp, iconSize = 32.dp)
        Text("Nothing logged yet", style = MaterialTheme.typography.titleLarge, color = Sc.TextPrimary)
        Text(
            "Vault locks, unlocks and file openings will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = Sc.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimelineRow(row: LogRow, isLastOfDay: Boolean) {
    val context = LocalContext.current
    val (icon, color) = when (row.kind) {
        LogRowKind.UNLOCKED -> Icons.Outlined.LockOpen to Sc.Inside
        LogRowKind.LOCKED -> Icons.Outlined.Lock to Sc.Outside
        LogRowKind.OPEN_ALLOWED -> Icons.Outlined.TaskAlt to Sc.Sky
        LogRowKind.OPEN_DENIED -> Icons.Outlined.Block to Sc.Outside
        LogRowKind.FOCUS -> Icons.Outlined.CenterFocusStrong to Sc.Violet
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Timeline rail: glowing dot + line down to the next event.
        Box(Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!isLastOfDay) {
                Canvas(Modifier.fillMaxHeight().width(2.dp)) {
                    drawLine(Sc.Hairline, Offset(size.width / 2, 34.dp.toPx()), Offset(size.width / 2, size.height), strokeWidth = 2.dp.toPx())
                }
            }
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.08f))))
                    .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (row.kind == LogRowKind.OPEN_DENIED) Sc.Outside else Sc.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DateUtils.formatDateTime(context, row.timestamp, DateUtils.FORMAT_SHOW_TIME),
                    style = MaterialTheme.typography.labelMedium,
                    color = Sc.TextTertiary,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = Sc.TextSecondary)
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(context: android.content.Context, timestamp: Long): String = when {
    DateUtils.isToday(timestamp) -> "Today"
    DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
}
