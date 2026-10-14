package com.safecomp.focus

import android.content.Context
import android.util.Log
import com.safecomp.BuildConfig
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.safecomp.data.AppPreferences
import com.safecomp.data.FocusChange
import com.safecomp.data.FocusVisit
import com.safecomp.data.WorkApp
import com.safecomp.data.WorkAppDao
import com.safecomp.log.EventLogRepository
import com.safecomp.notify.Notifier
import com.safecomp.security.SecuritySnapshot
import com.safecomp.security.SecurityState
import com.safecomp.security.SecurityStateListener
import com.safecomp.security.SecurityStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What the activity must do. Only an activity can call startLockTask()/stopLockTask(). */
enum class FocusCommand { START, STOP }

/** A command waiting for the activity. Held as state, not fired as an event, so it can't be
 *  lost when it's sent a moment before the activity is ready (e.g. on a cold start). */
data class PendingFocusCommand(val id: Long, val command: FocusCommand)

/** Everything the UI shows about Focus Mode. */
data class FocusStatus(
    val autoStart: Boolean = true,
    val mode: FocusMode = FocusMode.PINNING,
    val active: Boolean = false,
    /** Waiting for Android (e.g. the "Pin app?" dialog is showing). */
    val starting: Boolean = false,
    val stoppedThisVisit: Boolean = false,
    /** The last start failed without the employee saying no. */
    val startFailed: Boolean = false,
    val inside: Boolean = false,
)

/**
 * Runs Focus Mode from the geofence state. All the decisions are in [FocusPolicy]; this class
 * feeds it (security state, settings, whether SafeComp is visible, what Android reports) and
 * carries out what it says: commands for the activity, the device-owner allow-list,
 * notifications and the event log.
 *
 * It re-evaluates whenever the security state changes, the app comes to the foreground or
 * leaves it, the employee presses a button, and once a second while a SafeComp screen is
 * visible ([sync]). Polling is the only way to notice that the employee unpinned the app:
 * Android sends no callback to apps that aren't the device owner.
 */
class FocusController(
    private val context: Context,
    private val preferences: AppPreferences,
    private val workAppDao: WorkAppDao,
    val admin: FocusAdmin,
    private val securityState: SecurityStateRepository,
    private val eventLog: EventLogRepository,
    private val notifier: Notifier,
    private val isDemoActive: () -> Boolean,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) : SecurityStateListener, DefaultLifecycleObserver {

    private val mutex = Mutex()

    /** The local date the saved visit answers belong to (see [restoreMemory]). */
    private var visitDay = preferences.focusVisit.epochDay

    private var memory = restoreMemory()

    /** True while our "You're at the office" offer is the Focus notification on screen. */
    @Volatile private var offerShowing = false

    @Volatile private var foreground = false

    /** When the activity called startLockTask() for the current start (monotonic clock). */
    @Volatile private var startDeliveredAt: Long? = null

    /** The employee's latest touch on SafeComp (monotonic clock). */
    @Volatile private var lastInteractionAt: Long? = null

    private var nextCommandId = 0L
    private val _pendingCommand = MutableStateFlow<PendingFocusCommand?>(null)

    /** The command the activity must carry out, if any. See [commandHandled]. */
    val pendingCommand: StateFlow<PendingFocusCommand?> = _pendingCommand.asStateFlow()

    private val _status = MutableStateFlow(FocusStatus(autoStart = preferences.focusAutoStart))
    val status: StateFlow<FocusStatus> = _status.asStateFlow()

    val workApps: Flow<List<String>> = workAppDao.observe()

    override suspend fun onStateChanged(old: SecuritySnapshot, new: SecuritySnapshot) {
        if (old.state != new.state) evaluate()
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        scope.launch { evaluate() }
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        scope.launch { evaluate() }
    }

    /** From MainActivity: the employee touched SafeComp. Android's "Pin app?" dialog covers
     *  the whole screen, so this also tells us that dialog has closed. */
    fun onUserInteraction() {
        lastInteractionAt = clock()
    }

    /** The activity carried out [id]. A newer command (if any) stays pending. */
    fun commandHandled(id: Long) {
        val handled = _pendingCommand.value
        if (handled?.id == id && handled.command == FocusCommand.START) startDeliveredAt = clock()
        if (BuildConfig.DEBUG && handled?.id == id) Log.d(TAG, "activity ran ${handled.command}")
        _pendingCommand.update { if (it?.id == id) null else it }
    }

    /** Called every second while a SafeComp screen is visible. */
    suspend fun sync() = evaluate()

    suspend fun start() = evaluate(FocusRequest.USER_START)

    suspend fun stop() = evaluate(FocusRequest.USER_STOP)

    suspend fun setAutoStart(enabled: Boolean) {
        preferences.focusAutoStart = enabled
        evaluate(if (enabled) null else FocusRequest.TURNED_OFF)
    }

    suspend fun setWorkApp(packageName: String, selected: Boolean) {
        if (selected) workAppDao.insert(WorkApp(packageName)) else workAppDao.delete(packageName)
        mutex.withLock {
            // Already in Lock Task as the test owner: the change applies straight away.
            if (memory.active && admin.isTestOwner()) admin.applyAllowList(currentAllowList())
        }
    }

    suspend fun removeTestOwner() {
        stop()
        admin.removeTestOwner()
        evaluate()
    }

    /** Reset SafeComp: forget the work apps and this visit's answers too. */
    suspend fun reset() {
        workAppDao.clear()
        mutex.withLock {
            memory = memory.copy(userStoppedThisVisit = false, offeredThisVisit = false, startFailed = false)
            preferences.focusVisit = FocusVisit()
        }
    }

    // Runs on a background thread: it makes several system calls (DevicePolicyManager,
    // ActivityManager, notifications) and sync() calls it every second from the UI.
    private suspend fun evaluate(request: FocusRequest? = null): Unit = withContext(Dispatchers.Default) {
        mutex.withLock { decideAndAct(request) }
    }

    private suspend fun decideAndAct(request: FocusRequest?) {
        // Safety net: a "no" lasts until the phone is seen outside, or until tomorrow at the
        // latest (in case SafeComp never got to see the employee leave).
        if (visitDay != today()) {
            memory = memory.copy(userStoppedThisVisit = false, offeredThisVisit = false, startFailed = false)
            visitDay = today()
        }
        val before = memory
        val snapshot = securityState.snapshot.value
        val mode = FocusCapabilities.detect(context, admin)
        val decision = FocusPolicy.decide(
            memory,
            FocusInput(
                state = snapshot.state,
                autoStart = preferences.focusAutoStart,
                foreground = foreground,
                systemActive = FocusCapabilities.isLockTaskActive(context),
                nowMillis = clock(),
                request = request,
                startDeliveredAt = startDeliveredAt,
                lastInteractionAt = lastInteractionAt,
            ),
        )
        memory = decision.memory
        if (BuildConfig.DEBUG && (decision.action != null || decision.event != null || request != null)) {
            // Debug builds only: states and decisions, never anything about apps or places.
            Log.d(TAG, "state=${snapshot.state} mode=$mode fg=$foreground request=$request -> action=${decision.action} event=${decision.event}")
        }
        decision.event?.let { report(it, mode, snapshot) }

        when (val action = decision.action) {
            FocusAction.Start -> {
                // As test owner, set the allow-list first so startLockTask() enters real
                // Lock Task instead of asking to pin.
                if (mode == FocusMode.TEST_OWNER) admin.applyAllowList(currentAllowList())
                send(FocusCommand.START)
                // The "You're at the office" offer has done its job.
                if (offerShowing) {
                    notifier.clearFocus()
                    offerShowing = false
                }
            }
            is FocusAction.Stop -> {
                // As test owner, emptying the allow-list ends Lock Task even when SafeComp is
                // in the background. Otherwise the activity calls stopLockTask().
                send(FocusCommand.STOP)
                if (mode == FocusMode.TEST_OWNER) {
                    admin.clearAllowList()
                    // SafeComp may be in the background, where nothing else re-checks: confirm
                    // the end promptly so the log and the "Focus Mode ended" notice aren't late.
                    scope.launch {
                        delay(500)
                        evaluate()
                    }
                }
            }
            FocusAction.Offer -> {
                notifier.focusOffer(isDemoActive())
                offerShowing = true
            }
            null -> Unit
        }
        // Drop a command that no longer applies: a START after the employee left or said no,
        // or a STOP once Android has already stopped.
        _pendingCommand.update { pending ->
            when {
                pending == null -> null
                pending.command == FocusCommand.START && memory.startRequestedAt == null -> null
                pending.command == FocusCommand.STOP && !memory.active -> null
                else -> pending
            }
        }

        // "You're at the office" is out of date once the phone isn't confirmed inside.
        if (offerShowing && snapshot.state != SecurityState.INSIDE) {
            notifier.clearFocus()
            offerShowing = false
        }

        // Keep this visit's answers across app restarts.
        if (memory.userStoppedThisVisit != before.userStoppedThisVisit ||
            memory.offeredThisVisit != before.offeredThisVisit ||
            memory.startFailed != before.startFailed
        ) {
            preferences.focusVisit = FocusVisit(
                stopped = memory.userStoppedThisVisit,
                offered = memory.offeredThisVisit,
                startFailed = memory.startFailed,
                epochDay = visitDay,
            )
        }

        _status.value = FocusStatus(
            autoStart = preferences.focusAutoStart,
            mode = mode,
            active = memory.active,
            starting = memory.startRequestedAt != null,
            stoppedThisVisit = memory.userStoppedThisVisit,
            startFailed = memory.startFailed,
            inside = snapshot.state == SecurityState.INSIDE,
        )
    }

    private suspend fun report(event: FocusEvent, mode: FocusMode, snapshot: SecuritySnapshot) {
        val demo = isDemoActive()
        offerShowing = false // every report below replaces or removes the Focus notification
        when (event) {
            FocusEvent.Started -> {
                eventLog.focusChange(FocusChange.STARTED, snapshot)
                notifier.focusStarted(mode, workAppDao.all().size, demo)
            }
            is FocusEvent.Ended -> when (event.reason) {
                FocusStopReason.USER -> {
                    eventLog.focusChange(FocusChange.STOPPED_BY_USER, snapshot)
                    notifier.clearFocus()
                }
                FocusStopReason.TURNED_OFF -> {
                    eventLog.focusChange(FocusChange.TURNED_OFF, snapshot)
                    notifier.clearFocus()
                }
                FocusStopReason.LEFT_OFFICE -> {
                    eventLog.focusChange(FocusChange.ENDED_LEFT_OFFICE, snapshot)
                    notifier.focusEnded(locationUnconfirmed = snapshot.state == SecurityState.UNKNOWN, demo = demo)
                }
            }
            is FocusEvent.NotStarted -> {
                eventLog.focusChange(if (event.declined) FocusChange.DECLINED else FocusChange.NOT_STARTED, snapshot)
                notifier.clearFocus()
            }
        }
    }

    /**
     * Starts from what Android reports (so a restarted process doesn't log a second "started")
     * and from today's saved answers (so the employee isn't asked again after a restart).
     */
    private fun restoreMemory(): FocusMemory {
        val visit = preferences.focusVisit
        val today = visit.epochDay == today()
        return FocusMemory(
            active = FocusCapabilities.isLockTaskActive(context),
            userStoppedThisVisit = today && visit.stopped,
            offeredThisVisit = today && visit.offered,
            startFailed = today && visit.startFailed,
        )
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    private companion object {
        const val TAG = "SafeCompFocus"
    }

    private fun send(command: FocusCommand) {
        if (command == FocusCommand.START) startDeliveredAt = null
        _pendingCommand.value = PendingFocusCommand(++nextCommandId, command)
    }

    private suspend fun currentAllowList() = FocusPolicy.allowList(context.packageName, workAppDao.all())
}
