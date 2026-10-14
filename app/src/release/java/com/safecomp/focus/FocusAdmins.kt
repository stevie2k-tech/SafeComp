package com.safecomp.focus

import android.content.Context

/**
 * RELEASE source set: SafeComp can never be a device owner here. There is no
 * DeviceAdminReceiver in the release manifest, so Focus Mode uses screen pinning, or Lock Task
 * when a real company Device Policy Controller has allow-listed SafeComp.
 */
object FocusAdmins {
    fun create(context: Context): FocusAdmin = NoFocusAdmin
}

private object NoFocusAdmin : FocusAdmin {
    override fun isTestOwner() = false
    override fun applyAllowList(packages: List<String>) = false
    override fun clearAllowList() = Unit
    override fun removeTestOwner() = Unit
    override val setupCommand: String? = null
}
