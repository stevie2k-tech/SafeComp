package com.safecomp.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.safecomp.data.GeofenceConfig
import com.safecomp.ui.permissions.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed interface GeofenceRegistration {
    data object NotConfigured : GeofenceRegistration
    data object Active : GeofenceRegistration
    /** No "Allow all the time": the app only checks while it's open. Still fail-closed. */
    data object ForegroundOnly : GeofenceRegistration
    data class Failed(val message: String) : GeofenceRegistration
}

/**
 * Registers the office geofence with Google Play services. Play services then watches it
 * efficiently (Wi-Fi, cell and GPS, batched) and wakes [GeofenceBroadcastReceiver] on
 * enter, dwell and exit, even while SafeComp is closed.
 *
 * Registration is lost on reboot, on app update, and when location is switched off, so we
 * re-register on boot, on update, and every time the app comes to the foreground. That's
 * safe to repeat: the same request ID simply replaces the old geofence.
 */
class GeofenceManager(
    private val context: Context,
    private val permissionChecker: PermissionChecker,
) {
    private val client = LocationServices.getGeofencingClient(context)

    private val _registration = MutableStateFlow<GeofenceRegistration>(GeofenceRegistration.NotConfigured)
    val registration: StateFlow<GeofenceRegistration> = _registration.asStateFlow()

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // Play services fills in the transition details, so the PendingIntent must be mutable.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    @SuppressLint("MissingPermission") // Checked below.
    suspend fun register(config: GeofenceConfig?): GeofenceRegistration {
        val result = when {
            config == null -> {
                unregister()
                GeofenceRegistration.NotConfigured
            }
            !permissionChecker.current().backgroundLocation -> GeofenceRegistration.ForegroundOnly
            else -> try {
                val geofence = Geofence.Builder()
                    .setRequestId(REQUEST_ID)
                    .setCircularRegion(config.latitude, config.longitude, config.radiusMeters)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                            Geofence.GEOFENCE_TRANSITION_DWELL or
                            Geofence.GEOFENCE_TRANSITION_EXIT,
                    )
                    // DWELL fires only after the phone has stayed inside this long.
                    .setLoiteringDelay(config.dwellSeconds * 1000)
                    .build()
                val request = GeofencingRequest.Builder()
                    // If we're already inside or outside when registering, report it now.
                    .setInitialTrigger(
                        GeofencingRequest.INITIAL_TRIGGER_ENTER or
                            GeofencingRequest.INITIAL_TRIGGER_DWELL or
                            GeofencingRequest.INITIAL_TRIGGER_EXIT,
                    )
                    .addGeofence(geofence)
                    .build()
                client.addGeofences(request, pendingIntent).await()
                GeofenceRegistration.Active
            } catch (e: ApiException) {
                GeofenceRegistration.Failed(GeofenceStatusCodes.getStatusCodeString(e.statusCode))
            } catch (e: SecurityException) {
                GeofenceRegistration.ForegroundOnly
            }
        }
        _registration.value = result
        return result
    }

    suspend fun unregister() {
        runCatching { client.removeGeofences(listOf(REQUEST_ID)).await() }
        _registration.value = GeofenceRegistration.NotConfigured
    }

    private companion object {
        const val REQUEST_ID = "office"
    }
}
