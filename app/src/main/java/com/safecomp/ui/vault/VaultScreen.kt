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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecomp.data.FileKind
import com.safecomp.ui.components.GraceBanner
import com.safecomp.ui.components.SecureDialog
import com.safecomp.ui.components.SecureDialogProperties
import com.safecomp.ui.locked.LockedHedgehogScreen
import com.safecomp.vault.ImportProgress
import kotlinx.coroutines.flow.Flow

/** Types the vault can preview. Other types aren't offered in the picker. */
private val ImportMimeTypes = arrayOf(
    "image/*", "video/*", "audio/*", "application/pdf", "text/*", "application/json",
)

@OptIn(ExperimentalMaterial3Api::class)
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

    // The system file picker (Storage Access Framework). It grants access only to the files
    // the user picks, so SafeComp needs no broad storage permission.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.onFilesPicked(uris)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Vault") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                GraceBanner(graceSecondsLeft)
            }
        },
        floatingActionButton = {
            if (state is VaultUiState.Unlocked) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(ImportMimeTypes) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Import") },
                )
            }
        },
    ) { padding ->
        when (val s = state) {
            VaultUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is VaultUiState.Locked -> LockedHedgehogScreen(s.reason, Modifier.padding(padding))
            is VaultUiState.Unlocked -> Column(Modifier.fillMaxSize().padding(padding)) {
                progress?.let { ImportProgressCard(it, viewModel::dismissImportProgress) }
                if (s.items.isEmpty()) {
                    EmptyVault()
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(s.items, key = { it.id }) { item ->
                            VaultRow(item, onOpen = { onOpenFile(item.id) }, onDelete = { confirmDelete = item })
                        }
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
        )
    }
}

@Composable
private fun EmptyVault() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("The vault is empty", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap Import to add documents, photos, videos or audio. Each file is encrypted as " +
                "it's imported and can only be opened in the office.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VaultRow(item: VaultItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val details = listOf(
        item.kind.label,
        Formatter.formatShortFileSize(context, item.sizeBytes),
        DateUtils.formatDateTime(context, item.importedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
    ).joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(item.kind.icon, contentDescription = null) },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details) },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
        },
    )
}

@Composable
private fun ImportProgressCard(progress: ImportProgress, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (progress.running) {
                Text("Encrypting ${progress.done + progress.failed + 1} of ${progress.total}…")
                LinearProgressIndicator(
                    progress = { (progress.done + progress.failed).toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Imported ${progress.done} of ${progress.total} files.", style = MaterialTheme.typography.titleSmall)
                if (progress.failed > 0) Text("${progress.failed} couldn't be imported.")
                if (progress.originalsKept > 0) {
                    Text(
                        "${progress.originalsKept} original(s) couldn't be deleted by SafeComp. An " +
                            "unencrypted copy is still on the phone; please delete it yourself.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
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
        title = { Text(if (count == 1) "Import 1 file?" else "Import $count files?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Each file will be encrypted into the vault.")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { deleteOriginals = !deleteOriginals },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = deleteOriginals, onCheckedChange = { deleteOriginals = it })
                    Text("Delete the originals (recommended)")
                }
                Text(
                    "Otherwise an unencrypted copy stays on the phone, outside SafeComp's protection.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(deleteOriginals) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
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
