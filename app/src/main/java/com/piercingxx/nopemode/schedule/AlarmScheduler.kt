package com.piercingxx.nopemode.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.piercingxx.nopemode.data.AppStateDao
import com.piercingxx.nopemode.data.BlockedAppDao
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.ScheduleDao
import com.piercingxx.nopemode.data.SuspendRecordDao
import com.piercingxx.nopemode.enforce.Enforcer
import com.piercingxx.nopemode.enforce.SuspendEnforcer
import com.piercingxx.nopemode.schedule.Reconciler.Tier
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
     * every alarm fire and on boot recovery.
     */
    fun reconcileAndApply(now: LocalDateTime = LocalDateTime.now(zone)) {
        val schedules = runBlocking { scheduleDao.observeAll().first() }
        val override = OverrideMapper.toOverride(runBlocking { appStateDao.get() })
        val blocked = runBlocking { blockedAppDao.observeAll().first() }
            .map { it.packageName }.toSet()
        val currentSuspended = runBlocking { suspendRecordDao.observeAll().first() }
            .map { it.packageName }.toSet()

        val tier = currentTier()
        val plan = Reconciler.reconcile(now, schedules, override, blocked, currentSuspended, tier)

        // The enforcer's apply() takes the full desired suspended set and diffs
        // it against its own record; reconstruct it from the plan's diff.
        val desired = (currentSuspended - plan.toRelease) + plan.toSuspend
        val result = enforcer.apply(desired)
        if (!result.success) {
            Log.w(TAG, "enforcement incomplete; failed: ${result.failed}")
        }

        applyRinger(plan.active)
        arm(plan)
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
            val id = ringerPolicy.ensureRule(
                // TODO(WS7c): thread both from the Settings screen (design §18.5)
                // once friction settings are persisted. Repeat callers default on.
                quietRingerEnabled = true,
                allowRepeatCallers = true,
            ) ?: return@runCatching
            ringerPolicy.setActive(active)
            Log.d(TAG, "quiet ringer rule $id active=$active")
        }.onFailure { Log.w(TAG, "quiet ringer update failed", it) }
    }

    private fun currentTier(): Tier =
        if (dpm.isDeviceOwnerApp(context.packageName)) Tier.DEVICE_OWNER else Tier.FALLBACK

    /** Arm a single exact alarm at the plan's next trigger, or cancel if none. */
    private fun arm(plan: Reconciler.Plan) {
        val pi = BootReceiver.alarmPendingIntent(context)
        val trigger = AlarmMode.nextTrigger(plan, zone)
        if (trigger == null) {
            alarmManager.cancel(pi)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            trigger.toEpochMilli(),
            pi,
        )
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