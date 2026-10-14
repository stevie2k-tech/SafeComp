package com.safecomp.ui.vault

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.data.FileKind
import com.safecomp.security.StateReason
import com.safecomp.ui.components.DialogShape
import com.safecomp.ui.components.GlassCard
import com.safecomp.ui.components.GraceBanner
import com.safecomp.ui.components.IconBadge
import com.safecomp.ui.components.PrimaryButton
import com.safecomp.ui.components.ScTopBar
import com.safecomp.ui.components.ScreenBackground
import com.safecomp.ui.components.SectionLabel
import com.safecomp.ui.components.SecureDialog
import com.safecomp.ui.components.SecureDialogProperties
import com.safecomp.ui.locked.LockedHedgehogScreen
import com.safecomp.security.AuthStatus
import com.safecomp.ui.auth.AuthGateCard
import com.safecomp.ui.auth.rememberAuthGate
import com.safecomp.ui.permissions.openScreenLockSettings
import com.safecomp.ui.theme.Sc
import com.safecomp.vault.ImportProgress
import kotlinx.coroutines.flow.Flow

/** Types the vault can preview. Other types aren't offered in the picker. */
private val ImportMimeTypes = arrayOf(
    "image/*", "video/*", "audio/*", "application/pdf", "text/*", "application/json",
)

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    graceSecondsLeft: Flow<Long?>,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pending by viewModel.pendingImport.collectAsStateWithLifecycle()
    val progress by viewModel.importProgress.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<VaultItem?>(null) }
    val authGate = rememberAuthGate(viewModel.authSessionForUi)
    val context = LocalContext.current

    // The system file picker (Storage Access Framework). It grants access only to the files
    // the user picks, so SafeComp needs no broad storage permission.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.onFilesPicked(uris)
    }

    val glow = when (val s = state) {
        is VaultUiState.Unlocked -> Sc.Inside
        is VaultUiState.Locked -> if (s.reason == StateReason.OUTSIDE_FIX || s.reason == StateReason.OUTSIDE_GEOFENCE) Sc.Outside else Sc.Unknown
        is VaultUiState.NeedsAuth -> Sc.Accent
        VaultUiState.Loading -> null
    }

    ScreenBackground(glow = glow) {
        Column(Modifier.fillMaxSize()) {
            val unlocked = state as? VaultUiState.Unlocked
            ScTopBar(
                title = "Vault",
                subtitle = when (val s = state) {
                    is VaultUiState.Unlocked -> "${s.items.size} FILE${if (s.items.size == 1) "" else "S"} · AES-256-GCM"
                    is VaultUiState.Locked -> "LOCKED"
                    is VaultUiState.NeedsAuth -> "IN THE OFFICE · CONFIRM IT'S YOU"
                    VaultUiState.Loading -> " "
                },
                onBack = onBack,
            )
            GraceBanner(graceSecondsLeft)

            when (val s = state) {
                VaultUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Sc.Accent)
                }
                is VaultUiState.Locked -> LockedHedgehogScreen(s.reason)
                is VaultUiState.NeedsAuth -> AuthGateCard(
                    auth = s.auth,
                    onUnlock = { authGate.prompt(s.auth.pinOnly, "Open the vault") },
                    onSetScreenLock = { context.openScreenLockSettings() },
                    onReset = {
                        // With a screen lock, Reset needs authentication too.
                        if (s.auth.status == AuthStatus.NO_SCREEN_LOCK) {
                            viewModel.resetVault()
                        } else {
                            authGate.requireAuth("Reset the vault") { viewModel.resetVault() }
                        }
                    },
                )
                is VaultUiState.Unlocked -> Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        progress?.let { item(key = "progress") { ImportProgressCard(it, viewModel::dismissImportProgress) } }
                        if (s.items.isEmpty()) {
                            item(key = "empty") { EmptyVault() }
                        } else {
                            item(key = "label") { SectionLabel("Encrypted files", Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp)) }
                            items(s.items, key = { it.id }) { item ->
                                VaultRow(item, onOpen = { onOpenFile(item.id) }, onDelete = { confirmDelete = item })
                            }
                        }
                    }
                    if (unlocked != null) {
                        PrimaryButton(
                            text = "Import files",
                            onClick = { picker.launch(ImportMimeTypes) },
                            icon = Icons.Outlined.Add,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(20.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (pending.isNotEmpty()) {
        ImportConfirmDialog(
            count = pending.size,
            onConfirm = viewModel::confirmImport,
            onCancel = viewModel::cancelImport,
        )
    }
    confirmDelete?.let { item ->
        SecureDialog(
            title = "Delete from vault?",
            text = "\"${item.name}\" will be permanently deleted from SafeComp.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(item.id)
                confirmDelete = null
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun EmptyVault() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(Icons.Outlined.UploadFile, Sc.Accent, size = 76.dp, iconSize = 34.dp)
        Text("Your vault is empty", style = MaterialTheme.typography.titleLarge, color = Sc.TextPrimary)
        Text(
            "Import documents, photos, videos or audio. Each file is encrypted as it's imported " +
                "and only opens inside the office.",
            style = MaterialTheme.typography.bodyMedium,
            color = Sc.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VaultRow(item: VaultItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val meta = listOf(
        item.kind.label,
        Formatter.formatShortFileSize(context, item.sizeBytes),
        DateUtils.formatDateTime(context, item.importedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
    ).joinToString(" · ").uppercase()
    GlassCard(onClick = onOpen, contentPadding = PaddingValues(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(item.kind.icon, item.kind.tint)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Sc.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(meta, style = MaterialTheme.typography.labelSmall, color = Sc.TextTertiary, maxLines = 1)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Sc.TextTertiary)
            }
        }
    }
}

@Composable
private fun ImportProgressCard(progress: ImportProgress, onDismiss: () -> Unit) {
    GlassCard(tint = if (progress.originalsKept > 0) Sc.Unknown else Sc.Accent) {
        if (progress.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = Sc.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text("Encrypting", style = MaterialTheme.typography.titleSmall, color = Sc.TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("${progress.done + progress.failed + 1}/${progress.total}", style = MaterialTheme.typography.labelMedium, color = Sc.Accent)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (progress.done + progress.failed).toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
                color = Sc.Accent,
                trackColor = Sc.Hairline,
            )
        } else {
            Text(
                "Imported ${progress.done} of ${progress.total} file${if (progress.total == 1) "" else "s"}",
                style = MaterialTheme.typography.titleSmall,
                color = Sc.TextPrimary,
            )
            if (progress.failed > 0) {
                Text("${progress.failed} couldn't be imported.", style = MaterialTheme.typography.bodySmall, color = Sc.Outside)
            }
            if (progress.originalsKept > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${progress.originalsKept} original(s) couldn't be deleted. An unencrypted copy is " +
                        "still on the phone; please delete it yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sc.Unknown,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Done", color = Sc.Accent) }
            }
        }
    }
}

@Composable
private fun ImportConfirmDialog(count: Int, onConfirm: (Boolean) -> Unit, onCancel: () -> Unit) {
    var deleteOriginals by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onCancel,
        properties = SecureDialogProperties,
        shape = DialogShape,
        containerColor = Sc.SurfaceHigh,
        icon = { IconBadge(Icons.Outlined.Lock, Sc.Accent) },
        title = {
            Text(
                if (count == 1) "Encrypt 1 file?" else "Encrypt $count files?",
                style = MaterialTheme.typography.titleLarge,
                color = Sc.TextPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Each file is encrypted with AES-256-GCM into the vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sc.TextSecondary,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { deleteOriginals = !deleteOriginals },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = deleteOriginals,
                        onCheckedChange = { deleteOriginals = it },
                        colors = CheckboxDefaults.colors(checkedColor = Sc.Accent, checkmarkColor = Sc.Bg),
                    )
                    Text("Delete the originals (recommended)", style = MaterialTheme.typography.bodyMedium, color = Sc.TextPrimary)
                }
                Text(
                    "Otherwise an unencrypted copy stays on the phone, outside SafeComp's protection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sc.TextTertiary,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(deleteOriginals) }) { Text("Encrypt", color = Sc.Accent) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel", color = Sc.TextSecondary) } },
    )
}

private val FileKind.label: String
    get() = when (this) {
        FileKind.IMAGE -> "Image"
        FileKind.PDF -> "PDF"
        FileKind.TEXT -> "Text"
        FileKind.VIDEO -> "Video"
        FileKind.AUDIO -> "Audio"
        FileKind.OTHER -> "File"
    }

private val FileKind.icon: ImageVector
    get() = when (this) {
        FileKind.IMAGE -> Icons.Outlined.Image
        FileKind.PDF -> Icons.Outlined.PictureAsPdf
        FileKind.TEXT -> Icons.AutoMirrored.Outlined.TextSnippet
        FileKind.VIDEO -> Icons.Outlined.Movie
        FileKind.AUDIO -> Icons.Outlined.AudioFile
        FileKind.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }

private val FileKind.tint: Color
    get() = when (this) {
        FileKind.IMAGE -> Sc.Violet
        FileKind.PDF -> Sc.Outside
        FileKind.TEXT -> Sc.Sky
        FileKind.VIDEO -> Sc.Unknown
        FileKind.AUDIO -> Sc.Inside
        FileKind.OTHER -> Sc.TextSecondary
    }
