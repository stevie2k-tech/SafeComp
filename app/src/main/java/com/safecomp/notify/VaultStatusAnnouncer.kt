package com.safecomp.notify

import com.safecomp.security.Announcement
import com.safecomp.security.Announcements
import com.safecomp.security.SecuritySnapshot
import com.safecomp.security.SnapshotStore
import com.safecomp.security.SecurityStateListener
import com.safecomp.security.StateReason
import com.safecomp.ui.components.description

/** Posts "Vault locked" / "Vault unlocked" when [Announcements] says the user should know. */
class VaultStatusAnnouncer(
    private val notifier: Notifier,
    private val store: SnapshotStore,
    private val isDemoActive: () -> Boolean,
) : SecurityStateListener {

    override suspend fun onStateChanged(old: SecuritySnapshot, new: SecuritySnapshot) {
        when (Announcements.decide(store.lastAnnouncedUnlocked, new)) {
            Announcement.UNLOCKED -> {
                notifier.vaultUnlocked(demo = isDemoActive())
                store.lastAnnouncedUnlocked = true
            }
            Announcement.LOCKED -> {
                notifier.vaultLocked(lockedText(new.reason), demo = isDemoActive())
                store.lastAnnouncedUnlocked = false
            }
            null -> if (new.reason == StateReason.NOT_CONFIGURED) store.lastAnnouncedUnlocked = null
        }
    }

    private fun lockedText(reason: StateReason): String = when (reason) {
        StateReason.OUTSIDE_FIX, StateReason.OUTSIDE_GEOFENCE ->
            "You left the office area. Company files are locked until you return."
        else -> reason.description() + " Company files are locked."
    }
}
