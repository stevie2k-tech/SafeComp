package com.safecomp.security

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit

/**
 * Saves the security snapshot so it survives the process being killed. That's needed for
 * background locking: when the grace period ends, a job may run in a brand-new process,
 * and it must know the vault was unlocked and a lock is due.
 *
 * Holds only the state, its reason and monotonic timestamps. No location data.
 * The file is app-private and excluded from backups. Changing it requires root, which is
 * outside the threat model (see README).
 */
class SecuritySnapshotStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("safecomp_security_state", Context.MODE_PRIVATE)

    /** Android's boot counter. elapsedRealtime restarts at 0 on boot, so saved times from a
     *  previous boot are meaningless. */
    fun currentBootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun load(): Pair<SecuritySnapshot, Int>? {
        if (!prefs.contains(KEY_STATE)) return null
        return runCatching {
            val pendingSince = prefs.getLong(KEY_PENDING_SINCE, -1)
            val snapshot = SecuritySnapshot(
                state = SecurityState.valueOf(prefs.getString(KEY_STATE, null)!!),
                reason = StateReason.valueOf(prefs.getString(KEY_REASON, null)!!),
                enterEvidenceSince = prefs.getLong(KEY_ENTER_SINCE, -1).takeIf { it >= 0 },
                pendingExit = if (pendingSince >= 0) {
                    PendingExit(
                        sinceMillis = pendingSince,
                        target = SecurityState.valueOf(prefs.getString(KEY_PENDING_TARGET, null)!!),
                        reason = StateReason.valueOf(prefs.getString(KEY_PENDING_REASON, null)!!),
                    )
                } else {
                    null
                },
                lastConfirmedAt = prefs.getLong(KEY_LAST_CONFIRMED, -1).takeIf { it >= 0 },
                resumeInsideOnConfirm = prefs.getBoolean(KEY_RESUME, false),
            )
            snapshot to prefs.getInt(KEY_BOOT, -1)
        }.getOrNull() // A corrupt file is treated as "nothing saved": start from UNKNOWN.
    }

    fun save(snapshot: SecuritySnapshot) {
        prefs.edit {
            putString(KEY_STATE, snapshot.state.name)
            putString(KEY_REASON, snapshot.reason.name)
            putLong(KEY_ENTER_SINCE, snapshot.enterEvidenceSince ?: -1)
            putLong(KEY_PENDING_SINCE, snapshot.pendingExit?.sinceMillis ?: -1)
            putString(KEY_PENDING_TARGET, snapshot.pendingExit?.target?.name)
            putString(KEY_PENDING_REASON, snapshot.pendingExit?.reason?.name)
            putLong(KEY_LAST_CONFIRMED, snapshot.lastConfirmedAt ?: -1)
            putBoolean(KEY_RESUME, snapshot.resumeInsideOnConfirm)
            putInt(KEY_BOOT, currentBootCount())
        }
    }

    /** What the user was last told: true = "unlocked", false = "locked", null = nothing yet. */
    var lastAnnouncedUnlocked: Boolean?
        get() = if (prefs.contains(KEY_ANNOUNCED)) prefs.getBoolean(KEY_ANNOUNCED, false) else null
        set(value) = prefs.edit {
            if (value == null) remove(KEY_ANNOUNCED) else putBoolean(KEY_ANNOUNCED, value)
        }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_REASON = "reason"
        const val KEY_ENTER_SINCE = "enter_since"
        const val KEY_PENDING_SINCE = "pending_since"
        const val KEY_PENDING_TARGET = "pending_target"
        const val KEY_PENDING_REASON = "pending_reason"
        const val KEY_LAST_CONFIRMED = "last_confirmed"
        const val KEY_RESUME = "resume_inside"
        const val KEY_BOOT = "boot_count"
        const val KEY_ANNOUNCED = "last_announced_unlocked"
    }
}
