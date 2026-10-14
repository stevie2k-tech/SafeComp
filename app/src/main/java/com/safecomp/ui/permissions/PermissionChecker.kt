package com.safecomp.ui.permissions

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class PermissionStatus(
    /** Precise (fine) location. Approximate location alone is not enough: it can be off by
     *  1–3 km, which is bigger than any office geofence. */
    val preciseLocation: Boolean,
    /** "Allow all the time". Needed for geofence events while the app is closed. */
    val backgroundLocation: Boolean,
    val notifications: Boolean,
    /** The user picked "Approximate" in the Android 12+ dialog. We explain why that won't work. */
    val approximateOnly: Boolean = false,
    /** SafeComp is excluded from battery optimisation, so Android won't hold back its
     *  background geofence work. Not a permission, so [allGranted] ignores it. */
    val batteryUnrestricted: Boolean = true,
    /** A screen lock (PIN, pattern or password) is set. Phase 7 needs it to check it's the owner. */
    val screenLock: Boolean = true,
) {
    val allGranted: Boolean get() = preciseLocation && backgroundLocation && notifications
}

/** Reads the current permission state. Always re-read on resume: the user can change
 *  permissions in system settings at any time, and revoking location must lock the vault. */
class PermissionChecker(private val context: Context) {

    fun current(): PermissionStatus {
        val precise = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            precise && isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            precise // Before Android 10, foreground location also covered the background.
        }
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        val approximateOnly = !precise && isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val battery = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
        val screenLock = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
        return PermissionStatus(precise, background, notifications, approximateOnly, battery, screenLock)
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
