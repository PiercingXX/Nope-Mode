package com.piercingxx.nopemode.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.admin.BlockedMessage
import com.piercingxx.nopemode.admin.NopeDeviceAdminReceiver
import com.piercingxx.nopemode.data.AppStateDao
import com.piercingxx.nopemode.data.BlockedAppDao
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.ScheduleDao
import com.piercingxx.nopemode.data.SettingsStore
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
        // Master switch (design §11). Off means no schedule applies, so the
        // plan derives inactive, everything is released and the alarm is
        // cancelled — all through the normal derived path, not a special case
        // that bypasses the enforcer (D8).
        val enabled = SettingsStore(context).isEnabled()
        val schedules =
            if (enabled) runBlocking { scheduleDao.observeAll().first() } else emptyList()
        // The override has to be dropped too, not just the schedules. An
        // indefinite ForceOn pins the state active on its own (§6), so emptying
        // the schedule list alone leaves the master switch unable to turn
        // anything off — which is exactly how a tile tap became inescapable.
        val override =
            if (enabled) OverrideMapper.toOverride(runBlocking { appStateDao.get() })
            else Override.None
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
        applyBlockedMessage(plan.active, plan.nextBoundary)
        arm(plan)
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