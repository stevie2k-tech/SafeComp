package com.safecomp.data

import android.content.Context
import androidx.core.content.edit

/**
 * Small, non-sensitive UI preferences only (e.g. "has onboarding been shown").
 * Nothing confidential is ever stored here: vault data lives in encrypted files and Room.
 */
/** See [AppPreferences.focusVisit]. [epochDay]: the local date the answers were given. */
data class FocusVisit(
    val stopped: Boolean = false,
    val offered: Boolean = false,
    val startFailed: Boolean = false,
    val epochDay: Long = 0,
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("safecomp_settings", Context.MODE_PRIVATE)

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, value) }

    /** Focus Mode starts automatically when the phone is confirmed inside the office.
     *  On by default; the employee can switch it off, and can stop it every time. */
    var focusAutoStart: Boolean
        get() = prefs.getBoolean(KEY_FOCUS_AUTO_START, true)
        set(value) = prefs.edit { putBoolean(KEY_FOCUS_AUTO_START, value) }

    /** Focus Mode's answers for the current office visit, kept across app restarts so the
     *  employee isn't asked again after Android stops SafeComp in the background. */
    var focusVisit: FocusVisit
        get() = FocusVisit(
            stopped = prefs.getBoolean(KEY_VISIT_STOPPED, false),
            offered = prefs.getBoolean(KEY_VISIT_OFFERED, false),
            startFailed = prefs.getBoolean(KEY_VISIT_FAILED, false),
            epochDay = prefs.getLong(KEY_VISIT_DAY, 0),
        )
        set(value) = prefs.edit {
            putBoolean(KEY_VISIT_STOPPED, value.stopped)
            putBoolean(KEY_VISIT_OFFERED, value.offered)
            putBoolean(KEY_VISIT_FAILED, value.startFailed)
            putLong(KEY_VISIT_DAY, value.epochDay)
        }

    private companion object {
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_FOCUS_AUTO_START = "focus_auto_start"
        const val KEY_VISIT_STOPPED = "focus_visit_stopped"
        const val KEY_VISIT_OFFERED = "focus_visit_offered"
        const val KEY_VISIT_FAILED = "focus_visit_start_failed"
        const val KEY_VISIT_DAY = "focus_visit_day"
    }
}
