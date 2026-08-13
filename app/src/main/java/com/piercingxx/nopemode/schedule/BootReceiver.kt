package com.piercingxx.nopemode.schedule

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * WS6 — the receiver that wakes the reconcile loop (design §7.2).
 *
 * Handles two events:
 *  - [Intent.ACTION_BOOT_COMPLETED]: after a reboot no alarm survives, so the
 *    loop must be re-armed. It also reconciles once so a package stranded by a
 *    crash mid-activation (still present in `suspend_record`) is released.
 *  - [ACTION_RECONCILE]: the exact alarm fired — re-derive, apply, re-arm.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, ACTION_RECONCILE -> {
                try {
                    AlarmScheduler.from(context).reconcileAndApply()
                } catch (e: Exception) {
                    // Never crash the receiver; a failed reconcile is retried on
                    // the next alarm or boot.
                    Log.e(TAG, "reconcile failed", e)
                }
            }
            else -> Log.w(TAG, "ignored action: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        /** Action the exact alarm uses to wake the reconcile loop. */
        const val ACTION_RECONCILE = "com.piercingxx.nopemode.action.RECONCILE"

        /** The single [PendingIntent] the scheduler arms and cancels. */
        fun alarmPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, BootReceiver::class.java).apply {
                action = ACTION_RECONCILE
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}