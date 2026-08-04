package com.piercingxx.nopemode.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Which enforcement tier is available, and the one action that gives it up.
 *
 * Nope-Mode has two tiers (design.md §4). [Tier.DEVICE_OWNER] can suspend
 * packages outright; [Tier.FALLBACK] can only cancel notifications after
 * they have already posted, so a sound may escape first. Callers must be able
 * to tell the user which one they are actually on — silently degrading would
 * make the app claim protection it isn't providing.
 */
class DeviceOwnerManager(context: Context) {

    private val appContext = context.applicationContext
    private val dpm =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    enum class Tier { DEVICE_OWNER, FALLBACK }

    val adminComponent: ComponentName
        get() = NopeDeviceAdminReceiver.componentName(appContext)

    fun isDeviceOwner(): Boolean =
        dpm.isDeviceOwnerApp(appContext.packageName)

    fun tier(): Tier =
        if (isDeviceOwner()) Tier.DEVICE_OWNER else Tier.FALLBACK

    /**
     * The exact command to run over ADB to provision. Surfaced in the setup
     * screen because it can only be run on a device with zero accounts — once
     * an account exists the window is closed and reopening it costs a factory
     * reset.
     */
    fun provisioningCommand(): String =
        "adb shell dpm set-device-owner ${appContext.packageName}/" +
            ".admin.NopeDeviceAdminReceiver"

    /**
     * Give up device owner. Without this the only way off the MDM role is a
     * factory reset, so it is a hard requirement rather than a convenience
     * (design.md §2.2).
     *
     * Callers MUST release every suspended package first — once this returns,
     * [DevicePolicyManager.setPackagesSuspended] is no longer callable and
     * anything still suspended is stranded.
     *
     * @return true if device owner was relinquished.
     */
    fun relinquish(): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            dpm.clearDeviceOwnerApp(appContext.packageName)
            Log.i(TAG, "device owner relinquished")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "failed to relinquish device owner", e)
            false
        }
    }

    private companion object {
        const val TAG = "DeviceOwnerManager"
    }
}
