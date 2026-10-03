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

/**
 * The single source of truth for "may the vault be opened right now?".
 *
 * Every input (foreground fixes, geofence broadcasts, permission checks, ticks) goes through
 * [dispatch], which runs the pure [SecurityStateMachine] under a lock, so events from
 * different threads can't interleave.
 */
class SecurityStateRepository(
    private val configRepository: GeofenceConfigRepository,
    private val clock: () -> Long,
    scope: CoroutineScope,
) {
    private val _snapshot = MutableStateFlow(SecuritySnapshot())
    val snapshot: StateFlow<SecuritySnapshot> = _snapshot.asStateFlow()

    private val mutex = Mutex()

    init {
        // While a grace period is running, tick once a second so it expires on time even if
        // no new location data arrives. collectLatest restarts the loop on every change.
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
        mutex.withLock {
            _snapshot.value = SecurityStateMachine.reduce(
                current = _snapshot.value,
                event = event,
                nowMillis = clock(),
                params = params,
            )
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
