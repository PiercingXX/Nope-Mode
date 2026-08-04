package com.piercingxx.nopemode.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Device admin receiver. Exists so Nope-Mode can be provisioned as device
 * owner, which is what unlocks [android.app.admin.DevicePolicyManager
 * .setPackagesSuspended] — the only no-root API that makes an app both silent
 * and un-openable, and that survives a reboot.
 *
 * Provisioned once, over ADB, on a device with no accounts:
 *
 *     adb shell dpm set-device-owner \
 *         com.piercingxx.nopemode/.admin.NopeDeviceAdminReceiver
 */
class NopeDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: android.content.Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "device admin enabled")
    }

    override fun onDisabled(context: Context, intent: android.content.Intent) {
        super.onDisabled(context, intent)
        // Losing admin means SuspendEnforcer is gone. Anything still suspended
        // would be stranded with no way to release it, so WS5 must un-suspend
        // from suspend_record before admin is ever given up.
        Log.w(TAG, "device admin disabled")
    }

    companion object {
        private const val TAG = "NopeDeviceAdmin"

        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, NopeDeviceAdminReceiver::class.java)
    }
}
