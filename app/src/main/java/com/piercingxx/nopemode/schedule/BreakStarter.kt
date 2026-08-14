package com.piercingxx.nopemode.schedule

import android.content.Context
import com.piercingxx.nopemode.core.BreakPolicy
import com.piercingxx.nopemode.core.FrictionSettings
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.data.BreakLog
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * WS7 — "Take a break", the last piece of Focus Mode parity (design §1, §9).
 *
 * Design §1 records exactly what was taken from Focus Mode's behaviour: pick
 * distracting apps, pause them, schedule it, and offer a **bounded** break.
 * The first three worked; this is the fourth.
 *
 * Bounded is the whole point (D7): [Override.Break] carries an instant, and
 * there is no way to express "off until I say so". The break is written as an
 * override and logged, then reconcile applies it — nothing here touches the
 * enforcer directly (D8).
 *
 * Every friction check goes through [BreakPolicy], which is pure and tested;
 * this class only does the Android-side reading and writing.
 */
object BreakStarter {

    /** The outcome of asking for a break. */
    sealed interface Result {
        data class Started(val until: Instant) : Result

        /** Refused, with the reason to show the user rather than a dead button. */
        data class Refused(val reason: String) : Result
    }

    /**
     * Start a break of [minutes], if the frictions allow it.
     *
     * Re-checks the policy here rather than trusting the caller's earlier
     * check: the button may have been enabled a minute ago and the budget may
     * have been spent since. The check that matters is the one at the moment of
     * the write.
     */
    fun start(context: Context, minutes: Int, now: LocalDateTime = LocalDateTime.now()): Result {
        val db = NopeDatabase.get(context.applicationContext)
        val settings = SettingsStore(context).load()

        val schedules = runBlocking { db.scheduleDao().observeAll().first() }
        val recent = runBlocking { db.breakLogDao().mostRecent() }
        val lastStartedAt = recent?.let { Instant.ofEpochMilli(it.startedAt) }
        val breaks = runBlocking { recentBreaks(db) }

        val blocked = BreakPolicy.breakBlockedReason(now, schedules, breaks, lastStartedAt, settings)
        if (blocked != null) return Result.Refused(blocked)

        val startedAt = Instant.now()
        val until = startedAt.plus(Duration.ofMinutes(minutes.toLong()))

        runBlocking {
            db.breakLogDao().insert(BreakLog(startedAt = startedAt.toEpochMilli(), durationMinutes = minutes))
            db.appStateDao().upsert(OverrideMapper.toAppState(Override.Break(until)))
        }

        // Design §7.1: starting a break is a reconcile trigger. Reconcile also
        // arms the alarm at the break's expiry, which is what makes it bounded
        // in practice rather than only on paper.
        AlarmScheduler.from(context).reconcileAndApply()
        return Result.Started(until)
    }

    /** Clear a break early — the user changing their mind is not a bypass. */
    fun end(context: Context) {
        val db = NopeDatabase.get(context.applicationContext)
        runBlocking { db.appStateDao().upsert(OverrideMapper.toAppState(Override.None)) }
        AlarmScheduler.from(context).reconcileAndApply()
    }

    /** The break log rows the budget count needs (this window's, generously). */
    private suspend fun recentBreaks(db: NopeDatabase): List<BreakLog> {
        // A window is at most 24h, so anything older cannot be in it.
        val since = Instant.now().minus(Duration.ofHours(24)).toEpochMilli()
        return db.breakLogDao().since(since)
    }

    /**
     * Whether a break may be taken right now, and why not if it may not.
     * Used to label the Home button honestly instead of just disabling it.
     */
    fun blockedReason(
        context: Context,
        now: LocalDateTime = LocalDateTime.now(),
        settings: FrictionSettings = SettingsStore(context).load(),
    ): String? {
        val db = NopeDatabase.get(context.applicationContext)
        val schedules = runBlocking { db.scheduleDao().observeAll().first() }
        val recent = runBlocking { db.breakLogDao().mostRecent() }
        val breaks = runBlocking { recentBreaks(db) }
        return BreakPolicy.breakBlockedReason(
            now,
            schedules,
            breaks,
            recent?.let { Instant.ofEpochMilli(it.startedAt) },
            settings,
        )
    }
}
