package com.safecomp.security

enum class Announcement { LOCKED, UNLOCKED }

/**
 * Decides whether a state change deserves a "Vault locked" / "Vault unlocked" notification.
 *
 * [lastAnnouncedUnlocked] is what we last told the user (null = never told them anything).
 * The rules keep notifications meaningful instead of noisy:
 *  - "Unlocked" when the vault opens and we haven't already said so.
 *  - "Locked" only if we previously said "unlocked" (no "locked" spam on a fresh install).
 *  - No "locked" for passing states: CHECKING (just started / re-validating) and
 *    CONFIRMING_INSIDE. Otherwise every app start in the office would post
 *    "locked" then "unlocked" a second later.
 *
 * Notifications are only messages. Enforcement doesn't depend on them: the vault is
 * locked the instant the state leaves INSIDE, whether or not a notification is shown.
 */
object Announcements {
    private val transientReasons = setOf(StateReason.CHECKING, StateReason.CONFIRMING_INSIDE)

    fun decide(lastAnnouncedUnlocked: Boolean?, snapshot: SecuritySnapshot): Announcement? = when {
        snapshot.isUnlocked -> if (lastAnnouncedUnlocked != true) Announcement.UNLOCKED else null
        snapshot.reason in transientReasons -> null
        lastAnnouncedUnlocked == true -> Announcement.LOCKED
        else -> null
    }
}
