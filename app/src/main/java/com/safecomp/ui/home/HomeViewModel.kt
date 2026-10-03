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
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val permissions = MutableStateFlow(container.permissionChecker.current())

    private val ticker = flow {
        while (true) {
            emit(container.clock())
            delay(1_000)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        container.securityState.snapshot,
        ticker,
        container.geofenceConfigRepository.config,
        container.geofenceManager.registration,
        permissions,
    ) { snapshot, now, config, registration, permissions ->
        HomeUiState(snapshot, now, config, registration, permissions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        permissions.value = container.permissionChecker.current()
    }

    fun recheckNow() {
        container.appScope.launch { container.foregroundMonitor.recheckNow() }
    }
}
