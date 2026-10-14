package com.safecomp.focus

import com.safecomp.security.SecurityState

/** How Focus Mode can restrict this phone. Detected at runtime by [FocusCapabilities]. */
enum class FocusMode {
    /** Normal, un-enrolled phone: Android's screen pinning. Only SafeComp itself can be used,
     *  Android asks the user first, and the user can unpin at any time. */
    PINNING,

    /** A company's Device Policy Controller allow-listed SafeComp for Lock Task. The apps
     *  allowed are the admin's list, not the employee's. */
    MANAGED,

    /** Debug builds only: SafeComp was made a test device owner with adb, so it can apply
     *  the employee's own work-app allow-list. Used to demonstrate the enterprise version. */
    TEST_OWNER,
}

enum class FocusStopReason {
    /** The employee stopped it: Stop button, notification action, or Android's unpin gesture. */
    USER,

    /** The state left INSIDE: OUTSIDE (left the office) or UNKNOWN (SafeComp can no longer
     *  prove the phone is inside, e.g. location off). Both end Focus Mode, like the vault. */
    LEFT_OFFICE,

    /** The employee switched automatic Focus Mode off in settings while it was running. */
    TURNED_OFF,
}

/** Something the employee asked for directly. */
enum class FocusRequest { USER_START, USER_STOP, TURNED_OFF }

/** What the controller must do next. */
sealed interface FocusAction {
    data object Start : FocusAction
    data class Stop(val reason: FocusStopReason) : FocusAction

    /** INSIDE, but SafeComp is in the background, where Android doesn't let an app start
     *  Lock Task. Post a notification offering it; never launch ourselves. */
    data object Offer : FocusAction
}

/** Something that happened, for the event log and notifications. */
sealed interface FocusEvent {
    data object Started : FocusEvent
    data class Ended(val reason: FocusStopReason) : FocusEvent

    /** A start was requested but Android never entered Lock Task. [declined]: the employee
     *  went back to using SafeComp without pinning, so they closed the "Pin app?" dialog.
     *  Otherwise nothing was accepted within the timeout. Either way there's no automatic
     *  retry this visit; the employee can tap Start. */
    data class NotStarted(val declined: Boolean) : FocusEvent
}

/** What the controller remembers between decisions. */
data class FocusMemory(
    /** Android reported Lock Task / pinning as active at the last check. */
    val active: Boolean = false,
    /** Set when we decided to start, until Android confirms (or we give up). */
    val startRequestedAt: Long? = null,
    /** Set when we asked Android to stop, until it confirms. */
    val stopReason: FocusStopReason? = null,
    /** The employee said no during this office visit. Cleared when the phone is seen OUTSIDE. */
    val userStoppedThisVisit: Boolean = false,
    /** The background "start Focus Mode?" notification was already posted this visit. */
    val offeredThisVisit: Boolean = false,
    /** The last start this visit wasn't accepted within the timeout (not a clear "no"). */
    val startFailed: Boolean = false,
)

data class FocusInput(
    val state: SecurityState,
    /** "Start automatically at the office" (the settings switch). */
    val autoStart: Boolean,
    /** A SafeComp screen is visible: the only time Android lets us call startLockTask(). */
    val foreground: Boolean,
    /** What Android says right now (ActivityManager.lockTaskModeState != NONE). */
    val systemActive: Boolean,
    val nowMillis: Long,
    val request: FocusRequest? = null,
    /** When the activity actually called startLockTask() for the pending start. */
    val startDeliveredAt: Long? = null,
    /** The employee's latest touch on SafeComp itself. Android's "Pin app?" dialog covers
     *  the whole screen, so a touch on SafeComp means that dialog has closed. */
    val lastInteractionAt: Long? = null,
)

data class FocusDecision(
    val memory: FocusMemory,
    val action: FocusAction? = null,
    val event: FocusEvent? = null,
)

/**
 * The rules that tie Focus Mode to the geofence state. Pure Kotlin with no Android types, so
 * every rule is unit-tested, the same way as SecurityStateMachine.
 *
 * Deliberately separate from the vault's state machine: Focus Mode only READS the security
 * state. A bug here can't weaken when the vault locks.
 *
 * The rules, in order:
 *  1. Android is the source of truth. If Lock Task ended without us asking, the employee
 *     ended it, so we remember their "no" for the rest of this visit. A visit ends only when
 *     the phone is confirmed OUTSIDE: UNKNOWN (location off, a weak signal, the re-check after
 *     the app restarts) must not count as a new arrival, or the employee would be asked again.
 *  2. Only INSIDE allows Focus Mode. OUTSIDE or UNKNOWN always stops it. During the grace
 *     period the state is still INSIDE, so Focus Mode ends exactly when the vault locks.
 *  3. The employee's choice wins: a stop is never overridden by an automatic start.
 *  4. Automatic start needs the settings switch on, and SafeComp in the foreground.
 *     In the background we only offer it with a notification.
 */
object FocusPolicy {

    /** No answer this long after startLockTask(): stop showing "starting". Generous, because
     *  the employee may be reading the dialog. Accepting it later still works. */
    const val START_TIMEOUT_MILLIS = 60_000L

    /** After the employee touches SafeComp again, Android gets this long to report a pin
     *  (in case they pressed "Pin" just before) before it counts as "No thanks". */
    const val DECLINE_SETTLE_MILLIS = 1_500L

    fun decide(memory: FocusMemory, input: FocusInput): FocusDecision {
        var m = memory
        var event: FocusEvent? = null

        // 1. Reconcile with what Android actually did.
        when {
            input.systemActive && !m.active -> {
                m = m.copy(active = true, startRequestedAt = null, startFailed = false, stopReason = null)
                event = FocusEvent.Started
            }
            !input.systemActive && m.active -> {
                // Ended without us asking = the employee unpinned it.
                val reason = m.stopReason ?: FocusStopReason.USER
                m = m.copy(
                    active = false,
                    stopReason = null,
                    userStoppedThisVisit = m.userStoppedThisVisit || reason != FocusStopReason.LEFT_OFFICE,
                )
                event = FocusEvent.Ended(reason)
            }
            !input.systemActive && m.startRequestedAt != null -> {
                val now = input.nowMillis
                val delivered = input.startDeliveredAt
                val touched = input.lastInteractionAt
                // null = still waiting; true = the employee declined; false = no answer.
                val declined: Boolean? = when {
                    // The activity hasn't asked Android yet (e.g. still starting up): wait.
                    delivered == null || delivered < m.startRequestedAt -> null
                    // Touching SafeComp again means the full-screen dialog is gone.
                    touched != null && touched > delivered ->
                        if (now - touched >= DECLINE_SETTLE_MILLIS) true else null
                    now - delivered >= START_TIMEOUT_MILLIS -> false
                    else -> null
                }
                if (declined != null) {
                    m = m.copy(startRequestedAt = null, userStoppedThisVisit = true, startFailed = !declined)
                    event = FocusEvent.NotStarted(declined)
                }
            }
        }

        // 2. Not INSIDE: no start can be pending. Only a confirmed OUTSIDE ends the visit and
        //    forgets the employee's answers (see rule 1).
        if (input.state != SecurityState.INSIDE) m = m.copy(startRequestedAt = null)
        if (input.state == SecurityState.OUTSIDE) {
            m = m.copy(userStoppedThisVisit = false, offeredThisVisit = false, startFailed = false)
        }

        // 3. Decide.
        val action: FocusAction? = when {
            input.state != SecurityState.INSIDE ->
                if (m.active) FocusAction.Stop(m.stopReason ?: FocusStopReason.LEFT_OFFICE) else null

            input.request == FocusRequest.USER_STOP || input.request == FocusRequest.TURNED_OFF -> {
                val reason = if (input.request == FocusRequest.USER_STOP) FocusStopReason.USER else FocusStopReason.TURNED_OFF
                m = m.copy(userStoppedThisVisit = true, startRequestedAt = null)
                if (m.active) FocusAction.Stop(m.stopReason ?: reason) else null
            }

            // Already on. If a stop is still waiting for Android to confirm, ask again:
            // stopping is idempotent, and the first request may have been missed.
            m.active -> m.stopReason?.let { FocusAction.Stop(it) }

            input.request == FocusRequest.USER_START && input.foreground -> {
                m = m.copy(userStoppedThisVisit = false)
                FocusAction.Start
            }

            m.startRequestedAt != null || m.userStoppedThisVisit || !input.autoStart -> null
            input.foreground -> FocusAction.Start
            !m.offeredThisVisit -> FocusAction.Offer
            else -> null
        }

        // 4. Remember what we asked for.
        m = when (action) {
            FocusAction.Start -> m.copy(startRequestedAt = input.nowMillis, startFailed = false)
            is FocusAction.Stop -> m.copy(stopReason = action.reason)
            FocusAction.Offer -> m.copy(offeredThisVisit = true)
            null -> m
        }
        return FocusDecision(m, action, event)
    }

    /** The packages Lock Task may run. SafeComp is always included, so the employee can
     *  always get back to the Stop button. */
    fun allowList(ownPackage: String, workApps: Collection<String>): List<String> =
        (listOf(ownPackage) + workApps.filter { it.isNotBlank() && it != ownPackage }.sorted()).distinct()
}
