package com.safecomp.focus

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/** DEBUG source set: the test device owner. (The release source set has a stub.) */
object FocusAdmins {
    fun create(context: Context): FocusAdmin = TestOwnerFocusAdmin(context.applicationContext)
}

/**
 * DEBUG BUILDS ONLY. Demonstrates the enterprise version of Focus Mode on an emulator.
 *
 * A real company would enroll the phone with Android Enterprise and its own Device Policy
 * Controller would set the Lock Task allow-list. Here, `adb shell dpm set-device-owner` makes
 * SafeComp itself the device owner instead, so it can apply the employee's work-app list.
 * That command only works on a phone with no accounts (in practice, an emulator).
 */
private class TestOwnerFocusAdmin(private val context: Context) : FocusAdmin {

    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, FocusAdminReceiver::class.java)

    override fun isTestOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    override fun applyAllowList(packages: List<String>): Boolean {
        if (!isTestOwner()) return false
        dpm.setLockTaskPackages(admin, packages.toTypedArray())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Never trap the user: keep the notification shade (for the Stop action), Home,
            // Overview, the power menu and the status bar info available during Lock Task.
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
            )
        }
        return true
    }

    override fun clearAllowList() {
        if (isTestOwner()) dpm.setLockTaskPackages(admin, emptyArray())
    }

    @Suppress("DEPRECATION") // Still the only way for a device owner to step down by itself.
    override fun removeTestOwner() {
        if (!isTestOwner()) return
        clearAllowList()
        dpm.clearDeviceOwnerApp(context.packageName)
    }

    override val setupCommand =
        "adb shell dpm set-device-owner ${context.packageName}/.focus.FocusAdminReceiver"
}
