package com.safecomp.security

import com.safecomp.security.SecurityState.INSIDE
import com.safecomp.security.SecurityState.OUTSIDE
import com.safecomp.security.SecurityState.UNKNOWN
import com.safecomp.security.SecurityStateMachine.FRESHNESS_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoreAndAnnouncementTest {

    private fun inside(confirmedAt: Long, pending: PendingExit? = null) = SecuritySnapshot(
        state = INSIDE,
        reason = StateReason.INSIDE_CONFIRMED,
        lastConfirmedAt = confirmedAt,
        pendingExit = pending,
    )

    // --- Restoring saved state after the process was killed ---

    @Test fun `fresh INSIDE from the same boot is restored`() {
        val restored = SecurityStateMachine.restore(inside(100_000), savedBoot = 7, currentBoot = 7, nowMillis = 130_000)
        assertEquals(INSIDE, restored.state)
    }

    @Test fun `stale INSIDE is not trusted after a restart`() {
        val restored = SecurityStateMachine.restore(
            inside(100_000), savedBoot = 7, currentBoot = 7, nowMillis = 100_000 + FRESHNESS_MILLIS + 1,
        )
        assertEquals(UNKNOWN, restored.state)
    }

    @Test fun `anything saved before a reboot is thrown away`() {
        // After a reboot the uptime clock restarts, so a saved time can look "recent" when it isn't.
        val restored = SecurityStateMachine.restore(inside(100_000), savedBoot = 7, currentBoot = 8, nowMillis = 110_000)
        assertEquals(UNKNOWN, restored.state)
        assertEquals(StateReason.CHECKING, restored.reason)
    }

    @Test fun `unknown boot count means nothing is trusted`() {
        val restored = SecurityStateMachine.restore(inside(100_000), savedBoot = -1, currentBoot = -1, nowMillis = 110_000)
        assertEquals(UNKNOWN, restored.state)
    }

    @Test fun `a running grace period survives a restart and still expires`() {
        val saved = inside(100_000, PendingExit(105_000, OUTSIDE, StateReason.OUTSIDE_GEOFENCE))
        val restored = SecurityStateMachine.restore(saved, 7, 7, nowMillis = 120_000)
        assertEquals(INSIDE, restored.state)
        val params = GeofenceParams(radiusMeters = 150f, dwellMillis = 30_000, graceMillis = 60_000)
        // The background job's Tick, after the grace period: locked.
        assertEquals(OUTSIDE, SecurityStateMachine.reduce(restored, SecurityEvent.Tick, 165_000, params).state)
    }

    @Test fun `a grace period that started in the future (clock anomaly) locks on restore`() {
        val saved = inside(100_000, PendingExit(200_000, OUTSIDE, StateReason.OUTSIDE_GEOFENCE))
        assertEquals(OUTSIDE, SecurityStateMachine.restore(saved, 7, 7, nowMillis = 150_000).state)
    }

    @Test fun `half-finished dwell evidence is dropped on restore`() {
        val saved = SecuritySnapshot(state = UNKNOWN, reason = StateReason.CONFIRMING_INSIDE, enterEvidenceSince = 100_000)
        assertNull(SecurityStateMachine.restore(saved, 7, 7, nowMillis = 110_000).enterEvidenceSince)
    }

    // --- When to notify ---

    private val unlocked = inside(0)
    private val outside = SecuritySnapshot(state = OUTSIDE, reason = StateReason.OUTSIDE_FIX)
    private val checking = SecuritySnapshot(state = UNKNOWN, reason = StateReason.CHECKING)
    private val mock = SecuritySnapshot(state = UNKNOWN, reason = StateReason.MOCK_LOCATION)

    @Test fun `first unlock is announced`() {
        assertEquals(Announcement.UNLOCKED, Announcements.decide(null, unlocked))
        assertEquals(Announcement.UNLOCKED, Announcements.decide(false, unlocked))
    }

    @Test fun `staying unlocked is not announced again`() {
        assertNull(Announcements.decide(true, unlocked))
    }

    @Test fun `leaving after an unlock is announced`() {
        assertEquals(Announcement.LOCKED, Announcements.decide(true, outside))
        assertEquals(Announcement.LOCKED, Announcements.decide(true, mock))
    }

    @Test fun `no locked notification if the user was never told it was unlocked`() {
        assertNull(Announcements.decide(null, outside))
        assertNull(Announcements.decide(false, outside))
    }

    @Test fun `passing states are not announced (no lock-unlock flicker on app start)`() {
        assertNull(Announcements.decide(true, checking))
        assertNull(Announcements.decide(true, checking.copy(reason = StateReason.CONFIRMING_INSIDE)))
    }
}
