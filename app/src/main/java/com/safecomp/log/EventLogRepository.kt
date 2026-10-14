package com.safecomp.log

import com.safecomp.data.EventLogDao
import com.safecomp.data.AuthChange
import com.safecomp.data.EventLogEntry
import com.safecomp.data.FocusChange
import com.safecomp.security.SecuritySnapshot
import com.safecomp.security.SecurityStateListener
import kotlinx.coroutines.flow.Flow

/**
 * The local event log: state changes and file-open attempts. Stays on this phone (SafeComp
 * has no internet access) and is pruned to the last 14 days on every write.
 */
class EventLogRepository(
    private val dao: EventLogDao,
    private val isDemoActive: () -> Boolean = { false },
    private val wallClock: () -> Long = System::currentTimeMillis,
) : SecurityStateListener {

    val entries: Flow<List<EventLogEntry>> = dao.observeAll()

    override suspend fun onStateChanged(old: SecuritySnapshot, new: SecuritySnapshot) {
        EventLogPolicy.stateChangeEntry(old, new, wallClock())?.let { record(it) }
    }

    suspend fun fileOpenAttempt(fileId: String, snapshot: SecuritySnapshot, allowed: Boolean) {
        record(EventLogPolicy.fileOpenEntry(fileId, snapshot, allowed, wallClock()))
    }

    suspend fun focusChange(change: FocusChange, snapshot: SecuritySnapshot) {
        record(EventLogPolicy.focusEntry(change, snapshot, wallClock()))
    }

    suspend fun authChange(change: AuthChange, snapshot: SecuritySnapshot) {
        record(EventLogPolicy.authEntry(change, snapshot, wallClock()))
    }

    private suspend fun record(entry: EventLogEntry) {
        dao.insert(entry.copy(simulated = isDemoActive()))
        dao.deleteOlderThan(EventLogPolicy.retentionCutoff(wallClock()))
        dao.trimTo(EventLogEntry.MAX_ENTRIES)
    }

    suspend fun clear() = dao.clear()
}
