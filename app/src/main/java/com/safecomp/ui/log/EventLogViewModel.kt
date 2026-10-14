package com.safecomp.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safecomp.AppContainer
import com.safecomp.data.EventLogEntry
import com.safecomp.data.EventType
import com.safecomp.data.FocusChange
import com.safecomp.security.SecurityState
import com.safecomp.ui.components.description
import com.safecomp.vault.VaultLockedException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LogFilter(val label: String) { ALL("All"), STATE("State"), FILES("Files"), DENIED("Blocked"), FOCUS("Focus") }

enum class LogRowKind { UNLOCKED, LOCKED, OPEN_ALLOWED, OPEN_DENIED, FOCUS }

data class LogRow(
    val id: Long,
    val timestamp: Long,
    val kind: LogRowKind,
    val title: String,
    val detail: String,
)

data class EventLogUiState(
    val rows: List<LogRow> = emptyList(),
    val filter: LogFilter = LogFilter.ALL,
    /** Clearing (and showing file names) needs INSIDE: see [EventLogViewModel.clear]. */
    val unlocked: Boolean = false,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EventLogViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(LogFilter.ALL)

    private val unlocked = container.securityState.snapshot.map { it.isUnlocked }.distinctUntilChanged()

    val uiState: StateFlow<EventLogUiState> = combine(
        container.eventLog.entries,
        container.vaultRepository.files,
        unlocked,
        filter,
    ) { entries, files, unlocked, filter -> Input(entries, files, unlocked, filter) }
        .mapLatest { input ->
            // File names only while INSIDE. Outside, the log shows that something happened,
            // but not which file it was.
            val names = if (input.unlocked) {
                try {
                    container.vaultRepository.decryptNames(input.files)
                } catch (e: VaultLockedException) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
            EventLogUiState(
                rows = input.entries
                    .filter { it.matches(input.filter) }
                    .map { it.toRow(names, input.unlocked) },
                filter = input.filter,
                unlocked = input.unlocked,
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventLogUiState())

    fun setFilter(value: LogFilter) {
        filter.value = value
    }

    /**
     * Only while INSIDE. The log tells the employee what happened while they were away, e.g.
     * blocked attempts to open files. Someone holding the phone outside the office
     * shouldn't be able to erase that.
     */
    fun clear() {
        viewModelScope.launch {
            if (container.securityState.isUnlockedNow()) container.eventLog.clear()
        }
    }

    private data class Input(
        val entries: List<EventLogEntry>,
        val files: List<com.safecomp.data.VaultFile>,
        val unlocked: Boolean,
        val filter: LogFilter,
    )

    private fun EventLogEntry.matches(filter: LogFilter) = when (filter) {
        LogFilter.ALL -> true
        LogFilter.STATE -> type == EventType.STATE_CHANGE
        LogFilter.FILES -> type == EventType.FILE_OPEN
        LogFilter.DENIED -> type == EventType.FILE_OPEN && allowed == false
        LogFilter.FOCUS -> type == EventType.FOCUS
    }

    private fun EventLogEntry.toRow(names: Map<String, String>, unlocked: Boolean): LogRow {
        val why = (if (simulated) "[Demo] " else "") + reason?.description().orEmpty()
        return when (type) {
            EventType.STATE_CHANGE -> LogRow(
                id = id,
                timestamp = timestamp,
                kind = if (state == SecurityState.INSIDE) LogRowKind.UNLOCKED else LogRowKind.LOCKED,
                title = when {
                    state == SecurityState.INSIDE -> "Vault unlocked"
                    previousState == SecurityState.INSIDE -> "Vault locked"
                    // Locked to locked (e.g. OUTSIDE -> UNKNOWN): nothing was unlocked.
                    else -> "Vault still locked"
                },
                detail = "${previousState?.name ?: "?"} → ${state.name}. $why",
            )
            // Focus Mode: only that it started or ended and why. Never which apps were used.
            EventType.FOCUS -> LogRow(
                id = id,
                timestamp = timestamp,
                kind = LogRowKind.FOCUS,
                title = when (focusChange) {
                    FocusChange.STARTED -> "Focus Mode started"
                    FocusChange.STOPPED_BY_USER -> "You stopped Focus Mode"
                    FocusChange.ENDED_LEFT_OFFICE ->
                        if (state == SecurityState.UNKNOWN) "Focus Mode ended: location not confirmed" else "Focus Mode ended: left the office"
                    FocusChange.TURNED_OFF -> "Focus Mode turned off in settings"
                    FocusChange.DECLINED -> "You declined Focus Mode (didn't pin)"
                    FocusChange.NOT_STARTED -> "Focus Mode didn't start (pin request not accepted)"
                    null -> "Focus Mode"
                },
                detail = "State: ${state.name}. $why",
            )
            EventType.FILE_OPEN -> {
                val name = when {
                    !unlocked -> "a locked file"
                    else -> names[fileId]?.let { "\"$it\"" } ?: "a deleted file"
                }
                val ok = allowed == true
                LogRow(
                    id = id,
                    timestamp = timestamp,
                    kind = if (ok) LogRowKind.OPEN_ALLOWED else LogRowKind.OPEN_DENIED,
                    title = if (ok) "Opened $name" else "Blocked an attempt to open $name",
                    detail = "State: ${state.name}. $why",
                )
            }
        }
    }
}
