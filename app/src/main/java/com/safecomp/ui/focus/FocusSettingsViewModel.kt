package com.safecomp.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safecomp.AppContainer
import com.safecomp.focus.FocusStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkAppRow(val app: LaunchableApp, val selected: Boolean)

data class FocusSettingsUiState(
    val status: FocusStatus = FocusStatus(),
    val rows: List<WorkAppRow> = emptyList(),
    val selectedCount: Int = 0,
    val query: String = "",
    val loadingApps: Boolean = true,
    /** Debug builds only: the adb command for the test-owner demo. */
    val setupCommand: String? = null,
)

class FocusSettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val focus = container.focusController
    private val apps = MutableStateFlow<List<LaunchableApp>?>(null)
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch { apps.value = container.installedApps.launchable() }
    }

    val uiState: StateFlow<FocusSettingsUiState> = combine(
        focus.status,
        focus.workApps,
        apps,
        query,
    ) { status, selected, apps, query ->
        val chosen = selected.toSet()
        val installed = apps.orEmpty()
        FocusSettingsUiState(
            status = status,
            rows = installed
                .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                // Chosen apps first, so the employee sees their list at a glance.
                .sortedByDescending { it.packageName in chosen }
                .map { WorkAppRow(it, it.packageName in chosen) },
            selectedCount = installed.count { it.packageName in chosen },
            query = query,
            loadingApps = apps == null,
            setupCommand = focus.admin.setupCommand,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusSettingsUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setAutoStart(enabled: Boolean) {
        container.appScope.launch { focus.setAutoStart(enabled) }
    }

    fun setWorkApp(packageName: String, selected: Boolean) {
        container.appScope.launch { focus.setWorkApp(packageName, selected) }
    }

    fun removeTestOwner() {
        container.appScope.launch { focus.removeTestOwner() }
    }
}
