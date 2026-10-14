package com.safecomp.focus

import android.app.admin.DeviceAdminReceiver

/**
 * DEBUG BUILDS ONLY. The component `adb shell dpm set-device-owner` points at. It asks for
 * no device-admin policies at all (see res/xml/focus_device_admin.xml): it exists only so
 * SafeComp can set a Lock Task allow-list on a test emulator.
 */
class FocusAdminReceiver : DeviceAdminReceiver()
