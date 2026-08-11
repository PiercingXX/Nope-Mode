package com.piercingxx.nopemode.core

import com.piercingxx.nopemode.data.Schedule
import java.time.Duration
import java.time.LocalDateTime

/**
 * Derives the active/inactive decision from the schedule and any override
 * (design §6). Pure — no `android.*` at this layer.
 *
 * State is always obtained by calling [derive], never by accumulating events
 * (D8); a missed, late, or duplicated alarm cannot corrupt state.
 */
object NopeController {

    /**
     * The longest break a user can take. The UI offers 5 / 15 / 30 minutes
     * (design §9); 30 minutes is the maximum, so any `Break.until` further in
     * the future than this is the clock-moved-backwards guard firing (design
     * §14): the break is cancelled and the schedule decides.
     */
    val MAX_BREAK_DURATION: Duration = Duration.ofMinutes(30)

    /**
     * The full §6 truth table:
     *
     * | Override | Schedule | Result |
     * |---|---|---|
     * | `None` | inactive | inactive |
     * | `None` | active | **active** |
     * | `ForceOn(null)` | either | **active**, indefinitely |
     * | `ForceOn(t)`, now < t | either | **active** |
     * | `ForceOn(t)`, now ≥ t | — | expires → re-derive from schedule |
     * | `Break(t)`, now < t | active | inactive (break running) |
     * | `Break(t)`, now ≥ t | active | **active** — break expired, auto-resume |
     *
     * plus the clock-moved-backwards guard: a `Break.until` in the future by
     * more than [MAX_BREAK_DURATION] cancels the break (design §14).
     */
    fun derive(now: LocalDateTime, schedules: List<Schedule>, override: Override): Boolean {
        val scheduleActive = ScheduleEvaluator.shouldBeActiveAt(now, schedules)
        val nowInstant = now.atZone(java.time.ZoneId.systemDefault()).toInstant()
        return when (override) {
            is Override.ForceOn -> {
                if (override.until == null) true
                else if (nowInstant < override.until) true
                else scheduleActive // expired -> re-derive from schedule
            }
            is Override.Break -> {
                if (nowInstant < override.until) {
                    // Break still running — unless the clock moved backwards and
                    // the break is implausibly far in the future.
                    if (Duration.between(nowInstant, override.until) > MAX_BREAK_DURATION) {
                        scheduleActive
                    } else {
                        false
                    }
                } else {
                    // Break expired: auto-resume from the schedule.
                    scheduleActive
                }
            }
            Override.None -> scheduleActive
        }
    }
}