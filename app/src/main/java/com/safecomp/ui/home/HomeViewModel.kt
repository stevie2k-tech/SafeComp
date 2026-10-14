package com.safecomp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safecomp.AppContainer
import com.safecomp.data.GeofenceConfig
import com.safecomp.focus.FocusStatus
import com.safecomp.location.GeofenceRegistration
import com.safecomp.security.SecuritySnapshot
import com.safecomp.ui.focus.LaunchableApp
import com.safecomp.ui.permissions.PermissionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val snapshot: SecuritySnapshot = SecuritySnapshot(),
    /** Current monotonic time, ticking each second, for the countdowns. */
    val nowMillis: Long = 0,
    val config: GeofenceConfig? = null,
    val registration: GeofenceRegistration = GeofenceRegistration.NotConfigured,
    val permissions: PermissionStatus = PermissionStatus(false, false, false),
    val fileCount: Int = 0,
    val eventsToday: Int = 0,
    val focus: FocusStatus = FocusStatus(),
    /** The employee's work apps (label + icon), for quick launch during Lock Task. */
    val workApps: List<LaunchableApp> = emptyList(),
    /** False until the database values (office, counts) have arrived: hide what depends on them. */
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val permissions = MutableStateFlow(container.permissionChecker.current())

    private val ticker = flow {
        while (true) {
            emit(container.clock())
            delay(1_000)
        }
    }

    private val workApps = container.focusController.workApps
        .mapLatest { container.installedApps.describe(it) }

    private val counts = combine(
        container.vaultRepository.files,
        container.eventLog.entries,
        workApps,
    ) { files, entries, workApps ->
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        Triple(files.size, entries.count { it.timestamp >= startOfDay }, workApps)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(container.securityState.snapshot, ticker, container.focusController.status, ::Triple),
        container.geofenceConfigRepository.config,
        container.geofenceManager.registration,
        permissions,
        counts,
    ) { (snapshot, now, focus), config, registration, permissions, (fileCount, eventsToday, workApps) ->
        HomeUiState(snapshot, now, config, registration, permissions, fileCount, eventsToday, focus, workApps, loaded = true)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // Start from what's already known in memory, so the first frame never shows false
        // warnings (e.g. "precise location is off") before the real values arrive.
        HomeUiState(
            snapshot = container.securityState.snapshot.value,
            nowMillis = container.clock(),
            registration = container.geofenceManager.registration.value,
            permissions = container.permissionChecker.current(),
            focus = container.focusController.status.value,
        ),
    )

    fun refresh() {
        permissions.value = container.permissionChecker.current()
    }

    fun startFocus() {
        container.appScope.launch { container.focusController.start() }
    }

    fun stopFocus() {
        container.appScope.launch { container.focusController.stop() }
    }

    fun launchWorkApp(packageName: String) {
        container.installedApps.launch(packageName)
    }

    fun recheckNow() {
        container.appScope.launch { container.foregroundMonitor.recheckNow() }
    }
}
