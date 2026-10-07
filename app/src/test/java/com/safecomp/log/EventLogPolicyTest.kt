package com.safecomp.log

import com.safecomp.data.EventLogEntry
import com.safecomp.data.EventType
import com.safecomp.security.SecuritySnapshot
import com.safecomp.security.SecurityState
import com.safecomp.security.StateReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventLogPolicyTest {

    private val inside = SecuritySnapshot(state = SecurityState.INSIDE, reason = StateReason.INSIDE_CONFIRMED, lastConfirmedAt = 0)
    private val outside = SecuritySnapshot(state = SecurityState.OUTSIDE, reason = StateReason.OUTSIDE_FIX)

    @Test fun `a real state change is logged with before and after`() {
        val entry = EventLogPolicy.stateChangeEntry(inside, outside, wallClockMillis = 1_000)!!
        assertEquals(EventType.STATE_CHANGE, entry.type)
        assertEquals(SecurityState.INSIDE, entry.previousState)
        assertEquals(SecurityState.OUTSIDE, entry.state)
        assertEquals(StateReason.OUTSIDE_FIX, entry.reason)
    }

    @Test fun `location fixes that don't change the state are not logged`() {
        // Every fix updates lastConfirmedAt. Logging those would build a movement history.
        assertNull(EventLogPolicy.stateChangeEntry(inside, inside.copy(lastConfirmedAt = 10_000), 1_000))
    }

    @Test fun `a grace period starting is not a state change`() {
        val leaving = inside.copy(pendingExit = com.safecomp.security.PendingExit(5, SecurityState.OUTSIDE, StateReason.OUTSIDE_FIX))
        assertNull(EventLogPolicy.stateChangeEntry(inside, leaving, 1_000))
    }

    @Test fun `reason-only changes are not logged`() {
        val unknownA = SecuritySnapshot(state = SecurityState.UNKNOWN, reason = StateReason.CHECKING)
        val unknownB = unknownA.copy(reason = StateReason.NO_FIX)
        assertNull(EventLogPolicy.stateChangeEntry(unknownA, unknownB, 1_000))
    }

    @Test fun `a denied file open records the state at that moment`() {
        val entry = EventLogPolicy.fileOpenEntry("file-1", outside, allowed = false, wallClockMillis = 2_000)
        assertEquals(EventType.FILE_OPEN, entry.type)
        assertEquals("file-1", entry.fileId)
        assertEquals(false, entry.allowed)
        assertEquals(SecurityState.OUTSIDE, entry.state)
    }

    @Test fun `entries older than the retention period are cut off`() {
        val now = 100L * 24 * 60 * 60 * 1000
        val cutoff = EventLogPolicy.retentionCutoff(now)
        assertEquals(EventLogEntry.RETENTION_DAYS * 24L * 60 * 60 * 1000, now - cutoff)
    }

    @Test fun `log entries never carry location data`() {
        // A guard against someone adding coordinates to the log later.
        val fields = EventLogEntry::class.java.declaredFields.map { it.name.lowercase() }
        listOf("lat", "lng", "lon", "latitude", "longitude", "coordinate", "distance", "accuracy").forEach { banned ->
            assert(fields.none { it.contains(banned) }) { "EventLogEntry must not store '$banned'" }
        }
    }
}
