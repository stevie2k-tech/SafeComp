package com.safecomp.ui.geofence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safecomp.AppContainer
import com.safecomp.data.GeofenceConfig
import com.safecomp.location.isMockCompat
import com.safecomp.security.SecurityEvent
import com.safecomp.security.SecurityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class GeofenceSetupUiState(
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: Float = GeofenceConfig.DEFAULT_RADIUS_METERS,
    val dwellSeconds: Int = GeofenceConfig.DEFAULT_DWELL_SECONDS,
    val graceSeconds: Int = GeofenceConfig.DEFAULT_GRACE_SECONDS,
    val isFirstSetup: Boolean = true,
    /** See [GeofenceSetupViewModel.canEdit]. */
    val canEdit: Boolean = true,
    val locating: Boolean = false,
    val locationMessage: String? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
)

class GeofenceSetupViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(GeofenceSetupUiState())
    val uiState: StateFlow<GeofenceSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.geofenceConfigRepository.current()?.let { config ->
                _uiState.update {
                    it.copy(
                        latitude = config.latitude.format(),
                        longitude = config.longitude.format(),
                        radiusMeters = config.radiusMeters,
                        dwellSeconds = config.dwellSeconds,
                        graceSeconds = config.graceSeconds,
                        isFirstSetup = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(container.geofenceConfigRepository.config, container.securityState.snapshot) { config, snapshot ->
                canEdit(config, snapshot.state)
            }.collect { editable -> _uiState.update { it.copy(canEdit = editable) } }
        }
    }

    fun onLatitudeChange(value: String) = _uiState.update { it.copy(latitude = value, error = null) }
    fun onLongitudeChange(value: String) = _uiState.update { it.copy(longitude = value, error = null) }
    fun onRadiusChange(value: Float) = _uiState.update { it.copy(radiusMeters = value) }
    fun onDwellChange(value: Int) = _uiState.update { it.copy(dwellSeconds = value) }
    fun onGraceChange(value: Int) = _uiState.update { it.copy(graceSeconds = value) }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(locating = true, locationMessage = null, error = null) }
            val checker = container.locationChecker
            val message: String
            val unavailable = checker.unavailableReason()
            if (unavailable != null) {
                message = if (unavailable == SecurityEvent.PermissionMissing) {
                    "Precise location permission is needed."
                } else {
                    "Location services are off. Turn them on and try again."
                }
            } else {
                val location = checker.currentLocation()
                message = when {
                    location == null -> "Couldn't get a location fix. Move near a window and try again."
                    // A faked office location would let anyone unlock anywhere. Rejected.
                    location.isMockCompat() && !container.demoMode.settings.value.allowMockLocations ->
                        "A mock (fake) location was detected and was not accepted."
                    else -> {
                        _uiState.update {
                            it.copy(latitude = location.latitude.format(), longitude = location.longitude.format())
                        }
                        val accuracy = location.accuracy.roundToInt()
                        if (accuracy > 50) {
                            "Location set, but accuracy is only ±$accuracy m. Try again outdoors for a better fix."
                        } else {
                            "Location set (accuracy ±$accuracy m)."
                        }
                    }
                }
            }
            _uiState.update { it.copy(locating = false, locationMessage = message) }
        }
    }

    fun save() {
        val state = _uiState.value
        val lat = state.latitude.parseCoordinate()
        val lng = state.longitude.parseCoordinate()
        val error = when {
            lat == null || lat !in -90.0..90.0 -> "Latitude must be a number between -90 and 90."
            lng == null || lng !in -180.0..180.0 -> "Longitude must be a number between -180 and 180."
            else -> null
        }
        if (error != null || lat == null || lng == null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        // appScope, not viewModelScope: registering must finish even if the user leaves the screen.
        container.appScope.launch {
            val repo = container.geofenceConfigRepository
            // Check again here, not just by disabling the button: the UI isn't a security boundary.
            if (!canEdit(repo.current(), container.securityState.snapshot.value.state)) {
                _uiState.update { it.copy(saving = false, error = "You can only change the office location while inside it.") }
                return@launch
            }
            val saved = repo.save(
                GeofenceConfig(
                    latitude = lat,
                    longitude = lng,
                    radiusMeters = state.radiusMeters,
                    dwellSeconds = state.dwellSeconds,
                    graceSeconds = state.graceSeconds,
                ),
            )
            // New office = old evidence is meaningless. Back to UNKNOWN, then check afresh.
            container.securityState.dispatch(SecurityEvent.ConfigChanged)
            container.geofenceManager.register(saved)
            _uiState.update { it.copy(saving = false, done = true) }
            container.foregroundMonitor.recheckNow()
        }
    }

    /** Escape hatch when the office moves or the phone is handed over: forget everything. */
    fun reset() {
        container.appScope.launch {
            container.geofenceManager.unregister()
            container.geofenceConfigRepository.clear()
            container.vaultRepository.wipeAll()
            container.eventLog.clear()
            container.securityState.dispatch(SecurityEvent.ConfigChanged)
            _uiState.update { it.copy(done = true) }
        }
    }

    /**
     * The office location can only be changed while INSIDE it (or before it's set up).
     * Otherwise whoever holds the phone could move the "office" to where they're standing
     * and unlock the vault. Outside, the only option is Reset, which deletes everything.
     */
    private fun canEdit(config: GeofenceConfig?, state: SecurityState) =
        config == null || state == SecurityState.INSIDE

    private fun Double.format() = String.format(Locale.US, "%.6f", this)
    private fun String.parseCoordinate() = trim().replace(',', '.').toDoubleOrNull()
}
