package com.safecomp.ui.permissions

import androidx.lifecycle.ViewModel
import com.safecomp.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The permissions are requested one at a time, in this order. Android requires background
 *  location to be requested separately, and only after foreground location is granted.
 *  BATTERY isn't a permission: it asks Android not to hold back SafeComp's background work. */
enum class PermissionStep { PRECISE_LOCATION, BACKGROUND_LOCATION, NOTIFICATIONS, BATTERY }

data class OnboardingUiState(
    val status: PermissionStatus,
    /** The step being shown, or null when onboarding is finished. */
    val step: PermissionStep?,
    /** Steps where Android will no longer show the dialog, so the user must use Settings. */
    val blockedInSettings: Set<PermissionStep> = emptySet(),
)

class PermissionsViewModel(
    private val checker: PermissionChecker,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        checker.current().let { status ->
            OnboardingUiState(status = status, step = nextUnsatisfied(status, after = null))
        },
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        if (_uiState.value.step == null) preferences.onboardingCompleted = true
    }

    /** Called on every resume, e.g. when the user comes back from system settings. */
    fun refresh() {
        val status = checker.current()
        _uiState.update { it.copy(status = status) }
        val step = _uiState.value.step
        if (step != null && status.satisfies(step)) advance()
    }

    /**
     * Result of a system permission dialog. [canAskAgain] is false when Android will no
     * longer show the dialog (the user denied it twice, or chose "Don't ask again").
     */
    fun onPermissionResult(step: PermissionStep, canAskAgain: Boolean) {
        refresh()
        if (!_uiState.value.status.satisfies(step) && !canAskAgain) {
            _uiState.update { it.copy(blockedInSettings = it.blockedInSettings + step) }
        }
    }

    /** "Not now": the app still works with fewer permissions, it just fails closed more often. */
    fun skip() = advance()

    private fun advance() {
        val state = _uiState.value
        val next = nextUnsatisfied(state.status, after = state.step)
        _uiState.update { it.copy(step = next) }
        if (next == null) preferences.onboardingCompleted = true
    }

    private fun nextUnsatisfied(status: PermissionStatus, after: PermissionStep?): PermissionStep? {
        val start = after?.let { it.ordinal + 1 } ?: 0
        return PermissionStep.entries.drop(start).firstOrNull { !status.satisfies(it) }
    }
}

fun PermissionStatus.satisfies(step: PermissionStep): Boolean = when (step) {
    PermissionStep.PRECISE_LOCATION -> preciseLocation
    PermissionStep.BACKGROUND_LOCATION -> backgroundLocation
    PermissionStep.NOTIFICATIONS -> notifications
    // Without "Allow all the time" there is no background work to protect, so skip it.
    PermissionStep.BATTERY -> batteryUnrestricted || !backgroundLocation
}
