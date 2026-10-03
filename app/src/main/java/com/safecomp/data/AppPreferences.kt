package com.safecomp.data

import android.content.Context
import androidx.core.content.edit

/**
 * Small, non-sensitive UI preferences only (e.g. "has onboarding been shown").
 * Nothing confidential is ever stored here: vault data lives in encrypted files and Room.
 */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("safecomp_settings", Context.MODE_PRIVATE)

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, value) }

    private companion object {
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
