package com.piercingxx.nopemode.enforce

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
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

        // Release anything recorded but no longer desired, PLUS anything the
        // platform reports suspended that we no longer want. The second half
        // matters: `suspend_record` is our bookkeeping, and if it is ever lost —
        // app data cleared, database recreated, a build installed over one that
        // suspended things — the platform keeps those packages suspended with
        // nothing left pointing at them. Shell cannot clear a device-owner
        // suspension, so only this code can, and it will not act on a table that
        // no longer remembers them.
        //
        // Deriving the release set from the platform rather than from our own
        // record is what D8 asks for: state is read back, never accumulated.
        val onPlatform = platformSuspended()
        val toRelease = (recorded + onPlatform) - desired
        Log.i(TAG, "recorded=$recorded platformSuspended=$onPlatform desired=$desired toRelease=$toRelease")
        if (toRelease.isNotEmpty()) {
            // The release return value matters as much as the suspend one: a
            // package we cannot release stays suspended with nothing tracking
            // it, which is the orphan case that got us here.
            val notReleased: Array<out String>? = dpm.setPackagesSuspended(
                NopeDeviceAdminReceiver.componentName(context),
                toRelease.toTypedArray(),
                false,
            )
            val stuck = notReleased?.toList().orEmpty()
            if (stuck.isNotEmpty()) {
                Log.w(TAG, "could not release: $stuck")
            }
            // Only forget the ones that actually released.
            runBlocking { suspendRecordDao.deleteByPackages((toRelease - stuck.toSet()).toList()) }
        }

        // Suspend the newly-desired set. Record BEFORE suspending so a crash
        // mid-activation is recoverable on boot.
        val toSuspend = desired - recorded
        val failed = mutableSetOf<String>()
        if (toSuspend.isNotEmpty()) {
            runBlocking { suspendRecordDao.insertAll(toSuspend.map { SuspendRecord(it) }) }
            // Declared nullable on purpose. The framework annotates the return
            // non-null and normally hands back an EMPTY array when everything
            // succeeded — but it arrives here as a platform type, so treating it
            // as non-null is an assumption rather than a guarantee. Naming the
            // type keeps the null branch honest instead of an always-true guard.
            val refused: Array<out String>? = dpm.setPackagesSuspended(
                NopeDeviceAdminReceiver.componentName(context),
                toSuspend.toTypedArray(),
                true,
            )
            // Whatever comes back is the set it could NOT suspend. Never discard
            // it (R8): the record must not claim a package the platform refused.
            val refusedList = refused?.toList().orEmpty()
            failed.addAll(refusedList)
            if (refusedList.isNotEmpty()) {
                runBlocking { suspendRecordDao.deleteByPackages(refusedList) }
            }
        }

        if (failed.isNotEmpty()) {
            Log.w(TAG, "could not suspend: $failed")
        }
        return Enforcer.Result(failed)
    }

    /**
     * Every installed package the platform currently reports as suspended.
     *
     * `FLAG_SUSPENDED` is the platform's own answer, so orphans left by a lost
     * `suspend_record` are still visible here and can be released.
     */
    private fun platformSuspended(): Set<String> =
        runCatching {
            context.packageManager.getInstalledApplications(0)
                .filter { (it.flags and ApplicationInfo.FLAG_SUSPENDED) != 0 }
                .map { it.packageName }
                .toSet()
        }.getOrElse {
            Log.w(TAG, "could not read suspended packages", it)
            emptySet()
        }

    private companion object {
        const val TAG = "SuspendEnforcer"
    }
}