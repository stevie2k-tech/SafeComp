package com.safecomp.vault

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.safecomp.data.GeofenceConfigRepository
import com.safecomp.security.SecuritySnapshot
import com.safecomp.security.SecurityStateListener
import com.safecomp.security.SecurityStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

/**
 * The vault-wide reaction to locking. Viewers already wipe their own content when they see
 * the state change. This adds the parts no single screen owns:
 *  - tell the navigation to CLOSE any open viewer (on lock, and when the app is backgrounded),
 *  - clear the app's cache directory,
 *  - expose the grace-period countdown for the "locking in 42 s" banner.
 */
class VaultSession(
    private val context: Context,
    private val securityState: SecurityStateRepository,
    private val configRepository: GeofenceConfigRepository,
    private val clock: () -> Long,
) : SecurityStateListener, DefaultLifecycleObserver {

    private val _closeViewers = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when every open file viewer must close. */
    val closeViewers: SharedFlow<Unit> = _closeViewers.asSharedFlow()

    /** The unlock session ended (Phase 7): close every viewer, like a lock. */
    fun closeAllViewers() {
        _closeViewers.tryEmit(Unit)
    }

    override suspend fun onStateChanged(old: SecuritySnapshot, new: SecuritySnapshot) {
        if (old.isUnlocked && !new.isUnlocked) {
            _closeViewers.tryEmit(Unit)
            clearCache()
        }
    }

    /**
     * Process-level ON_STOP: no SafeComp screen is visible any more (home button, app switch,
     * screen off). Close viewers so decrypted content doesn't sit in memory while the phone is
     * in someone's pocket. (Activity-level onStop would also fire on screen rotation.)
     */
    override fun onStop(owner: LifecycleOwner) {
        _closeViewers.tryEmit(Unit)
    }

    /**
     * SafeComp never writes plaintext to the cache. This is defence in depth, in case a
     * library ever does.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Seconds until the pending lock, or null when no grace period is running. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val graceSecondsLeft: Flow<Long?> = securityState.snapshot
        .distinctUntilChangedBy { it.pendingExit }
        .flatMapLatest { snapshot ->
            val pending = snapshot.pendingExit
            if (pending == null) {
                flowOf(null)
            } else {
                flow {
                    val graceMillis = (configRepository.current()?.graceSeconds ?: 0) * 1000L
                    while (true) {
                        emit(((graceMillis - (clock() - pending.sinceMillis)) / 1000).coerceAtLeast(0))
                        delay(1_000)
                    }
                }
            }
        }
}
