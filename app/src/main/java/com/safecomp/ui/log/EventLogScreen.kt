package com.safecomp.ui.log

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.data.EventLogEntry
import com.safecomp.ui.components.SecureDialog
import com.safecomp.ui.theme.InsideGreen
import com.safecomp.ui.theme.OutsideRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(viewModel: EventLogViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = state.unlocked && state.rows.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear log")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }
            Text(
                "Stored only on this phone for ${EventLogEntry.RETENTION_DAYS} days. Never uploaded. " +
                    "No locations are recorded." +
                    if (state.unlocked) "" else " File names are hidden outside the office.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text("Nothing logged yet.", textAlign = TextAlign.Center)
                }
                else -> LazyColumn { items(state.rows, key = { it.id }) { LogRowItem(it) } }
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
        )
    }
}

@Composable
private fun LogRowItem(row: LogRow) {
    val context = LocalContext.current
    val (icon, tint) = when (row.kind) {
        LogRowKind.UNLOCKED -> Icons.Outlined.LockOpen to InsideGreen
        LogRowKind.LOCKED -> Icons.Outlined.Lock to OutsideRed
        LogRowKind.OPEN_ALLOWED -> Icons.Outlined.TaskAlt to InsideGreen
        LogRowKind.OPEN_DENIED -> Icons.Outlined.Block to OutsideRed
    }
    val time = DateUtils.formatDateTime(
        context,
        row.timestamp,
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
    )
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        headlineContent = { Text(row.title, color = if (row.kind == LogRowKind.OPEN_DENIED) OutsideRed else Color.Unspecified) },
        supportingContent = { Text("$time · ${row.detail}") },
    )
}
