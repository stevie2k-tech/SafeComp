package com.safecomp.security

import com.safecomp.data.GeofenceConfig
import com.safecomp.data.GeofenceConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reacts to state changes (lock handling, notifications, background grace timer). */
fun interface SecurityStateListener {
    suspend fun onStateChanged(old: SecuritySnapshot, new: SecuritySnapshot)
}

/**
 * The single source of truth for "may the vault be opened right now?".
 *
 * Every input (foreground fixes, geofence broadcasts, permission checks, ticks) goes through
 * [dispatch], which runs the pure [SecurityStateMachine] under a lock, so events from
 * different threads can't interleave. Each new state is saved, so it survives the process
 * being killed, and then passed to the listeners. dispatch() returns only after the listeners
 * have run, so a background job or receiver can't be frozen half-way through a lock.
 */
class SecurityStateRepository(
    private val configRepository: GeofenceConfigRepository,
    private val store: SecuritySnapshotStore,
    private val clock: () -> Long,
    scope: CoroutineScope,
) {
    private val _snapshot = MutableStateFlow(
        store.load()?.let { (saved, savedBoot) ->
            SecurityStateMachine.restore(saved, savedBoot, store.currentBootCount(), clock())
        } ?: SecuritySnapshot(),
    )
    val snapshot: StateFlow<SecuritySnapshot> = _snapshot.asStateFlow()

    private val mutex = Mutex()
    private val listeners = mutableListOf<SecurityStateListener>()

    fun addListener(listener: SecurityStateListener) {
        synchronized(listeners) { listeners += listener }
    }

    init {
        // While a grace period is running, tick once a second so it expires on time even if
        // no new location data arrives. collectLatest restarts the loop on every change.
        // (If the process is frozen in the background, GraceExpiryWorker does the same job.)
        scope.launch {
            _snapshot.collectLatest { snapshot ->
                if (snapshot.pendingExit != null) {
                    delay(1_000)
                    dispatch(SecurityEvent.Tick)
                }
            }
        }
    }

    suspend fun dispatch(event: SecurityEvent) {
        val params = configRepository.current()?.toParams()
        val (old, new) = mutex.withLock {
            val old = _snapshot.value
            val new = SecurityStateMachine.reduce(
                current = old,
                event = event,
                nowMillis = clock(),
                params = params,
            )
            _snapshot.value = new
            if (new != old) store.save(new)
            old to new
        }
        if (new != old) {
            // Outside the lock: a listener may dispatch an event itself.
            synchronized(listeners) { listeners.toList() }.forEach { it.onStateChanged(old, new) }
        }
    }

    /**
     * The check to call right before decrypting anything. It applies elapsed time first, so an
     * expired grace period locks the vault even if the ticker hasn't run yet (e.g. in Doze).
     */
    suspend fun isUnlockedNow(): Boolean {
        dispatch(SecurityEvent.Tick)
        return _snapshot.value.isUnlocked
    }
}

fun GeofenceConfig.toParams() = GeofenceParams(
    radiusMeters = radiusMeters,
    dwellMillis = dwellSeconds * 1000L,
    graceMillis = graceSeconds * 1000L,
)
