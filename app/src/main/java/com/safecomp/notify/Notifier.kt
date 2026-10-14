package com.safecomp.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.safecomp.MainActivity
import com.safecomp.R
import com.safecomp.focus.FocusActionReceiver
import com.safecomp.focus.FocusMode

/**
 * Vault-status and Focus Mode notifications. They never include file names, contents or app
 * names, only a generic status, so they're safe to show on the lock screen.
 */
class Notifier(private val context: Context) {

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(VAULT_CHANNEL, "Vault status", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Tells you when the vault locks or unlocks." },
        )
        manager.createNotificationChannel(
            NotificationChannel(FOCUS_CHANNEL, "Focus Mode", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Tells you when Focus Mode starts or ends, with a Stop button." },
        )
    }

    fun vaultLocked(detail: String, demo: Boolean) = post(
        channel = VAULT_CHANNEL,
        id = VAULT_ID,
        title = "Vault locked" + demo.suffix(),
        text = detail,
        icon = R.drawable.ic_stat_vault_locked,
    )

    fun vaultUnlocked(demo: Boolean) = post(
        channel = VAULT_CHANNEL,
        id = VAULT_ID,
        title = "Vault unlocked" + demo.suffix(),
        text = "You're in the office. Company files can be opened.",
        icon = R.drawable.ic_stat_vault_unlocked,
    )

    /** Dismissible on purpose: Focus Mode is opt-in, so this is information, not a leash. */
    fun focusStarted(mode: FocusMode, workAppCount: Int, demo: Boolean) = post(
        channel = FOCUS_CHANNEL,
        id = FOCUS_ID,
        title = "Focus Mode on" + demo.suffix(),
        text = when (mode) {
            FocusMode.PINNING -> "SafeComp is pinned to the screen while you're at the office. " +
                "Tap Stop, or use Android's unpin gesture, to leave Focus Mode at any time."
            FocusMode.TEST_OWNER -> "Only SafeComp and your $workAppCount work app" +
                "${if (workAppCount == 1) "" else "s"} can be opened. Tap Stop to leave at any time."
            FocusMode.MANAGED -> "Only the apps your organisation allows can be opened. " +
                "Tap Stop to leave at any time."
        },
        icon = R.drawable.ic_stat_vault_unlocked,
        stopAction = true,
    )

    fun focusOffer(demo: Boolean) = post(
        channel = FOCUS_CHANNEL,
        id = FOCUS_ID,
        title = "You're at the office" + demo.suffix(),
        text = "Open SafeComp to start Focus Mode. You can stop it at any time.",
        icon = R.drawable.ic_stat_vault_unlocked,
    )

    /** [locationUnconfirmed]: the state is UNKNOWN (e.g. location off, weak signal), not OUTSIDE. */
    fun focusEnded(locationUnconfirmed: Boolean, demo: Boolean) = post(
        channel = FOCUS_CHANNEL,
        id = FOCUS_ID,
        title = "Focus Mode ended" + demo.suffix(),
        text = if (locationUnconfirmed) {
            "SafeComp can no longer confirm you're in the office (for example, location is off " +
                "or the signal is weak), so your phone is back to normal."
        } else {
            "You left the office, so your phone is back to normal."
        },
        icon = R.drawable.ic_stat_vault_locked,
    )

    /** The employee stopped it themselves: no need to tell them, just remove the old one. */
    fun clearFocus() = NotificationManagerCompat.from(context).cancel(FOCUS_ID)

    private fun Boolean.suffix() = if (this) " (demo)" else ""

    private fun post(channel: String, id: Int, title: String, text: String, icon: Int, stopAction: Boolean = false) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return // The user said no to notifications; the home screen still shows the state.
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (stopAction) {
            // Explicit intent to our own non-exported receiver: other apps can't trigger it.
            val stop = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, FocusActionReceiver::class.java).setAction(FocusActionReceiver.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Stop Focus Mode", stop)
        }
        try {
            // One ID per channel: each new status replaces the previous one, so only the
            // current status is ever shown.
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private companion object {
        const val VAULT_CHANNEL = "vault_status"
        const val FOCUS_CHANNEL = "focus_mode"
        const val VAULT_ID = 1
        const val FOCUS_ID = 2
    }
}
