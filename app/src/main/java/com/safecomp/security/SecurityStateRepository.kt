package com.safecomp.security

import com.safecomp.BuildConfig
import com.safecomp.data.GeofenceConfig
import com.safecomp.demo.DemoSettings
import com.safecomp.data.GeofenceConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Reacts to state changes (lock handling, notifications, background grace timer).
 * Called in the order the changes happened. Must not call [SecurityStateRepository.dispatch].
 */
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
    private val store: SnapshotStore,
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
    /** Orders listener calls. Listeners must not call [dispatch] themselves (it would deadlock). */
    private val listenerMutex = Mutex()
    private val listeners = mutableListOf<SecurityStateListener>()

    fun addListener(listener: SecurityStateListener) {
        synchronized(listeners) { listeners += listener }
    }

    init {
        // While a grace period is running, tick once a second so it expires on time even if
        // no new location data arrives. It must LOOP: a tick before the deadline doesn't
        // change the state, so the flow doesn't emit and wouldn't restart a one-shot tick.
        // collectLatest cancels the loop as soon as the state does change.
        // (If the process is frozen in the background, GraceExpiryWorker does the same job.)
        scope.launch {
            _snapshot.collectLatest { snapshot ->
                while (snapshot.pendingExit != null) {
                    delay(1_000)
                    dispatch(SecurityEvent.Tick)
                }
            }
        }
    }

    /** Set by the container. Always "off" in release builds (see DemoModeController). */
    var demoSettings: StateFlow<DemoSettings> = MutableStateFlow(DemoSettings())

    /**
     * @param simulated true only for input from debug demo mode. Release builds refuse it,
     *   as a second barrier behind the fact that release builds contain no code that sends it.
     */
    suspend fun dispatch(event: SecurityEvent, simulated: Boolean = false) {
        if (simulated && !BuildConfig.DEBUG) return
        val demo = if (BuildConfig.DEBUG) demoSettings.value else DemoSettings()
        // While a position is simulated, real location input is ignored, so bad GPS on
        // presentation day can't fight the demo.
        if (demo.simulation != null && !simulated && event.isRealLocationInput()) return

        // A state change and its listeners run to completion even if the caller is cancelled
        // (e.g. the foreground monitor stops because the app went to the background). Otherwise
        // a listener such as the one that schedules the background lock could be skipped.
        withContext(NonCancellable) { applyEvent(event, demo) }
    }

    private suspend fun applyEvent(event: SecurityEvent, demo: DemoSettings) {
        val params = configRepository.current()?.toParams()
        mutex.lock()
        val old: SecuritySnapshot
        val new: SecuritySnapshot
        try {
            old = _snapshot.value
            new = SecurityStateMachine.reduce(
                current = old,
                event = event,
                nowMillis = clock(),
                params = params,
                allowMockLocations = demo.allowMockLocations,
            )
            _snapshot.value = new
            if (new != old) store.save(new)
            // Hand-over-hand: take the listener lock BEFORE releasing the state lock, so
            // listeners always see changes in the order they happened, even if a listener is
            // slow. (Otherwise a later "locked" could be notified before an earlier
            // "unlocked", leaving the wrong notification and log order.)
            if (new != old) listenerMutex.lock()
        } finally {
            mutex.unlock()
        }
        if (new != old) {
            try {
                synchronized(listeners) { listeners.toList() }.forEach { it.onStateChanged(old, new) }
            } finally {
                listenerMutex.unlock()
            }
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

private fun SecurityEvent.isRealLocationInput(): Boolean = when (this) {
    is SecurityEvent.LocationFix,
    SecurityEvent.GeofenceEntered,
    SecurityEvent.GeofenceDwelled,
    SecurityEvent.GeofenceExited,
    SecurityEvent.MockLocationDetected,
    SecurityEvent.PermissionMissing,
    SecurityEvent.LocationServicesOff,
    SecurityEvent.FixUnavailable,
    -> true
    SecurityEvent.ConfigChanged, SecurityEvent.AppForegrounded, SecurityEvent.Tick -> false
}

fun GeofenceConfig.toParams() = GeofenceParams(
    radiusMeters = radiusMeters,
    dwellMillis = dwellSeconds * 1000L,
    graceMillis = graceSeconds * 1000L,
)
