package com.safecomp.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.safecomp.data.GeofenceConfigRepository
import com.safecomp.security.SecurityEvent
import com.safecomp.security.SecurityStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The real enforcement path. Files can only be viewed inside SafeComp, and SafeComp is only
 * visible in the foreground, so while it's visible we check location ourselves instead of
 * trusting geofence broadcasts, which can arrive late.
 *
 * Attached to the whole process lifecycle: onStart = any SafeComp screen became visible,
 * onStop = no SafeComp screen is visible any more. Nothing runs in the background.
 */
class ForegroundLocationMonitor(
    private val context: Context,
    private val locationChecker: LocationChecker,
    private val configRepository: GeofenceConfigRepository,
    private val securityState: SecurityStateRepository,
    private val geofenceManager: GeofenceManager,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private var job: Job? = null
    @Volatile private var lastFixAt = 0L

    /** Location switched on/off in quick settings: re-check immediately. */
    private val providersReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch { recheckNow() }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        ContextCompat.registerReceiver(
            context,
            providersReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lastFixAt = clock()
        job = scope.launch {
            // If INSIDE is older than the freshness window, drop to UNKNOWN until a fresh
            // fix confirms it, so a stale INSIDE can't be used to open files.
            securityState.dispatch(SecurityEvent.AppForegrounded)
            geofenceManager.register(configRepository.current())
            recheckNow()
            launch { watchdog() }
            followUpdates()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        job?.cancel()
        job = null
        runCatching { context.unregisterReceiver(providersReceiver) }
    }

    /** One fresh fix, right now. Also used by the "Check now" button and after setup. */
    suspend fun recheckNow() {
        val config = configRepository.current()
        if (config == null) {
            securityState.dispatch(SecurityEvent.Tick) // Resolves to UNKNOWN / not configured.
            return
        }
        locationChecker.unavailableReason()?.let {
            securityState.dispatch(it)
            return
        }
        val location = locationChecker.currentLocation()
        if (location == null) {
            securityState.dispatch(SecurityEvent.FixUnavailable)
        } else {
            lastFixAt = clock()
            securityState.dispatch(location.toSecurityEvent(config))
        }
    }

    private suspend fun followUpdates() {
        locationChecker.locationUpdates(UPDATE_INTERVAL_MILLIS)
            .catch { /* Permission revoked mid-stream: the watchdog reports it. */ }
            .collect { location ->
                val config = configRepository.current() ?: return@collect
                lastFixAt = clock()
                securityState.dispatch(location.toSecurityEvent(config))
            }
    }

    /** If fixes stop arriving (e.g. deep inside a building), INSIDE must not last forever. */
    private suspend fun watchdog() {
        while (true) {
            delay(WATCHDOG_PERIOD_MILLIS)
            val unavailable = locationChecker.unavailableReason()
            when {
                unavailable != null -> securityState.dispatch(unavailable)
                clock() - lastFixAt > STALE_FIX_MILLIS -> securityState.dispatch(SecurityEvent.FixUnavailable)
            }
        }
    }

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 10_000L
        const val WATCHDOG_PERIOD_MILLIS = 15_000L
        const val STALE_FIX_MILLIS = 45_000L
    }
}
