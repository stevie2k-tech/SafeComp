package com.safecomp

import android.content.Context
import android.os.SystemClock
import com.safecomp.crypto.KeyManager
import com.safecomp.data.AppPreferences
import com.safecomp.data.GeofenceConfigRepository
import com.safecomp.data.SafeCompDatabase
import com.safecomp.location.ForegroundLocationMonitor
import com.safecomp.log.EventLogRepository
import com.safecomp.location.GraceExpiryScheduler
import com.safecomp.location.GeofenceManager
import com.safecomp.location.LocationChecker
import com.safecomp.notify.Notifier
import com.safecomp.notify.VaultStatusAnnouncer
import com.safecomp.security.SecuritySnapshotStore
import com.safecomp.security.SecurityStateRepository
import com.safecomp.ui.permissions.PermissionChecker
import com.safecomp.vault.VaultRepository
import com.safecomp.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Holds the app's long-lived objects and wires them together.
 *
 * We use a hand-written container instead of a DI framework like Hilt: it is a few lines,
 * has no code generation, and makes it obvious where every dependency comes from.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the process. Used for work that must outlive a single screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Monotonic: unaffected by the user changing the date/time in Settings. */
    val clock: () -> Long = SystemClock::elapsedRealtime

    val preferences = AppPreferences(appContext)
    val permissionChecker = PermissionChecker(appContext)

    private val database = SafeCompDatabase.build(appContext)
    val geofenceConfigRepository = GeofenceConfigRepository(database.geofenceConfigDao())

    private val snapshotStore = SecuritySnapshotStore(appContext)
    val securityState = SecurityStateRepository(geofenceConfigRepository, snapshotStore, clock, appScope)
    val locationChecker = LocationChecker(appContext, permissionChecker)
    val geofenceManager = GeofenceManager(appContext, permissionChecker)
    val foregroundMonitor = ForegroundLocationMonitor(
        context = appContext,
        locationChecker = locationChecker,
        configRepository = geofenceConfigRepository,
        securityState = securityState,
        geofenceManager = geofenceManager,
        clock = clock,
        scope = appScope,
    )

    val eventLog = EventLogRepository(database.eventLogDao())

    private val keyManager = KeyManager(appContext)
    val vaultRepository = VaultRepository(
        context = appContext,
        dao = database.vaultFileDao(),
        keyManager = keyManager,
        securityState = securityState,
        eventLog = eventLog,
        scope = appScope,
    )

    val vaultSession = VaultSession(appContext, securityState, geofenceConfigRepository, clock)
    private val notifier = Notifier(appContext)

    init {
        securityState.addListener(eventLog)
        securityState.addListener(vaultSession)
        securityState.addListener(VaultStatusAnnouncer(notifier, snapshotStore))
        securityState.addListener(GraceExpiryScheduler(appContext, geofenceConfigRepository, clock))
        appScope.launch {
            vaultRepository.cleanUpOrphans()
            vaultSession.clearCache()
        }
    }
}
