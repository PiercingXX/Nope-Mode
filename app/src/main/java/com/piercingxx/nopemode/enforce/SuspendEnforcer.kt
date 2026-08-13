package com.piercingxx.nopemode.enforce

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.piercingxx.nopemode.admin.NopeDeviceAdminReceiver
import com.piercingxx.nopemode.data.SuspendRecord
import com.piercingxx.nopemode.data.SuspendRecordDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * WS5 — the device-owner tier of enforcement (design §5, §4.1).
 *
 * Uses [DevicePolicyManager.setPackagesSuspended] — the only no-root API that
 * makes an app both silent and un-openable, and that survives a reboot.
 *
 * Crash safety (design §5): a package is written to `suspend_record` BEFORE it
 * is suspended, and deleted AFTER it is released. A crash mid-activation leaves
 * a record that boot recovery can act on; a crash mid-release can only strand
 * an app that is still recorded and therefore still recoverable.
 *
 * Never report success for a package the platform refused to suspend (R8):
 * [setPackagesSuspended] returns the array it could NOT suspend, and that array
 * is surfaced through [Enforcer.Result.failed].
 *
 * Android-dependent — its behavior is deferred to the operator's on-device
 * check (design §16, SDK 37). The pure selection slice is [SuspendablePackages].
 */
class SuspendEnforcer(
    private val context: Context,
    private val suspendRecordDao: SuspendRecordDao,
) : Enforcer {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    override fun apply(desired: Set<String>): Enforcer.Result {
        val recorded = runBlocking { suspendRecordDao.observeAll().first() }
            .map { it.packageName }
            .toSet()

        // Release anything recorded but no longer desired. Unsuspend BEFORE
        // deleting the record so a crash mid-release leaves a recoverable record.
        val toRelease = recorded - desired
        if (toRelease.isNotEmpty()) {
            dpm.setPackagesSuspended(
                NopeDeviceAdminReceiver.componentName(context),
                toRelease.toTypedArray(),
                false,
            )
            runBlocking { suspendRecordDao.deleteByPackages(toRelease.toList()) }
        }

        // Suspend the newly-desired set. Record BEFORE suspending so a crash
        // mid-activation is recoverable on boot.
        val toSuspend = desired - recorded
        val failed = mutableSetOf<String>()
        if (toSuspend.isNotEmpty()) {
            runBlocking { suspendRecordDao.insertAll(toSuspend.map { SuspendRecord(it) }) }
            val refused = dpm.setPackagesSuspended(
                NopeDeviceAdminReceiver.componentName(context),
                toSuspend.toTypedArray(),
                true,
            )
            // setPackagesSuspended returns the packages it could NOT suspend
            // (or null when all succeeded). Never discard it (R8).
            refused?.let { failed.addAll(it) }
            if (refused != null) {
                runBlocking { suspendRecordDao.deleteByPackages(refused.toList()) }
            }
        }

        if (failed.isNotEmpty()) {
            Log.w(TAG, "could not suspend: $failed")
        }
        return Enforcer.Result(failed)
    }

    private companion object {
        const val TAG = "SuspendEnforcer"
    }
}