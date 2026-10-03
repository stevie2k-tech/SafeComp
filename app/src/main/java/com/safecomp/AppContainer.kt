package com.safecomp

import android.content.Context
import android.os.SystemClock
import com.safecomp.data.AppPreferences
import com.safecomp.data.GeofenceConfigRepository
import com.safecomp.data.SafeCompDatabase
import com.safecomp.location.ForegroundLocationMonitor
import com.safecomp.location.GeofenceManager
import com.safecomp.location.LocationChecker
import com.safecomp.security.SecurityStateRepository
import com.safecomp.ui.permissions.PermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    val securityState = SecurityStateRepository(geofenceConfigRepository, clock, appScope)
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
}
