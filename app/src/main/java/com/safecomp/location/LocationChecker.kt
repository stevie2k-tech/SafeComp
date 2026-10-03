package com.safecomp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.safecomp.data.GeofenceConfig
import com.safecomp.security.SecurityEvent
import com.safecomp.ui.permissions.PermissionChecker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Reads the phone's location with the Fused Location Provider. */
class LocationChecker(
    private val context: Context,
    private val permissionChecker: PermissionChecker,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(LocationManager::class.java)

    /** Returns a fail-closed event if location can't be used at all, or null if it can. */
    fun unavailableReason(): SecurityEvent? = when {
        !permissionChecker.current().preciseLocation -> SecurityEvent.PermissionMissing
        !LocationManagerCompat.isLocationEnabled(locationManager) -> SecurityEvent.LocationServicesOff
        else -> null
    }

    /** One fresh, high-accuracy fix, or null if none arrives within the timeout. */
    @SuppressLint("MissingPermission") // Callers check unavailableReason() first.
    suspend fun currentLocation(): Location? {
        if (unavailableReason() != null) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_FIX_AGE_MILLIS)
            .setDurationMillis(FIX_TIMEOUT_MILLIS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(FIX_TIMEOUT_MILLIS + 2_000) {
                client.getCurrentLocation(request, cancellation.token).await()
            }
        } catch (e: SecurityException) {
            null // Permission was revoked between the check and the call.
        } finally {
            cancellation.cancel()
        }
    }

    /** A stream of fixes about every [intervalMillis], only while collected (foreground). */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMillis: Long): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    companion object {
        const val FIX_TIMEOUT_MILLIS = 20_000L
        const val MAX_FIX_AGE_MILLIS = 5_000L
    }
}

/**
 * Detects locations from a mock-location app (e.g. a "fake GPS" app chosen in Developer
 * options). Android 12+ has isMock; older versions have the now-deprecated isFromMockProvider.
 * This catches casual spoofing. A rooted device can hide it, which the README lists as a limitation.
 */
fun Location.isMockCompat(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        isMock
    } else {
        @Suppress("DEPRECATION")
        isFromMockProvider
    }

/** Turns a fix into a state-machine event. The coordinates go no further than this function. */
fun Location.toSecurityEvent(office: GeofenceConfig): SecurityEvent.LocationFix {
    val result = FloatArray(1)
    Location.distanceBetween(latitude, longitude, office.latitude, office.longitude, result)
    return SecurityEvent.LocationFix(
        distanceMeters = result[0],
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        isMock = isMockCompat(),
    )
}
