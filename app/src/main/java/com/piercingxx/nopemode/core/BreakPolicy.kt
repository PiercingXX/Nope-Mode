package com.piercingxx.nopemode.core

import com.piercingxx.nopemode.data.BreakLog
import com.piercingxx.nopemode.data.Schedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Anti-bypass rules for breaks (design §9). Pure — no `android.*` at this layer.
 *
 * Two frictions, both configurable and both default on:
 *  - **Minimum interval between breaks** (default 30 min) — prevents chaining
 *    5-minute breaks into an unbounded evening.
 *  - **Break budget** (default 3 per active window) — exhausted means exhausted.
 *
 * The budget is scoped to the CURRENT active window and resets when the window
 * ends: a fresh window starts with a full budget regardless of how many breaks
 * were taken before it began.
 *
 * Every friction setting is editable only while Nope-Mode is INACTIVE (§9) —
 * otherwise the 2am workaround is simply to raise the break budget.
 */
object BreakPolicy {

    /** Default minimum interval between break starts, in minutes. */
    val DEFAULT_MIN_INTERVAL_MINUTES: Int = 30

    /** Default number of breaks allowed per active window. */
    val DEFAULT_BUDGET: Int = 3

    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * True when the minimum interval since [lastBreakStartedAt] has elapsed, so a
     * new break may start. A null last break (never taken one) is always allowed;
     * a [now] before the last break (clock moved backwards) is not.
     */
    fun minIntervalElapsed(
        now: Instant,
        lastBreakStartedAt: Instant?,
        minIntervalMinutes: Int = DEFAULT_MIN_INTERVAL_MINUTES,
    ): Boolean {
        if (lastBreakStartedAt == null) return true
        val elapsed = Duration.between(lastBreakStartedAt, now)
        return !elapsed.isNegative && elapsed >= Duration.ofMinutes(minIntervalMinutes.toLong())
    }

    /**
     * Remaining break budget in the current active window. When Nope-Mode is
     * inactive there is no active window to consume budget, so the full budget
     * is available. [isActive] covers ForceOn outside a schedule: budget then
     * counts from local midnight rather than refusing the break entirely.
     */
    fun budgetRemaining(
        now: LocalDateTime,
        schedules: List<Schedule>,
        breaks: List<BreakLog>,
        budget: Int = DEFAULT_BUDGET,
        isActive: Boolean = currentWindowStart(now, schedules) != null,
    ): Int {
        val windowStart = currentWindowStart(now, schedules)
        val startMillis = when {
            windowStart != null -> windowStart.atZone(zone).toInstant().toEpochMilli()
            isActive -> now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            else -> return budget
        }
        val used = breaks.count { it.startedAt >= startMillis }
        return (budget - used).coerceAtLeast(0)
    }

    /**
     * True when a break may be taken right now: Nope-Mode must be active (there
     * is something to take a break from), the minimum interval must have elapsed,
     * and the budget must not be exhausted.
     *
     * [isActive] is derived state (master on AND schedule/ForceOn). Window start
     * alone refuses a daytime ForceOn, which is still something to break from.
     */
    fun isBreakAllowed(
        now: LocalDateTime,
        schedules: List<Schedule>,
        breaks: List<BreakLog>,
        lastBreakStartedAt: Instant?,
        settings: FrictionSettings = FrictionSettings(),
        isActive: Boolean = currentWindowStart(now, schedules) != null,
    ): Boolean {
        if (!isActive) return false
        if (budgetRemaining(now, schedules, breaks, settings.breakBudgetPerWindow, isActive) <= 0) {
            return false
        }
        return minIntervalElapsed(
            now.atZone(zone).toInstant(),
            lastBreakStartedAt,
            settings.minIntervalMinutes,
        )
    }

    /**
     * Why a break cannot be taken, or null when it can. The UI needs the reason,
     * not just the boolean: "no breaks left until 08:00" is actionable, a greyed
     * button with no explanation is just confusing (R8).
     */
    fun breakBlockedReason(
        now: LocalDateTime,
        schedules: List<Schedule>,
        breaks: List<BreakLog>,
        lastBreakStartedAt: Instant?,
        settings: FrictionSettings = FrictionSettings(),
        isActive: Boolean = currentWindowStart(now, schedules) != null,
    ): String? {
        if (!isActive) {
            return "Nope-Mode is not active — there is nothing to take a break from."
        }
        val remaining = budgetRemaining(
            now, schedules, breaks, settings.breakBudgetPerWindow, isActive,
        )
        if (remaining <= 0) {
            return "No breaks left in this window. The budget resets when the window ends."
        }
        val nowInstant = now.atZone(zone).toInstant()
        if (!minIntervalElapsed(nowInstant, lastBreakStartedAt, settings.minIntervalMinutes)) {
            val waited = Duration.between(lastBreakStartedAt, nowInstant).toMinutes()
            val left = (settings.minIntervalMinutes - waited).coerceAtLeast(0)
            return "Too soon since the last break — $left min to wait."
        }
        return null
    }

    /**
     * Friction settings are editable only while Nope-Mode is INACTIVE (§9).
     */
    fun canEditFrictionSettings(isActive: Boolean): Boolean = !isActive

    /**
     * The start of the active window containing [now], or null when inactive.
     * Handles both normal (`end > start`) and midnight-crossing (`end <= start`)
     * windows; `daysMask` refers to the day the window STARTS.
     */
    private fun currentWindowStart(now: LocalDateTime, schedules: List<Schedule>): LocalDateTime? {
        val minute = now.hour * 60 + now.minute
        val day = now.toLocalDate()
        for (schedule in schedules) {
            if (!schedule.enabled) continue
            val start = schedule.startMinuteOfDay
            val end = schedule.endMinuteOfDay
            if (end > start) {
                if (minute in start until end && isDaySet(schedule.daysMask, dayOfWeekIndex(day))) {
                    return day.atTime(start / 60, start % 60)
                }
            } else {
                if (minute >= start && isDaySet(schedule.daysMask, dayOfWeekIndex(day))) {
                    return day.atTime(start / 60, start % 60)
                }
                if (minute < end && isDaySet(schedule.daysMask, dayOfWeekIndex(day.minusDays(1)))) {
                    return day.minusDays(1).atTime(start / 60, start % 60)
                }
            }
        }
        return null
    }

    private fun dayOfWeekIndex(day: LocalDate): Int = day.dayOfWeek.value - 1 // 0 = Monday .. 6 = Sunday

    private fun isDaySet(daysMask: Int, dayIndex: Int): Boolean = (daysMask and (1 shl dayIndex)) != 0
}