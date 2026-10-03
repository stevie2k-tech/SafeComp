package com.safecomp.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.SecureFlagPolicy

/**
 * Dialogs and pop-ups are drawn in their own windows, which do NOT inherit the activity's
 * FLAG_SECURE. Always use these properties (or [SecureDialog]) so they can't be screenshotted.
 */
val SecureDialogProperties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)
val SecurePopupProperties = PopupProperties(focusable = true, securePolicy = SecureFlagPolicy.SecureOn)

@Composable
fun SecureDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "OK",
    onConfirm: () -> Unit = onDismiss,
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = dismissLabel?.let { label ->
            { TextButton(onClick = onDismiss) { Text(label) } }
        },
        properties = SecureDialogProperties,
    )
}
