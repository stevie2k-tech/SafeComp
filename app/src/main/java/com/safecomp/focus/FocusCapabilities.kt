package com.safecomp.focus

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context

/** Runtime detection: what can Focus Mode actually do on this phone right now? */
object FocusCapabilities {

    fun detect(context: Context, admin: FocusAdmin): FocusMode = when {
        admin.isTestOwner() -> FocusMode.TEST_OWNER
        // A company DPC allow-listed SafeComp: real Lock Task, with the admin's app list.
        context.getSystemService(DevicePolicyManager::class.java)
            .isLockTaskPermitted(context.packageName) -> FocusMode.MANAGED
        // Everyone else: screen pinning. Android asks the user, and only SafeComp is usable.
        else -> FocusMode.PINNING
    }

    /** True while the phone is in Lock Task or screen pinning. */
    fun isLockTaskActive(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).lockTaskModeState !=
            ActivityManager.LOCK_TASK_MODE_NONE
}
