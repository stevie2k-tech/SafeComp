package com.safecomp.ui.auth

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.safecomp.security.AuthSession
import com.safecomp.ui.permissions.findActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Asks the phone's owner to prove it's them, with Android's own fingerprint/face/PIN prompt.
 * SafeComp never sees or stores the PIN or the fingerprint: Android checks them and tells the
 * Keystore, which then allows the vault key for 5 minutes.
 */
class AuthGate internal constructor(
    private val activity: FragmentActivity?,
    private val session: AuthSession,
    private val scope: CoroutineScope,
    private val keyguard: ManagedActivityResultLauncher<Intent, ActivityResult>,
    private val pending: MutableState<(() -> Unit)?>,
) {
    /** For settings: runs [action] at once in a fresh session, otherwise after a prompt. */
    fun requireAuth(reason: String, action: () -> Unit) {
        if (session.maySaveSettings()) action() else prompt(pinOnly = session.state.value.pinOnly, reason, action)
    }

    /**
     * Shows the prompt. [pinOnly]: the phone's fingerprints changed, so only the PIN is
     * accepted this time.
     */
    fun prompt(pinOnly: Boolean, reason: String, onSuccess: () -> Unit = {}) {
        val activity = activity ?: return
        pending.value = onSuccess
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showBiometricPrompt(activity, reason, if (pinOnly) DEVICE_CREDENTIAL else BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            // Android 8–10 can't show fingerprint and PIN in one prompt: fingerprint first,
            // with a "Use PIN" button that opens the phone's own PIN screen.
            val fingerprintReady = BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
            if (!pinOnly && fingerprintReady) showBiometricPrompt(activity, reason, BIOMETRIC_STRONG) else launchPinScreen(activity, reason)
        }
    }

    private fun showBiometricPrompt(activity: FragmentActivity, reason: String, authenticators: Int) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm it's you")
            .setSubtitle(reason)
            .setAllowedAuthenticators(authenticators)
            .apply { if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Use PIN") }
            .build()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    succeed(pinUsed = result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) launchPinScreen(activity, reason) else pending.value = null
                }
            },
        )
        prompt.authenticate(info)
    }

    @Suppress("DEPRECATION") // The Android 8–10 way to show the phone's own PIN screen.
    private fun launchPinScreen(activity: Activity, reason: String) {
        val intent = activity.getSystemService(KeyguardManager::class.java)
            .createConfirmDeviceCredentialIntent("Confirm it's you", reason)
        if (intent == null) pending.value = null else keyguard.launch(intent)
    }

    internal fun succeed(pinUsed: Boolean) {
        val action = pending.value
        pending.value = null
        scope.launch {
            session.onAuthenticated(pinUsed)
            action?.invoke()
        }
    }
}

@Composable
fun rememberAuthGate(session: AuthSession): AuthGate {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    val holder = remember { arrayOfNulls<AuthGate>(1) }
    val keyguard = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) holder[0]?.succeed(pinUsed = true) else pending.value = null
    }
    return remember(session, keyguard) {
        AuthGate(context.findActivity() as? FragmentActivity, session, scope, keyguard, pending).also { holder[0] = it }
    }
}
