package com.safecomp.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.safecomp.SafeCompApp
import com.safecomp.security.SecurityEvent
import kotlinx.coroutines.launch

/**
 * Receives geofence transitions from Play services, possibly in a freshly started process.
 *
 * These broadcasts can arrive minutes late (Android batches them to save battery). That's
 * why they're never the only signal: the app re-checks location itself whenever it opens.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        val container = (context.applicationContext as SafeCompApp).container
        val event = geofencingEvent.toSecurityEvent() ?: return

        // goAsync keeps the receiver alive until the (suspending) dispatch has finished.
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.securityState.dispatch(event)
            } finally {
                pending.finish()
            }
        }
    }

    private fun GeofencingEvent.toSecurityEvent(): SecurityEvent? {
        if (hasError()) {
            // GEOFENCE_NOT_AVAILABLE: the user switched location off. Fail closed.
            return if (errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                SecurityEvent.LocationServicesOff
            } else {
                null
            }
        }
        if (triggeringLocation?.isMockCompat() == true) return SecurityEvent.MockLocationDetected
        return when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> SecurityEvent.GeofenceEntered
            Geofence.GEOFENCE_TRANSITION_DWELL -> SecurityEvent.GeofenceDwelled
            Geofence.GEOFENCE_TRANSITION_EXIT -> SecurityEvent.GeofenceExited
            else -> null
        }
    }
}
