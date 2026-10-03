package com.safecomp.security

import com.safecomp.security.SecurityEvent.AppForegrounded
import com.safecomp.security.SecurityEvent.ConfigChanged
import com.safecomp.security.SecurityEvent.FixUnavailable
import com.safecomp.security.SecurityEvent.GeofenceDwelled
import com.safecomp.security.SecurityEvent.GeofenceEntered
import com.safecomp.security.SecurityEvent.GeofenceExited
import com.safecomp.security.SecurityEvent.LocationFix
import com.safecomp.security.SecurityEvent.LocationServicesOff
import com.safecomp.security.SecurityEvent.MockLocationDetected
import com.safecomp.security.SecurityEvent.PermissionMissing
import com.safecomp.security.SecurityEvent.Tick
import com.safecomp.security.SecurityState.INSIDE
import com.safecomp.security.SecurityState.OUTSIDE
import com.safecomp.security.SecurityState.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityStateMachineTest {

    private val params = GeofenceParams(radiusMeters = 150f, dwellMillis = 30_000, graceMillis = 60_000)

    private val insideFix = LocationFix(distanceMeters = 20f, accuracyMeters = 15f, isMock = false)
    private val outsideFix = LocationFix(distanceMeters = 900f, accuracyMeters = 15f, isMock = false)
    private val mockInsideFix = insideFix.copy(isMock = true)
    private val vagueFix = LocationFix(distanceMeters = 20f, accuracyMeters = 400f, isMock = false)

    /** A tiny driver so tests read as a timeline: at(t) { event }. */
    private inner class Timeline(
        var snapshot: SecuritySnapshot = SecuritySnapshot(),
        val allowMock: Boolean = false,
        val p: GeofenceParams? = params,
    ) {
        fun at(time: Long, event: SecurityEvent): SecuritySnapshot {
            snapshot = SecurityStateMachine.reduce(snapshot, event, time, p, allowMock)
            return snapshot
        }
    }

    private fun confirmedInsideAt(time: Long) = Timeline().apply { at(time, GeofenceDwelled) }

    // --- Starting state and fail-closed ---

    @Test fun `starts UNKNOWN and locked`() {
        val s = SecuritySnapshot()
        assertEquals(UNKNOWN, s.state)
        assertFalse(s.isUnlocked)
    }

    @Test fun `no config means UNKNOWN even with an inside fix`() {
        val t = Timeline(p = null)
        assertEquals(UNKNOWN, t.at(0, insideFix).state)
        assertEquals(StateReason.NOT_CONFIGURED, t.snapshot.reason)
    }

    @Test fun `permission revoked while INSIDE locks immediately, no grace`() {
        val t = confirmedInsideAt(0)
        val s = t.at(1_000, PermissionMissing)
        assertEquals(UNKNOWN, s.state)
        assertEquals(StateReason.PERMISSION_MISSING, s.reason)
    }

    @Test fun `location services off while INSIDE locks immediately`() {
        val t = confirmedInsideAt(0)
        assertEquals(UNKNOWN, t.at(1_000, LocationServicesOff).state)
    }

    @Test fun `config change throws away old evidence`() {
        val t = confirmedInsideAt(0)
        assertEquals(UNKNOWN, t.at(1_000, ConfigChanged).state)
    }

    // --- Mock locations ---

    @Test fun `mock fix while INSIDE locks immediately`() {
        val t = confirmedInsideAt(0)
        val s = t.at(1_000, mockInsideFix)
        assertEquals(UNKNOWN, s.state)
        assertEquals(StateReason.MOCK_LOCATION, s.reason)
    }

    @Test fun `mock geofence trigger locks immediately`() {
        val t = confirmedInsideAt(0)
        assertEquals(UNKNOWN, t.at(1_000, MockLocationDetected).state)
    }

    @Test fun `mock fixes can never build up dwell time`() {
        val t = Timeline()
        t.at(0, mockInsideFix)
        assertEquals(UNKNOWN, t.at(60_000, mockInsideFix).state)
    }

    @Test fun `mock fixes are accepted only when explicitly allowed (debug demo mode)`() {
        val t = Timeline(allowMock = true)
        t.at(0, mockInsideFix)
        assertEquals(INSIDE, t.at(30_000, mockInsideFix).state)
    }

    // --- Entering: dwell ---

    @Test fun `a single inside fix does not unlock`() {
        val s = Timeline().at(0, insideFix)
        assertEquals(UNKNOWN, s.state)
        assertEquals(StateReason.CONFIRMING_INSIDE, s.reason)
    }

    @Test fun `inside fixes spanning the dwell time unlock`() {
        val t = Timeline()
        t.at(0, insideFix)
        assertEquals(UNKNOWN, t.at(29_999, insideFix).state)
        assertEquals(INSIDE, t.at(30_000, insideFix).state)
    }

    @Test fun `an outside fix while confirming resets the dwell`() {
        val t2 = Timeline()
        t2.at(0, insideFix)
        t2.at(20_000, outsideFix)
        t2.at(25_000, insideFix)
        assertFalse(t2.at(40_000, insideFix).isUnlocked) // only 15 s since evidence restarted
        assertTrue(t2.at(55_000, insideFix).isUnlocked)
    }

    @Test fun `geofence ENTER alone does not unlock, DWELL does`() {
        val t = Timeline()
        assertFalse(t.at(0, GeofenceEntered).isUnlocked)
        assertTrue(t.at(30_000, GeofenceDwelled).isUnlocked)
    }

    @Test fun `a vague fix is not inside evidence`() {
        val t = Timeline()
        t.at(0, vagueFix)
        assertFalse(t.at(60_000, vagueFix).isUnlocked)
    }

    // --- Leaving: grace period ---

    @Test fun `outside fix while INSIDE starts grace but stays unlocked`() {
        val t = confirmedInsideAt(0)
        val s = t.at(10_000, outsideFix)
        assertEquals(INSIDE, s.state)
        assertNotNull(s.pendingExit)
    }

    @Test fun `grace expires into OUTSIDE on a tick`() {
        val t = confirmedInsideAt(0)
        t.at(10_000, outsideFix)
        assertEquals(INSIDE, t.at(69_999, Tick).state)
        assertEquals(OUTSIDE, t.at(70_000, Tick).state)
    }

    @Test fun `repeated outside fixes do not extend the grace period`() {
        val t = confirmedInsideAt(0)
        t.at(10_000, outsideFix)
        t.at(40_000, outsideFix)
        assertEquals(OUTSIDE, t.at(70_000, outsideFix).state)
    }

    @Test fun `coming back inside during grace cancels the lock`() {
        val t = confirmedInsideAt(0)
        t.at(10_000, outsideFix)
        val back = t.at(40_000, insideFix)
        assertEquals(INSIDE, back.state)
        assertNull(back.pendingExit)
        assertEquals(INSIDE, t.at(200_000, Tick).state)
    }

    @Test fun `geofence EXIT also gets the grace period`() {
        val t = confirmedInsideAt(0)
        assertEquals(INSIDE, t.at(1_000, GeofenceExited).state)
        assertEquals(OUTSIDE, t.at(61_000, Tick).state)
    }

    @Test fun `poor accuracy while INSIDE uses grace, then becomes UNKNOWN`() {
        val t = confirmedInsideAt(0)
        assertEquals(INSIDE, t.at(1_000, vagueFix).state)
        val s = t.at(61_000, Tick)
        assertEquals(UNKNOWN, s.state)
        assertEquals(StateReason.POOR_ACCURACY, s.reason)
    }

    @Test fun `no fix while INSIDE uses grace, then becomes UNKNOWN`() {
        val t = confirmedInsideAt(0)
        t.at(1_000, FixUnavailable)
        assertEquals(UNKNOWN, t.at(61_000, Tick).state)
    }

    @Test fun `outside evidence during an uncertain grace makes the result OUTSIDE`() {
        val t = confirmedInsideAt(0)
        t.at(1_000, FixUnavailable)
        t.at(5_000, outsideFix)
        assertEquals(OUTSIDE, t.at(61_000, Tick).state)
    }

    @Test fun `any event after the grace deadline sees the lock first`() {
        val t = confirmedInsideAt(0)
        t.at(1_000, outsideFix)
        // No ticks ran (e.g. Doze). The next event still applies the expired grace first,
        // so a late inside fix can't "rescue" an already-expired grace period.
        assertEquals(OUTSIDE, t.at(120_000, insideFix).state)
    }

    // --- Clock behaviour ---

    @Test fun `clock going backwards during grace locks (fail closed)`() {
        val t = confirmedInsideAt(100_000)
        t.at(110_000, outsideFix)
        assertEquals(OUTSIDE, t.at(50_000, Tick).state)
    }

    // --- Freshness when returning to the foreground ---

    @Test fun `fresh INSIDE survives returning to the foreground`() {
        val t = confirmedInsideAt(0)
        assertEquals(INSIDE, t.at(60_000, AppForegrounded).state)
    }

    @Test fun `stale INSIDE drops to UNKNOWN on foreground until a fix confirms it`() {
        val t = confirmedInsideAt(0)
        val s = t.at(SecurityStateMachine.FRESHNESS_MILLIS + 1, AppForegrounded)
        assertEquals(UNKNOWN, s.state)
        // We knew we were inside, so a fresh inside fix restores it without a new dwell.
        assertEquals(INSIDE, t.at(SecurityStateMachine.FRESHNESS_MILLIS + 5_000, insideFix).state)
    }

    @Test fun `stale INSIDE followed by an outside fix is OUTSIDE`() {
        val t = confirmedInsideAt(0)
        t.at(SecurityStateMachine.FRESHNESS_MILLIS + 1, AppForegrounded)
        assertEquals(OUTSIDE, t.at(SecurityStateMachine.FRESHNESS_MILLIS + 5_000, outsideFix).state)
    }

    @Test fun `regular inside fixes keep INSIDE fresh`() {
        val t = confirmedInsideAt(0)
        t.at(100_000, insideFix)
        assertEquals(INSIDE, t.at(200_000, AppForegrounded).state)
    }
}
