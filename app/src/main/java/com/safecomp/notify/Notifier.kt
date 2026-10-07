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

/**
 * Vault-status notifications. They never include file names or contents, only whether the
 * vault is locked and a generic reason, so they're safe to show on the lock screen.
 */
class Notifier(private val context: Context) {

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vault status",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Tells you when the vault locks or unlocks." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun vaultLocked(detail: String) = post(
        title = "Vault locked",
        text = detail,
        icon = R.drawable.ic_stat_vault_locked,
    )

    fun vaultUnlocked() = post(
        title = "Vault unlocked",
        text = "You're in the office. Company files can be opened.",
        icon = R.drawable.ic_stat_vault_unlocked,
    )

    private fun post(title: String, text: String, icon: Int) {
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            // Same ID for both: "unlocked" replaces "locked" and vice versa, so only the
            // current status is ever shown.
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private companion object {
        const val CHANNEL_ID = "vault_status"
        const val NOTIFICATION_ID = 1
    }
}
