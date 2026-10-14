package com.safecomp.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.safecomp.crypto.AuthenticationRequiredException
import com.safecomp.crypto.CanaryState
import com.safecomp.crypto.KekState
import com.safecomp.crypto.KeyManager
import com.safecomp.crypto.KeysDestroyedException
import com.safecomp.crypto.VaultKeystore
import com.safecomp.data.AppPreferences
import com.safecomp.data.AuthChange
import com.safecomp.log.EventLogRepository
import com.safecomp.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screens show about "who you are". */
data class AuthUiState(
    val status: AuthStatus = AuthStatus.NEEDS_AUTH,
    /** The phone's fingerprints changed: the next unlock must use the PIN. */
    val pinOnly: Boolean = false,
    /** This install has vault keys (so a destroyed key means files were lost). */
    val hasKeys: Boolean = false,
)

/**
 * The owner's unlock session. All the rules are in [AuthPolicy]; this class feeds it and
 * carries out the consequences:
 *  - starts a session after a successful fingerprint/face/PIN prompt ([onAuthenticated]);
 *  - loads the vault keys only while INSIDE and authenticated ([unlockVault]);
 *  - ends the session (keys dropped, viewers closed) when SafeComp goes to the background, the
 *    screen turns off, or the 5 minutes run out; drops the keys when the phone leaves the office;
 *  - notices that fingerprints changed, or that Android destroyed the key, and logs it.
 */
class AuthSession(
    context: Context,
    private val keystore: VaultKeystore,
    private val keyManager: KeyManager,
    private val preferences: AppPreferences,
    private val vaultSession: VaultSession,
    private val eventLog: EventLogRepository,
    private val securityState: SecurityStateRepository,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) : SecurityStateListener, DefaultLifecycleObserver {

    private val authenticatedAt = MutableStateFlow<Long?>(null)

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        // Screen off = the phone may be about to change hands: ask again next time.
        ContextCompat.registerReceiver(
            context.applicationContext,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = end()
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // End the session the moment the 5 minutes are up (checked once a second).
        scope.launch {
            authenticatedAt.collectLatest { at ->
                if (at == null) return@collectLatest
                while (AuthPolicy.isFresh(at, clock())) delay(1_000)
                end()
            }
        }
        scope.launch { refresh() }
    }

    fun facts(): AuthFacts = AuthFacts(
        deviceSecure = keystore.isDeviceSecure(),
        keysDestroyed = keystore.kekState() == KekState.DESTROYED,
        authenticatedAt = authenticatedAt.value,
        nowMillis = clock(),
    )

    /** Inside a fresh session. Checked before changing any setting (cheap: no Keystore call). */
    fun maySaveSettings(): Boolean = AuthPolicy.maySaveSettings(quickFacts())

    fun mayReset(): Boolean = AuthPolicy.mayReset(quickFacts())

    /** The facts the settings rules need; the destroyed-key check isn't one of them. */
    private fun quickFacts() = AuthFacts(keystore.isDeviceSecure(), keysDestroyed = false, authenticatedAt.value, clock())

    /** Re-reads the phone's state: screen lock, destroyed key, changed fingerprints. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (keystore.canaryState() == CanaryState.INVALIDATED && !preferences.authPinRequired) {
            preferences.authPinRequired = true
            eventLog.authChange(AuthChange.PIN_REQUIRED, securityState.snapshot.value)
            end()
        }
        val facts = facts()
        if (facts.keysDestroyed && !preferences.authKeysDestroyedLogged) {
            preferences.authKeysDestroyedLogged = true
            keyManager.lock()
            eventLog.authChange(AuthChange.KEYS_DESTROYED, securityState.snapshot.value)
        }
        publish(facts)
    }

    /** The owner just passed the fingerprint/face/PIN prompt. [pinUsed]: they used the PIN. */
    suspend fun onAuthenticated(pinUsed: Boolean) = withContext(Dispatchers.IO) {
        if (preferences.authPinRequired && !pinUsed) return@withContext // The prompt only offers the PIN then.
        authenticatedAt.value = clock()
        if (preferences.authPinRequired || keystore.canaryState() != CanaryState.VALID) {
            // Trust the fingerprints enrolled now (after a PIN check if they had changed).
            keystore.resetCanary()
            preferences.authPinRequired = false
        }
        eventLog.authChange(AuthChange.AUTHENTICATED, securityState.snapshot.value)
        publish(facts())
    }

    /**
     * Loads the vault keys into memory. Only while INSIDE ("where") and in a fresh session
     * ("who"). False if either is missing; the screen then shows the hedgehog or the prompt.
     */
    suspend fun unlockVault(): Boolean = withContext(Dispatchers.IO) {
        if (keyManager.isUnlocked) return@withContext true
        if (!securityState.isUnlockedNow()) return@withContext false
        if (AuthPolicy.status(facts()) != AuthStatus.AUTHENTICATED) return@withContext false
        try {
            if (keyManager.unlock()) eventLog.authChange(AuthChange.KEYS_UPGRADED, securityState.snapshot.value)
            true
        } catch (e: AuthenticationRequiredException) {
            end() // The hardware's window disagrees with ours: ask again.
            false
        } catch (e: KeysDestroyedException) {
            refresh()
            false
        }
    }

    /** Ends the session: keys out of memory, open files closed. */
    fun end() {
        authenticatedAt.value = null
        keyManager.lock()
        vaultSession.closeAllViewers()
        scope.launch { publish(withContext(Dispatchers.IO) { facts() }) }
    }

    /** After Reset: forget everything about the old keys. */
    suspend fun afterReset() {
        preferences.authPinRequired = false
        preferences.authKeysDestroyedLogged = false
        end()
        refresh()
    }

    /** SafeComp went to the background: authenticate again when it comes back. */
    override fun onStop(owner: LifecycleOwner) = end()

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { refresh() }
    }

    /** Left the office (or UNKNOWN): the keys leave memory at once. The vault is locked anyway. */
    override suspend fun onStateChanged(old: SecuritySnapshot, new: SecuritySnapshot) {
        if (old.isUnlocked && !new.isUnlocked) keyManager.lock()
    }

    private fun publish(facts: AuthFacts) {
        _state.value = AuthUiState(
            status = AuthPolicy.status(facts),
            pinOnly = AuthPolicy.pinOnly(preferences.authPinRequired),
            hasKeys = keyManager.hasKeys(),
        )
    }
}
