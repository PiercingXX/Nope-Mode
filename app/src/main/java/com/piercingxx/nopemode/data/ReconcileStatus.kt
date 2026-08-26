package com.piercingxx.nopemode.data

import android.content.Context

/**
 * Last reconcile outcome, for Home (R8). Not user settings — a failed apply
 * must survive a process death so the headline cannot claim success.
 */
class ReconcileStatus(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setFailedPackages(failed: Set<String>) {
        prefs.edit().putStringSet(KEY_FAILED, failed).apply()
    }

    fun failedPackages(): Set<String> = prefs.getStringSet(KEY_FAILED, emptySet()) ?: emptySet()

    fun setExactAlarmDegraded(degraded: Boolean) {
        prefs.edit().putBoolean(KEY_EXACT_DEGRADED, degraded).apply()
    }

    fun isExactAlarmDegraded(): Boolean = prefs.getBoolean(KEY_EXACT_DEGRADED, false)

    fun setReconcileError(message: String?) {
        prefs.edit().putString(KEY_ERROR, message).apply()
    }

    fun reconcileError(): String? = prefs.getString(KEY_ERROR, null)

    private companion object {
        const val PREFS_NAME = "nopemode_reconcile"
        const val KEY_FAILED = "failed_packages"
        const val KEY_EXACT_DEGRADED = "exact_alarm_degraded"
        const val KEY_ERROR = "reconcile_error"
    }
}
