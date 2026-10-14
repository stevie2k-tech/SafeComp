package com.safecomp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safecomp.security.AuthStatus
import com.safecomp.security.AuthUiState
import com.safecomp.ui.components.IconBadge
import com.safecomp.ui.components.PrimaryButton
import com.safecomp.ui.components.SecondaryButton
import com.safecomp.ui.theme.Sc

/**
 * Inside the office, before the vault opens: "who you are". Also explains the two cases where
 * it can't open (no screen lock; keys destroyed because the screen lock was removed).
 */
@Composable
fun AuthGateCard(
    auth: AuthUiState,
    onUnlock: () -> Unit,
    onSetScreenLock: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            auth.status == AuthStatus.NO_SCREEN_LOCK -> {
                Header(Icons.Outlined.Lock, Sc.Unknown, "Set a screen lock first")
                Body(
                    "SafeComp checks it's you with the phone's own fingerprint, face or PIN, so it never " +
                        "stores a password of its own. That needs a screen lock (PIN, pattern or password). " +
                        "Until you set one, the vault stays locked." +
                        if (auth.hasKeys) {
                            "\n\nIf this phone had a screen lock before, removing it destroyed the vault keys: " +
                                "the files can't be opened any more and must be re-imported."
                        } else {
                            ""
                        },
                )
                PrimaryButton("Set screen lock", onSetScreenLock, Modifier.fillMaxWidth(), icon = Icons.Outlined.Lock)
                if (auth.hasKeys) {
                    SecondaryButton("Reset vault", onReset, Modifier.fillMaxWidth(), icon = Icons.Outlined.DeleteForever, color = Sc.Outside)
                }
            }
            auth.status == AuthStatus.KEYS_DESTROYED -> {
                Header(Icons.Outlined.KeyOff, Sc.Outside, "The vault keys were destroyed")
                Body(
                    "The screen lock was removed at some point, so Android destroyed the key that protects " +
                        "the vault. That's deliberate: nobody can open these files now, not even you. Reset the " +
                        "vault, then re-import the files from the company source.",
                )
                PrimaryButton("Reset vault", onReset, Modifier.fillMaxWidth(), icon = Icons.Outlined.DeleteForever)
            }
            auth.pinOnly -> {
                Header(Icons.Outlined.Password, Sc.Unknown, "Confirm with your PIN")
                Body(
                    "The fingerprints on this phone changed. To make sure nobody added their own finger, " +
                        "SafeComp needs your PIN, pattern or password once. After that, fingerprints work again.",
                )
                PrimaryButton("Unlock with PIN", onUnlock, Modifier.fillMaxWidth(), icon = Icons.Outlined.Password)
            }
            else -> {
                Header(Icons.Outlined.Fingerprint, Sc.Accent, "Confirm it's you")
                Body(
                    "You're in the office. Use your fingerprint, face or PIN to open the vault. It stays open " +
                        "for 5 minutes, and locks again sooner if you leave SafeComp or the screen turns off.",
                )
                PrimaryButton("Unlock with fingerprint or PIN", onUnlock, Modifier.fillMaxWidth(), icon = Icons.Outlined.Fingerprint)
            }
        }
    }
}

@Composable
private fun Header(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String) {
    IconBadge(icon, tint, size = 72.dp, iconSize = 34.dp)
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, color = Sc.TextPrimary, textAlign = TextAlign.Center)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Sc.TextSecondary, textAlign = TextAlign.Center)
}
