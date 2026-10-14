package com.safecomp.focus

/**
 * The part of Focus Mode that needs device-owner powers.
 *
 * Only this interface lives in `main`. The `debug` source set has a working version that
 * applies the employee's allow-list when SafeComp has been made a TEST device owner with adb.
 * The `release` source set has a stub that is never a device owner and does nothing, and
 * release builds don't even contain the DeviceAdminReceiver, so they can't be made one.
 * (Same pattern as demo mode.)
 */
interface FocusAdmin {
    /** True only while SafeComp is the device owner (debug builds, set up with adb). */
    fun isTestOwner(): Boolean

    /** Lets Lock Task run exactly these packages. False if we aren't the device owner. */
    fun applyAllowList(packages: List<String>): Boolean

    /** Empties the allow-list. If Lock Task is running, Android ends it. */
    fun clearAllowList()

    /** Gives up device-owner status so the app can be uninstalled normally. */
    fun removeTestOwner()

    /** The adb command that makes this build a test device owner, or null in release. */
    val setupCommand: String?
}
