package com.piercingxx.nopemode.core

import com.piercingxx.nopemode.data.Schedule
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The pure computation behind `AlarmScheduler`'s "next boundary only; recompute
 * and re-arm after each fire" rule (design §7.2). Pure — no `android.*` at this
 * layer.
 *
 * Given derived active state, [next] returns the next instant at which the
 * active/inactive state flips. The overall state is piecewise-constant and only
 * changes at a schedule boundary (a window start or end), so the next flip is
 * simply the earliest schedule boundary strictly after [now].
 *
 * Windows where `end <= start` wrap into the next day; `daysMask` refers to the
 * day the window STARTS (bit 0 = Monday .. bit 6 = Sunday), matching
 * [ScheduleEvaluator].
 */
object NextBoundary {

    /**
     * The next instant strictly after [now] at which the active/inactive state
     * flips, or null when no enabled schedule produces a boundary within the
     * search horizon (e.g. no enabled schedules at all).
     *
     * The horizon covers a full week of start-days plus the wrap into the next
     * day, so a single-day-per-week schedule's next start is always found.
     */
    fun next(now: LocalDateTime, schedules: List<Schedule>): LocalDateTime? {
        var earliest: LocalDateTime? = null
        val startDay = now.toLocalDate()
        // Start one day back so a midnight-crossing window that began YESTERDAY
        // and wraps into today contributes its end boundary on today (that is
        // the active window's next flip when `now` is in its early hours). 8
        // further start-days covers any weekly cadence (a Monday-only window's
        // next start is at most 7 days away); the wrap end of the last start-day
        // lands on day +8, still within the horizon.
        for (offset in -1..8) {
            val day = startDay.plusDays(offset.toLong())
            for (schedule in schedules) {
                if (!schedule.enabled) continue
                val start = schedule.startMinuteOfDay
                val end = schedule.endMinuteOfDay
                if (end > start) {
                    if (isDaySet(schedule.daysMask, day)) {
                        earliest = earlier(earliest, at(day, start), now)
                        earliest = earlier(earliest, at(day, end), now)
                    }
                } else {
                    if (isDaySet(schedule.daysMask, day)) {
                        earliest = earlier(earliest, at(day, start), now)
                        // The wrap ends on the FOLLOWING day at `end`.
                        earliest = earlier(earliest, at(day.plusDays(1), end), now)
                    }
                }
            }
        }
        return earliest
    }

    private fun at(day: LocalDate, minuteOfDay: Int): LocalDateTime =
        day.atTime(minuteOfDay / 60, minuteOfDay % 60)

    /**
     * Returns the earlier of [a] and [b], but only considers a boundary strictly
     * after [now] (a boundary at or before now is not a future flip). A past or
     * present boundary is never chosen over a future one.
     */
    private fun earlier(a: LocalDateTime?, b: LocalDateTime, now: LocalDateTime): LocalDateTime? {
        if (!b.isAfter(now)) return a
        if (a == null) return b
        return if (b.isBefore(a)) b else a
    }

    private fun isDaySet(daysMask: Int, day: LocalDate): Boolean =
        (daysMask and (1 shl (day.dayOfWeek.value - 1))) != 0
}