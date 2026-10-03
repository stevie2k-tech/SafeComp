package com.safecomp.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safecomp.SafeCompApp
import kotlinx.coroutines.launch

/** Geofences are wiped on reboot and on app update, so register the office geofence again. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val container = (context.applicationContext as SafeCompApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.geofenceManager.register(container.geofenceConfigRepository.current())
            } finally {
                pending.finish()
            }
        }
    }
}
