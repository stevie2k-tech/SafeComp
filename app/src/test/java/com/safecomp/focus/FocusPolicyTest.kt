package com.safecomp.focus

import com.safecomp.security.SecurityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPolicyTest {

    private fun input(
        state: SecurityState = SecurityState.INSIDE,
        autoStart: Boolean = true,
        foreground: Boolean = true,
        systemActive: Boolean = false,
        now: Long = 1_000,
        request: FocusRequest? = null,
        delivered: Long? = null,
        touched: Long? = null,
    ) = FocusInput(state, autoStart, foreground, systemActive, now, request, delivered, touched)

    private val running = FocusMemory(active = true)

    // --- Starting ---------------------------------------------------------------------------

    @Test fun `inside, enabled and in the foreground starts Focus Mode`() {
        val d = FocusPolicy.decide(FocusMemory(), input())
        assertEquals(FocusAction.Start, d.action)
        assertEquals(1_000L, d.memory.startRequestedAt)
    }

    @Test fun `OUTSIDE and UNKNOWN never start Focus Mode`() {
        for (state in listOf(SecurityState.OUTSIDE, SecurityState.UNKNOWN)) {
            assertNull(FocusPolicy.decide(FocusMemory(), input(state = state)).action)
            assertNull(FocusPolicy.decide(FocusMemory(), input(state = state, request = FocusRequest.USER_START)).action)
        }
    }

    @Test fun `in the background it only offers, once per visit`() {
        val first = FocusPolicy.decide(FocusMemory(), input(foreground = false))
        assertEquals(FocusAction.Offer, first.action)
        assertNull(FocusPolicy.decide(first.memory, input(foreground = false)).action)
    }

    @Test fun `opening the app after an offer starts it`() {
        val offered = FocusPolicy.decide(FocusMemory(), input(foreground = false)).memory
        assertEquals(FocusAction.Start, FocusPolicy.decide(offered, input(foreground = true)).action)
    }

    @Test fun `auto-start off means no automatic start or offer, but a manual start works`() {
        assertNull(FocusPolicy.decide(FocusMemory(), input(autoStart = false)).action)
        assertNull(FocusPolicy.decide(FocusMemory(), input(autoStart = false, foreground = false)).action)
        assertEquals(
            FocusAction.Start,
            FocusPolicy.decide(FocusMemory(), input(autoStart = false, request = FocusRequest.USER_START)).action,
        )
    }

    @Test fun `no second start request while Android is still asking`() {
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0)).memory
        assertNull(FocusPolicy.decide(asked, input(now = 5_000)).action)
    }

    @Test fun `Android confirming the start is reported once`() {
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0)).memory
        val confirmed = FocusPolicy.decide(asked, input(now = 2_000, systemActive = true))
        assertEquals(FocusEvent.Started, confirmed.event)
        assertTrue(confirmed.memory.active)
        assertNull(confirmed.memory.startRequestedAt)
        assertNull(FocusPolicy.decide(confirmed.memory, input(now = 3_000, systemActive = true)).event)
    }

    @Test fun `nothing is judged before the activity has actually asked Android`() {
        // E.g. a cold start: the decision is made before the screen is ready to call startLockTask().
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0)).memory
        val later = FocusPolicy.decide(asked, input(now = 10 * FocusPolicy.START_TIMEOUT_MILLIS))
        assertNull(later.event)
        assertEquals(0L, later.memory.startRequestedAt)
    }

    @Test fun `a slow reader is not treated as a no`() {
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0)).memory
        val reading = FocusPolicy.decide(asked, input(now = FocusPolicy.START_TIMEOUT_MILLIS - 1 + 500, delivered = 500))
        assertNull(reading.event)
        assertFalse(reading.memory.userStoppedThisVisit)
    }

    @Test fun `touching SafeComp again without pinning counts as No thanks`() {
        // The pin dialog covers the whole screen, so a touch on SafeComp means it closed.
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0)).memory
        val touched = FocusPolicy.decide(asked, input(now = 30_000, delivered = 500, touched = 30_000))
        assertNull(touched.event) // a moment's grace, in case "Pin" was pressed just before
        val declined = FocusPolicy.decide(
            touched.memory,
            input(now = 30_000 + FocusPolicy.DECLINE_SETTLE_MILLIS, delivered = 500, touched = 30_000),
        )
        assertEquals(FocusEvent.NotStarted(declined = true), declined.event)
        assertNull(declined.action)
        assertTrue(declined.memory.userStoppedThisVisit)
        assertFalse(declined.memory.startFailed)
    }

    @Test fun `the tap that pressed Start does not count as a no`() {
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0, request = FocusRequest.USER_START)).memory
        val d = FocusPolicy.decide(asked, input(now = 5_000, delivered = 500, touched = 0))
        assertNull(d.event)
    }

    @Test fun `pinning just after a touch is reported as started, not declined`() {
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0)).memory
        val pinned = FocusPolicy.decide(asked, input(now = 30_800, systemActive = true, delivered = 500, touched = 30_000))
        assertEquals(FocusEvent.Started, pinned.event)
        assertTrue(pinned.memory.active)
    }

    @Test fun `no answer within the timeout is 'not started', not the employee's no`() {
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0)).memory
        val timedOut = FocusPolicy.decide(asked, input(now = 500 + FocusPolicy.START_TIMEOUT_MILLIS, delivered = 500))
        assertEquals(FocusEvent.NotStarted(declined = false), timedOut.event)
        assertTrue(timedOut.memory.startFailed)
        // No automatic retry loop...
        assertNull(FocusPolicy.decide(timedOut.memory, input(now = 70_000)).action)
        // ...but Start works, and clears the failure.
        val retry = FocusPolicy.decide(timedOut.memory, input(now = 71_000, request = FocusRequest.USER_START))
        assertEquals(FocusAction.Start, retry.action)
        assertFalse(retry.memory.startFailed)
    }

    @Test fun `accepting the dialog after the timeout still starts Focus Mode`() {
        val failed = FocusMemory(userStoppedThisVisit = true, startFailed = true)
        val late = FocusPolicy.decide(failed, input(now = 90_000, systemActive = true))
        assertEquals(FocusEvent.Started, late.event)
        assertFalse(late.memory.startFailed)
    }

    // --- Ending -----------------------------------------------------------------------------

    @Test fun `OUTSIDE and UNKNOWN always stop it, whatever the other flags say`() {
        for (state in listOf(SecurityState.OUTSIDE, SecurityState.UNKNOWN)) {
            for (autoStart in listOf(true, false)) {
                for (foreground in listOf(true, false)) {
                    val d = FocusPolicy.decide(
                        running,
                        input(state = state, autoStart = autoStart, foreground = foreground, systemActive = true),
                    )
                    assertEquals(FocusAction.Stop(FocusStopReason.LEFT_OFFICE), d.action)
                }
            }
        }
    }

    @Test fun `leaving keeps asking Android to stop until it has`() {
        val first = FocusPolicy.decide(running, input(state = SecurityState.OUTSIDE, systemActive = true))
        val again = FocusPolicy.decide(first.memory, input(state = SecurityState.OUTSIDE, systemActive = true))
        assertEquals(FocusAction.Stop(FocusStopReason.LEFT_OFFICE), again.action)
        val done = FocusPolicy.decide(again.memory, input(state = SecurityState.OUTSIDE, systemActive = false))
        assertEquals(FocusEvent.Ended(FocusStopReason.LEFT_OFFICE), done.event)
        assertNull(done.action)
    }

    @Test fun `the grace period keeps Focus Mode on, because the state is still INSIDE`() {
        // A pending exit doesn't change the public state, so the policy sees INSIDE.
        val d = FocusPolicy.decide(running, input(state = SecurityState.INSIDE, systemActive = true))
        assertNull(d.action)
        assertNull(d.event)
    }

    @Test fun `a running Focus Mode found after the app was away is stopped if no longer inside`() {
        // E.g. the process was killed while pinned and restarted with state UNKNOWN.
        val d = FocusPolicy.decide(FocusMemory(), input(state = SecurityState.UNKNOWN, systemActive = true))
        assertEquals(FocusEvent.Started, d.event)
        assertEquals(FocusAction.Stop(FocusStopReason.LEFT_OFFICE), d.action)
    }

    // --- The employee is always in control ----------------------------------------------------

    @Test fun `unpinning is detected as the employee's choice and isn't overridden`() {
        val unpinned = FocusPolicy.decide(running, input(systemActive = false))
        assertEquals(FocusEvent.Ended(FocusStopReason.USER), unpinned.event)
        assertNull(unpinned.action)
        // Still inside, still in the foreground, auto-start still on: it does NOT restart.
        assertNull(FocusPolicy.decide(unpinned.memory, input(now = 60_000)).action)
    }

    @Test fun `the Stop button stops it and blocks automatic restarts`() {
        val stopped = FocusPolicy.decide(running, input(systemActive = true, request = FocusRequest.USER_STOP))
        assertEquals(FocusAction.Stop(FocusStopReason.USER), stopped.action)
        val ended = FocusPolicy.decide(stopped.memory, input(systemActive = false))
        assertEquals(FocusEvent.Ended(FocusStopReason.USER), ended.event)
        assertNull(FocusPolicy.decide(ended.memory, input()).action)
    }

    @Test fun `the employee can change their mind and start it again`() {
        val stoppedMemory = FocusMemory(userStoppedThisVisit = true)
        val d = FocusPolicy.decide(stoppedMemory, input(request = FocusRequest.USER_START))
        assertEquals(FocusAction.Start, d.action)
        assertFalse(d.memory.userStoppedThisVisit)
    }

    @Test fun `switching auto-start off while running stops it`() {
        val d = FocusPolicy.decide(running, input(systemActive = true, request = FocusRequest.TURNED_OFF))
        assertEquals(FocusAction.Stop(FocusStopReason.TURNED_OFF), d.action)
    }

    @Test fun `a new office visit starts fresh`() {
        val stoppedMemory = FocusMemory(userStoppedThisVisit = true, offeredThisVisit = true)
        val left = FocusPolicy.decide(stoppedMemory, input(state = SecurityState.OUTSIDE)).memory
        assertFalse(left.userStoppedThisVisit)
        assertFalse(left.offeredThisVisit)
        assertEquals(FocusAction.Start, FocusPolicy.decide(left, input()).action)
    }

    @Test fun `UNKNOWN does not end the visit, so the employee isn't asked again`() {
        // E.g. location switched off for a moment, a weak signal, or the re-check after
        // Android stopped SafeComp in the background.
        val stopped = FocusMemory(userStoppedThisVisit = true, offeredThisVisit = true)
        val blip = FocusPolicy.decide(stopped, input(state = SecurityState.UNKNOWN)).memory
        assertTrue(blip.userStoppedThisVisit)
        assertTrue(blip.offeredThisVisit)
        assertNull(FocusPolicy.decide(blip, input(state = SecurityState.INSIDE)).action)
        assertNull(FocusPolicy.decide(blip, input(state = SecurityState.INSIDE, foreground = false)).action)
    }

    @Test fun `UNKNOWN still cancels a pending start`() {
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0)).memory
        val unknown = FocusPolicy.decide(asked, input(state = SecurityState.UNKNOWN, now = 1_000)).memory
        assertNull(unknown.startRequestedAt)
    }

    @Test fun `Focus Mode ended by UNKNOWN comes back once the phone is confirmed inside again`() {
        // The employee didn't choose to stop, so it isn't remembered as a "no".
        val running = FocusMemory(active = true, stopReason = FocusStopReason.LEFT_OFFICE)
        val ended = FocusPolicy.decide(running, input(state = SecurityState.UNKNOWN, systemActive = false))
        assertEquals(FocusEvent.Ended(FocusStopReason.LEFT_OFFICE), ended.event)
        assertFalse(ended.memory.userStoppedThisVisit)
        assertEquals(FocusAction.Start, FocusPolicy.decide(ended.memory, input(state = SecurityState.INSIDE)).action)
    }

    @Test fun `leaving the office during the pin dialog cancels the pending start`() {
        val asked = FocusPolicy.decide(FocusMemory(), input(now = 0)).memory
        val left = FocusPolicy.decide(asked, input(state = SecurityState.OUTSIDE, now = 1_000)).memory
        assertNull(left.startRequestedAt)
        // If the employee then accepts the (stale) dialog anyway, it's stopped straight away.
        val accepted = FocusPolicy.decide(left, input(state = SecurityState.OUTSIDE, systemActive = true, now = 2_000))
        assertEquals(FocusAction.Stop(FocusStopReason.LEFT_OFFICE), accepted.action)
    }

    // --- Allow-list ---------------------------------------------------------------------------

    @Test fun `the allow-list always contains SafeComp, first and only once`() {
        assertEquals(listOf("com.safecomp"), FocusPolicy.allowList("com.safecomp", emptyList()))
        assertEquals(
            listOf("com.safecomp", "com.a.mail", "com.b.docs"),
            FocusPolicy.allowList("com.safecomp", listOf("com.b.docs", "com.safecomp", "com.a.mail", "com.a.mail", "")),
        )
    }
}
