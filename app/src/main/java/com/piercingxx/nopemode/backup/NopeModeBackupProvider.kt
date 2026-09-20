package com.piercingxx.nopemode.backup

import com.piercingxx.nopemode.schedule.AlarmScheduler
import com.piercingxx.suite.backup.BackupContents
import com.piercingxx.suite.backup.SuiteBackupProvider

/**
 * Nope-Mode's door in the suite backup contract
 * (xx-apps/docs/SUITE-BACKUP-PROVIDER.md). The snapshot is the vendored
 * defaults — every SharedPreferences xml and every database, which is
 * `nope-mode.db` (schedules, the blocked-app list, override state, and
 * break history) — minus two prefs files that are tied to this device and
 * would mislead or misfire on another one:
 *
 *  - `nopemode_ringer` holds the persisted [id][com.piercingxx.nopemode.service.RingerPolicy]
 *    of the automatic zen rule this device created. [com.piercingxx.nopemode.service.RingerPolicy.ensureRule]
 *    trusts a persisted id without checking it still exists, so restoring a
 *    stale id from another phone would make Quiet Ringer believe it already
 *    has a rule and never create a real one.
 *  - `nopemode_reconcile` is [com.piercingxx.nopemode.data.ReconcileStatus]:
 *    the last reconcile's failed packages, exact-alarm-degraded flag, and
 *    error message — a diagnostic snapshot of this device's last run, not a
 *    setting. [afterRestore] triggers a fresh reconcile immediately, which
 *    overwrites it anyway.
 *
 * Restore re-arms the schedule the same way [com.piercingxx.nopemode.schedule.BootReceiver]
 * does for [android.content.Intent.ACTION_BOOT_COMPLETED]: no alarm survives
 * a restore any more than it survives a reboot, so [afterRestore] re-derives
 * state and arms the next boundary before the base class ends the process.
 */
class NopeModeBackupProvider : SuiteBackupProvider() {

    override val appName: String get() = "nope-mode"

    override fun contents(): BackupContents {
        val defaults = snapshot.defaultContents()
        return BackupContents(
            prefs = defaults.prefs.filter { it.name !in DEVICE_TIED_PREFS },
            databases = defaults.databases,
            files = emptyList(),
            exports = emptyMap(),
        )
    }

    override fun afterRestore() {
        AlarmScheduler.from(context!!).reconcileAndApply()
    }

    private companion object {
        val DEVICE_TIED_PREFS = setOf("nopemode_ringer.xml", "nopemode_reconcile.xml")
    }
}
