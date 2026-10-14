package com.safecomp.log

import com.safecomp.data.EventLogEntry
import com.safecomp.data.EventType
import com.safecomp.data.FocusChange
import com.safecomp.security.SecuritySnapshot

/** Pure rules for what goes into the event log, so they can be unit-tested. */
object EventLogPolicy {

    /**
     * Only real state changes (INSIDE / OUTSIDE / UNKNOWN) are logged, not every location
     * fix or reason change. That keeps the log short and stops it from turning into a
     * detailed movement history.
     */
    fun stateChangeEntry(old: SecuritySnapshot, new: SecuritySnapshot, wallClockMillis: Long): EventLogEntry? {
        if (old.state == new.state) return null
        return EventLogEntry(
            timestamp = wallClockMillis,
            type = EventType.STATE_CHANGE,
            state = new.state,
            reason = new.reason,
            previousState = old.state,
        )
    }

    fun fileOpenEntry(fileId: String, snapshot: SecuritySnapshot, allowed: Boolean, wallClockMillis: Long) =
        EventLogEntry(
            timestamp = wallClockMillis,
            type = EventType.FILE_OPEN,
            state = snapshot.state,
            reason = snapshot.reason,
            fileId = fileId,
            allowed = allowed,
        )

    /** Focus Mode starting or ending. Records only that it happened and why, never which
     *  apps were used while it was on: Focus Mode is not a way to watch the employee. */
    fun focusEntry(change: FocusChange, snapshot: SecuritySnapshot, wallClockMillis: Long) =
        EventLogEntry(
            timestamp = wallClockMillis,
            type = EventType.FOCUS,
            state = snapshot.state,
            reason = snapshot.reason,
            focusChange = change,
        )

    fun retentionCutoff(wallClockMillis: Long): Long =
        wallClockMillis - EventLogEntry.RETENTION_DAYS * 24L * 60 * 60 * 1000
}
