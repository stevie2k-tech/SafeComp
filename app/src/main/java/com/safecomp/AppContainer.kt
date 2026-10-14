package com.safecomp

import android.content.Context
import android.os.SystemClock
import com.safecomp.crypto.KeyManager
import com.safecomp.crypto.VaultKeystore
import com.safecomp.data.AppPreferences
import com.safecomp.data.GeofenceConfigRepository
import com.safecomp.data.SafeCompDatabase
import com.safecomp.demo.DemoModeController
import com.safecomp.demo.DemoModes
import com.safecomp.focus.FocusAdmins
import com.safecomp.focus.FocusController
import com.safecomp.location.ForegroundLocationMonitor
import com.safecomp.log.EventLogRepository
import com.safecomp.location.GraceExpiryScheduler
import com.safecomp.location.GeofenceManager
import com.safecomp.location.LocationChecker
import com.safecomp.notify.Notifier
import com.safecomp.notify.VaultStatusAnnouncer
import com.safecomp.security.AuthSession
import com.safecomp.security.SecuritySnapshotStore
import com.safecomp.security.SecurityStateRepository
import com.safecomp.ui.focus.InstalledApps
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

    /** Demo mode: a working implementation in debug builds, an inert stub in release builds. */
    val demoMode: DemoModeController = DemoModes.create(
        context = appContext,
        securityState = securityState,
        scope = appScope,
        onSimulationEnded = { foregroundMonitor.recheckNow() },
    )
    private val isDemoActive: () -> Boolean = { demoMode.settings.value.active }

    val eventLog = EventLogRepository(database.eventLogDao(), isDemoActive)

    val vaultSession = VaultSession(appContext, securityState, geofenceConfigRepository, clock)

    private val vaultKeystore = VaultKeystore(appContext)
    private val keyManager = KeyManager(appContext, vaultKeystore)

    /** "Who you are" (Phase 7): fingerprint/face/PIN session. Location is "where you are". */
    val authSession = AuthSession(
        context = appContext,
        keystore = vaultKeystore,
        keyManager = keyManager,
        preferences = preferences,
        vaultSession = vaultSession,
        eventLog = eventLog,
        securityState = securityState,
        clock = clock,
        scope = appScope,
    )

    val vaultRepository = VaultRepository(
        context = appContext,
        dao = database.vaultFileDao(),
        keyManager = keyManager,
        authSession = authSession,
        securityState = securityState,
        eventLog = eventLog,
        scope = appScope,
    )
    private val notifier = Notifier(appContext)

    /** Apps with a home-screen icon, for the Focus Mode work-app picker. */
    val installedApps = InstalledApps(appContext)

    /** Focus Mode: reads the security state, never changes it. */
    val focusController = FocusController(
        context = appContext,
        preferences = preferences,
        workAppDao = database.workAppDao(),
        admin = FocusAdmins.create(appContext),
        securityState = securityState,
        eventLog = eventLog,
        notifier = notifier,
        isDemoActive = isDemoActive,
        clock = clock,
        scope = appScope,
    )

    init {
        securityState.addListener(eventLog)
        securityState.addListener(vaultSession)
        securityState.demoSettings = demoMode.settings
        securityState.addListener(VaultStatusAnnouncer(notifier, snapshotStore, isDemoActive))
        securityState.addListener(GraceExpiryScheduler(appContext, geofenceConfigRepository, clock))
        securityState.addListener(focusController)
        securityState.addListener(authSession)
        appScope.launch {
            vaultRepository.cleanUpOrphans()
            vaultSession.clearCache()
        }
    }
}
