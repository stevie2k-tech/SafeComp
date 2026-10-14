package com.safecomp.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safecomp.SafeCompApp
import kotlinx.coroutines.launch

/**
 * The "Stop Focus Mode" button on the notification. Not exported: only our own
 * PendingIntent can reach it, so another app can't switch Focus Mode on or off.
 */
class FocusActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        val container = (context.applicationContext as SafeCompApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.focusController.stop()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.safecomp.focus.STOP"
    }
}
