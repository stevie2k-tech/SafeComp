package com.safecomp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safecomp.AppContainer
import com.safecomp.data.GeofenceConfig
import com.safecomp.location.GeofenceRegistration
import com.safecomp.security.SecuritySnapshot
import com.safecomp.ui.permissions.PermissionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
    /** False until the database values (office, counts) have arrived: hide what depends on them. */
    val loaded: Boolean = false,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val permissions = MutableStateFlow(container.permissionChecker.current())

    private val ticker = flow {
        while (true) {
            emit(container.clock())
            delay(1_000)
        }
    }

    private val counts = combine(
        container.vaultRepository.files,
        container.eventLog.entries,
    ) { files, entries ->
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        files.size to entries.count { it.timestamp >= startOfDay }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(container.securityState.snapshot, ticker, ::Pair),
        container.geofenceConfigRepository.config,
        container.geofenceManager.registration,
        permissions,
        counts,
    ) { (snapshot, now), config, registration, permissions, (fileCount, eventsToday) ->
        HomeUiState(snapshot, now, config, registration, permissions, fileCount, eventsToday, loaded = true)
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
        ),
    )

    fun refresh() {
        permissions.value = container.permissionChecker.current()
    }

    fun recheckNow() {
        container.appScope.launch { container.foregroundMonitor.recheckNow() }
    }
}
