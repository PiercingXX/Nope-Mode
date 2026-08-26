package com.piercingxx.nopemode.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.admin.BlockedMessage
import com.piercingxx.nopemode.admin.NopeDeviceAdminReceiver
import com.piercingxx.nopemode.data.AppStateDao
import com.piercingxx.nopemode.data.BlockedAppDao
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.ReconcileStatus
import com.piercingxx.nopemode.data.ScheduleDao
import com.piercingxx.nopemode.data.SettingsStore
import com.piercingxx.nopemode.data.SuspendRecordDao
import com.piercingxx.nopemode.enforce.Enforcer
import com.piercingxx.nopemode.enforce.PackagePruner
import com.piercingxx.nopemode.enforce.SuspendEnforcer
import com.piercingxx.nopemode.schedule.Reconciler.Tier
import com.piercingxx.nopemode.service.BreakNotification
import com.piercingxx.nopemode.service.RingerPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WS6 — the Android wiring of the reconcile loop (design §7.2).
 *
 * Arms exactly ONE exact alarm at a time, at the boundary [AlarmMode] picks
 * from the [Reconciler.Plan]. When the alarm fires (or the device boots) it
 * re-derives state via [Reconciler.reconcile], applies the diff through the
 * injected [Enforcer], and re-arms the next boundary. State is always obtained
 * by re-deriving, never by accumulating alarm events (D8), so a missed, late,
 * or duplicated alarm cannot corrupt state.
 *
 * The tier is chosen from the live device-owner state: a device-owner install
 * can suspend packages ([SuspendEnforcer]); a fallback install cannot and the
 * enforcer must be switched accordingly (T9 wires the fallback tier).
 *
 * Android-dependent — behavior is deferred to the operator's on-device check
 * (design §16). The pure decision slice is [AlarmMode].
 */
class AlarmScheduler private constructor(
    private val context: Context,
    private val scheduleDao: ScheduleDao,
    private val appStateDao: AppStateDao,
    private val blockedAppDao: BlockedAppDao,
    private val suspendRecordDao: SuspendRecordDao,
    private val enforcer: Enforcer,
    private val ringerPolicy: RingerPolicy,
    private val zone: ZoneId,
) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /**
     * Re-derive state, apply the diff, and re-arm the next boundary. Called on
     * every alarm fire and on boot recovery. Does not throw: apply and arm are
     * isolated so a denied exact-alarm grant cannot skip enforcement, and a
     * failed apply cannot skip the next alarm (R8).
     */
    fun reconcileAndApply(now: LocalDateTime = LocalDateTime.now(zone)) {
        val status = ReconcileStatus(context)
        try {
            val enabled = SettingsStore(context).isEnabled()
            val storedOverride = OverrideMapper.toOverride(runBlocking { appStateDao.get() })
            val schedules =
                if (enabled) runBlocking { scheduleDao.observeAll().first() } else emptyList()
            val override = if (enabled) storedOverride else Override.None
            val blockedRaw = runBlocking { blockedAppDao.observeAll().first() }
                .map { it.packageName }.toSet()
            val suspendedRaw = runBlocking { suspendRecordDao.observeAll().first() }
                .map { it.packageName }.toSet()
            val (blocked, currentSuspended) = pruneUninstalled(blockedRaw, suspendedRaw)

            val tier = currentTier()
            val plan = Reconciler.reconcile(now, schedules, override, blocked, currentSuspended, tier)

            val desired = (currentSuspended - plan.toRelease) + plan.toSuspend
            var applyError: String? = null
            val result = runCatching { enforcer.apply(desired) }
                .getOrElse { e ->
                    Log.e(TAG, "enforcer.apply threw", e)
                    applyError = e.message ?: e.javaClass.simpleName
                    Enforcer.Result(failed = desired)
                }
            status.setReconcileError(applyError)
            status.setFailedPackages(result.failed)
            if (!result.success) {
                Log.w(TAG, "enforcement incomplete; failed: ${result.failed}")
            }

            applyRinger(plan.active)
            applyBlockedMessage(plan.active, plan.nextBoundary)
            BreakNotification.sync(context, storedOverride, enabled)
            arm(plan)
        } catch (e: Exception) {
            Log.e(TAG, "reconcileAndApply failed", e)
            status.setReconcileError(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Drop rows whose packages are gone. A failed or empty PackageManager
     * read is treated as "don't know", not "everything uninstalled".
     */
    private fun pruneUninstalled(
        blocked: Set<String>,
        currentSuspended: Set<String>,
    ): Pair<Set<String>, Set<String>> {
        val installed = runCatching {
            context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
        }.getOrElse {
            Log.w(TAG, "could not list installed packages; skip prune", it)
            return blocked to currentSuspended
        }
        if (installed.isEmpty()) return blocked to currentSuspended
        val goneBlocked = PackagePruner.gone(blocked, installed)
        val goneSuspended = PackagePruner.gone(currentSuspended, installed)
        if (goneBlocked.isNotEmpty()) {
            runBlocking { blockedAppDao.deleteByPackages(goneBlocked.toList()) }
        }
        if (goneSuspended.isNotEmpty()) {
            runBlocking { suspendRecordDao.deleteByPackages(goneSuspended.toList()) }
        }
        return (blocked - goneBlocked) to (currentSuspended - goneSuspended)
    }

    /**
     * Put Nope-Mode's own words on the platform's blocked dialog.
     *
     * Opening a suspended app raises Settings' ActionDisabledByAdminDialog,
     * whose look is not ours to change. Its body text is: a device owner's
     * short support message replaces "For more info, contact your IT admin",
     * which is misleading here — there is no IT admin, just a schedule the user
     * set (design §11, BRAND-GUIDE §6).
     */
    private fun applyBlockedMessage(active: Boolean, endsAt: LocalDateTime?) {
        runCatching {
            val admin = NopeDeviceAdminReceiver.componentName(context)
            val message = BlockedMessage.shortSupportMessage(active, endsAt)
            // Both: the short one is what the blocked dialog renders, the long
            // one is what Settings shows on the device-admin detail screen.
            dpm.setShortSupportMessage(admin, message)
            dpm.setLongSupportMessage(admin, message)
        }.onFailure { Log.w(TAG, "could not set support message", it) }
    }

    /**
     * Drive the Quiet Ringer from the same derived `isActive` as everything else
     * (design §18.2) — there is no second scheduler.
     *
     * Inert until the user grants Do Not Disturb access: [RingerPolicy.ensureRule]
     * returns null and nothing is touched. That grant is the opt-in, so this
     * cannot start silencing calls on its own. Home surfaces the ungranted state
     * rather than letting a silently inert Quiet Ringer look active (§18.4, R8).
     */
    private fun applyRinger(active: Boolean) {
        runCatching {
            val settings = SettingsStore(context).load()
            if (!settings.quietRingerEnabled) {
                // Master off (§18.5). Deactivate rather than leave a stale rule
                // active — an off toggle that still silences calls is the exact
                // dishonesty R8 forbids.
                ringerPolicy.setActive(false)
                return@runCatching
            }
            val id = ringerPolicy.ensureRule(
                quietRingerEnabled = true,
                allowRepeatCallers = settings.allowRepeatCallers,
            ) ?: return@runCatching
            ringerPolicy.setActive(active)
            Log.d(TAG, "quiet ringer rule $id active=$active")
        }.onFailure { Log.w(TAG, "quiet ringer update failed", it) }
    }

    private fun currentTier(): Tier =
        if (dpm.isDeviceOwnerApp(context.packageName)) Tier.DEVICE_OWNER else Tier.FALLBACK

    /** Arm a single alarm at the plan's next trigger, or cancel if none. */
    private fun arm(plan: Reconciler.Plan) {
        val pi = BootReceiver.alarmPendingIntent(context)
        val trigger = AlarmMode.nextTrigger(plan, zone)
        if (trigger == null) {
            alarmManager.cancel(pi)
            return
        }
        val millis = trigger.toEpochMilli()
        val exact = canScheduleExact()
        val status = ReconcileStatus(context)
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
            status.setExactAlarmDegraded(!exact)
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm denied; degrading to inexact", e)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }.onFailure { Log.e(TAG, "inexact alarm also failed", it) }
            status.setExactAlarmDegraded(true)
        }
    }

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    companion object {
        private const val TAG = "AlarmScheduler"

        /** Wire a scheduler for the current device-owner tier. */
        fun from(context: Context): AlarmScheduler {
            val appContext = context.applicationContext
            val db = NopeDatabase.get(appContext)
            return AlarmScheduler(
                context = appContext,
                scheduleDao = db.scheduleDao(),
                appStateDao = db.appStateDao(),
                blockedAppDao = db.blockedAppDao(),
                suspendRecordDao = db.suspendRecordDao(),
                enforcer = SuspendEnforcer(appContext, db.suspendRecordDao()),
                ringerPolicy = RingerPolicy(appContext),
                zone = ZoneId.systemDefault(),
            )
        }
    }
}