package com.piercingxx.nopemode.admin

import android.content.Context
import android.util.Log
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.ReconcileStatus
import com.piercingxx.nopemode.enforce.SuspendEnforcer
import com.piercingxx.nopemode.schedule.AlarmScheduler
import com.piercingxx.nopemode.service.RingerPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Give up device owner the only safe way: release suspended packages, tear
 * down the zen rule, then clearDeviceOwnerApp. Skipping the release strands
 * apps with no API left to unsuspend them (design §14).
 */
object RelinquishAction {

    /**
     * @return true when device owner was actually cleared.
     */
    fun run(context: Context): Boolean {
        val app = context.applicationContext
        val db = NopeDatabase.get(app)
        val result = runCatching {
            SuspendEnforcer(app, db.suspendRecordDao()).apply(emptySet())
        }.getOrElse { e ->
            Log.e(TAG, "release before relinquish failed", e)
            ReconcileStatus(app).setReconcileError(e.message ?: e.javaClass.simpleName)
            return false
        }
        if (!result.success) {
            ReconcileStatus(app).setFailedPackages(result.failed)
            Log.e(TAG, "could not release before relinquish: ${result.failed}")
            return false
        }
        val leftover = runBlocking {
            db.suspendRecordDao().observeAll().first().map { it.packageName }.toSet()
        }
        if (leftover.isNotEmpty()) {
            ReconcileStatus(app).setFailedPackages(leftover)
            Log.e(TAG, "packages still recorded as suspended: $leftover")
            return false
        }
        runCatching { RingerPolicy(app).tearDown() }
            .onFailure { Log.w(TAG, "zen rule teardown failed", it) }
        val ok = DeviceOwnerManager(app).relinquish()
        runCatching { AlarmScheduler.from(app).reconcileAndApply() }
            .onFailure { Log.w(TAG, "reconcile after relinquish failed", it) }
        return ok
    }

    private const val TAG = "RelinquishAction"
}
