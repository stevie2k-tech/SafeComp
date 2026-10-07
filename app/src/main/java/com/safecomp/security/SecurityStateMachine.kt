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

/**
 * The security state machine, as one pure function: (old snapshot, event, time) -> new snapshot.
 *
 * It has no Android code, no I/O and no hidden clock, so every rule is unit-tested.
 * `nowMillis` comes from a MONOTONIC clock (SystemClock.elapsedRealtime on the device). If it
 * came from the wall clock, changing the time in Settings could cut the grace period short
 * or stretch it out.
 *
 * Rules, in short:
 *  - Definite loss of trust (mock location, permission revoked, location off, not configured)
 *    -> UNKNOWN immediately, no grace.
 *  - GPS noise (poor accuracy, no fix) and outside evidence while INSIDE -> grace period first,
 *    so the vault doesn't flicker at the boundary.
 *  - Entering INSIDE needs continuous inside evidence for the dwell time.
 */
object SecurityStateMachine {

    /** INSIDE older than this is re-checked when the app comes to the foreground. */
    const val FRESHNESS_MILLIS = 2 * 60 * 1000L

    fun reduce(
        current: SecuritySnapshot,
        event: SecurityEvent,
        nowMillis: Long,
        params: GeofenceParams?,
        allowMockLocations: Boolean = false,
    ): SecuritySnapshot {
        if (params == null) return locked(SecurityState.UNKNOWN, StateReason.NOT_CONFIGURED)

        // Time is applied first: an expired grace period takes effect before anything else.
        val s = expireGrace(current, nowMillis, params)

        return when (event) {
            Tick -> s
            PermissionMissing -> locked(SecurityState.UNKNOWN, StateReason.PERMISSION_MISSING)
            LocationServicesOff -> locked(SecurityState.UNKNOWN, StateReason.LOCATION_OFF)
            ConfigChanged -> locked(SecurityState.UNKNOWN, StateReason.CHECKING)
            MockLocationDetected ->
                if (allowMockLocations) s else locked(SecurityState.UNKNOWN, StateReason.MOCK_LOCATION)
            FixUnavailable -> uncertain(s, nowMillis, StateReason.NO_FIX)
            GeofenceDwelled -> confirmedInside(nowMillis)
            GeofenceEntered -> insideEvidence(s, nowMillis, params)
            GeofenceExited -> outsideEvidence(s, nowMillis, StateReason.OUTSIDE_GEOFENCE)
            AppForegrounded -> revalidateIfStale(s, nowMillis)
            is LocationFix -> when {
                event.isMock && !allowMockLocations ->
                    locked(SecurityState.UNKNOWN, StateReason.MOCK_LOCATION)
                // The fix is so vague it could be either side of the boundary.
                event.accuracyMeters > params.radiusMeters ->
                    uncertain(s, nowMillis, StateReason.POOR_ACCURACY)
                event.distanceMeters <= params.radiusMeters -> insideEvidence(s, nowMillis, params)
                else -> outsideEvidence(s, nowMillis, StateReason.OUTSIDE_FIX)
            }
        }
    }

    /**
     * Rebuilds the state saved before the process was killed. Applied ONCE, at load time, so a
     * restored snapshot can never be more trusted than a live one:
     *  - a different boot -> start from UNKNOWN (elapsedRealtime restarts at 0 on every boot)
     *  - a stale INSIDE -> UNKNOWN until a fresh fix confirms it (same rule as AppForegrounded)
     *  - an expired or impossible grace period -> its lock takes effect
     *  - half-finished dwell evidence is dropped: dwell needs CONTINUOUS evidence
     */
    fun restore(saved: SecuritySnapshot, savedBoot: Int, currentBoot: Int, nowMillis: Long): SecuritySnapshot {
        if (savedBoot != currentBoot || savedBoot < 0) return SecuritySnapshot()
        val pending = saved.pendingExit
        if (saved.state == SecurityState.INSIDE && pending != null && nowMillis < pending.sinceMillis) {
            return locked(pending.target, pending.reason)
        }
        val revalidated = revalidateIfStale(saved, nowMillis)
        return if (revalidated.state == SecurityState.INSIDE) {
            revalidated
        } else {
            revalidated.copy(enterEvidenceSince = null, pendingExit = null)
        }
    }

    private fun locked(state: SecurityState, reason: StateReason) =
        SecuritySnapshot(state = state, reason = reason)

    private fun confirmedInside(now: Long) = SecuritySnapshot(
        state = SecurityState.INSIDE,
        reason = StateReason.INSIDE_CONFIRMED,
        lastConfirmedAt = now,
    )

    private fun expireGrace(s: SecuritySnapshot, now: Long, params: GeofenceParams): SecuritySnapshot {
        val pending = s.pendingExit ?: return s
        if (s.state != SecurityState.INSIDE) return s.copy(pendingExit = null)
        val elapsed = now - pending.sinceMillis
        // elapsed < 0 means the clock went backwards. That shouldn't happen with a monotonic
        // clock, so treat it as suspicious and lock (fail closed).
        return if (elapsed >= params.graceMillis || elapsed < 0) {
            locked(pending.target, pending.reason)
        } else {
            s
        }
    }

    private fun insideEvidence(s: SecuritySnapshot, now: Long, params: GeofenceParams): SecuritySnapshot {
        if (s.state == SecurityState.INSIDE) {
            // Back inside during the grace period: cancel the pending lock.
            return s.copy(reason = StateReason.INSIDE_CONFIRMED, pendingExit = null, lastConfirmedAt = now)
        }
        if (s.resumeInsideOnConfirm) return confirmedInside(now)

        val since = s.enterEvidenceSince?.takeIf { it <= now } ?: now
        return if (now - since >= params.dwellMillis) {
            confirmedInside(now)
        } else {
            s.copy(reason = StateReason.CONFIRMING_INSIDE, enterEvidenceSince = since)
        }
    }

    private fun outsideEvidence(s: SecuritySnapshot, now: Long, reason: StateReason): SecuritySnapshot {
        if (s.state == SecurityState.INSIDE) {
            // Keep the original start time so repeated outside fixes don't extend the grace.
            val pending = s.pendingExit?.copy(target = SecurityState.OUTSIDE, reason = reason)
                ?: PendingExit(sinceMillis = now, target = SecurityState.OUTSIDE, reason = reason)
            return s.copy(pendingExit = pending)
        }
        return locked(SecurityState.OUTSIDE, reason)
    }

    /** Poor accuracy or no fix: the phone may still be in the office, we just can't tell. */
    private fun uncertain(s: SecuritySnapshot, now: Long, reason: StateReason): SecuritySnapshot {
        if (s.state == SecurityState.INSIDE) {
            // Outside evidence wins over uncertainty if both are pending.
            val pending = s.pendingExit
                ?: PendingExit(sinceMillis = now, target = SecurityState.UNKNOWN, reason = reason)
            return s.copy(pendingExit = pending)
        }
        return locked(SecurityState.UNKNOWN, reason).copy(resumeInsideOnConfirm = s.resumeInsideOnConfirm)
    }

    private fun revalidateIfStale(s: SecuritySnapshot, now: Long): SecuritySnapshot {
        if (s.state != SecurityState.INSIDE) return s
        val confirmedAt = s.lastConfirmedAt
        val stale = confirmedAt == null || now - confirmedAt > FRESHNESS_MILLIS || now < confirmedAt
        return if (stale) {
            SecuritySnapshot(
                state = SecurityState.UNKNOWN,
                reason = StateReason.CHECKING,
                resumeInsideOnConfirm = true,
            )
        } else {
            s
        }
    }
}
