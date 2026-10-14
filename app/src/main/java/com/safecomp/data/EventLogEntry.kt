package com.safecomp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.safecomp.security.SecurityState
import com.safecomp.security.StateReason

enum class EventType { STATE_CHANGE, FILE_OPEN, FOCUS, AUTH }

/** AUTH entries only: who-you-are events. Never how, where, or which finger. */
enum class AuthChange { AUTHENTICATED, PIN_REQUIRED, KEYS_DESTROYED, KEYS_UPGRADED }

/** FOCUS entries only: what happened to Focus Mode. Never which apps were used. */
enum class FocusChange { STARTED, STOPPED_BY_USER, ENDED_LEFT_OFFICE, TURNED_OFF, DECLINED, NOT_STARTED }

/**
 * One line of the local event log.
 *
 * Deliberately minimal: no coordinates, no file names (only the file's random ID, whose name
 * is decrypted for display only while INSIDE), never uploaded, and deleted after
 * [EventLogEntry.RETENTION_DAYS]. It's for the employee's own information: "did anything
 * happen while I was away?", not for monitoring the employee.
 */
@Entity(tableName = "event_log", indices = [Index("timestamp")])
data class EventLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Wall-clock time, for display only. */
    val timestamp: Long,
    val type: EventType,
    /** The security state when the event happened (for STATE_CHANGE: the new state). */
    val state: SecurityState,
    val reason: StateReason? = null,
    /** STATE_CHANGE only: the state before. */
    val previousState: SecurityState? = null,
    /** FILE_OPEN only. */
    val fileId: String? = null,
    /** FILE_OPEN only: whether decryption was allowed. */
    val allowed: Boolean? = null,
    /** FOCUS only. */
    val focusChange: FocusChange? = null,
    /** AUTH only. */
    val authChange: AuthChange? = null,
    /** Recorded while debug demo mode was on, so demo events are never mistaken for real ones. */
    @ColumnInfo(defaultValue = "0") val simulated: Boolean = false,
) {
    companion object {
        const val RETENTION_DAYS = 14
        const val MAX_ENTRIES = 2_000
    }
}
