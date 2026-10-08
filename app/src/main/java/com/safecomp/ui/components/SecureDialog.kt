package com.safecomp.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.safecomp.ui.theme.Sc

/**
 * Dialogs and pop-ups are drawn in their own windows, which do NOT inherit the activity's
 * FLAG_SECURE. Always use these properties (or [SecureDialog]) so they can't be screenshotted.
 */
val SecureDialogProperties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)
val SecurePopupProperties = PopupProperties(focusable = true, securePolicy = SecureFlagPolicy.SecureOn)

val DialogShape = RoundedCornerShape(28.dp)

@Composable
fun SecureDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "OK",
    onConfirm: () -> Unit = onDismiss,
    dismissLabel: String? = null,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        containerColor = Sc.SurfaceHigh,
        titleContentColor = Sc.TextPrimary,
        textContentColor = Sc.TextSecondary,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Sc.Outside else Sc.Accent, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = dismissLabel?.let { label ->
            {
                TextButton(onClick = onDismiss) {
                    Text(label, color = Sc.TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        properties = SecureDialogProperties,
    )
}

