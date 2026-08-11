package com.piercingxx.nopemode.core

import com.piercingxx.nopemode.data.Schedule
import java.time.LocalDateTime

/**
 * Pure schedule evaluation — the heart of the app (design §7.1, §3, §5).
 *
 * State is always obtained by calling [shouldBeActiveAt], never by accumulating
 * alarm events (D8). A missed, late, or duplicated alarm cannot corrupt state.
 *
 * Windows where `end <= start` wrap into the next day; the shipped default
 * (20:00 -> 08:00, i.e. 1200 -> 480) is the common path, not an edge case.
 * `daysMask` refers to the day the window STARTS (bit 0 = Monday .. bit 6 =
 * Sunday). DST is handled implicitly: we compare minute-of-day on local wall
 * clock time, so a spring-forward or fall-back inside a window changes the
 * wall-clock duration but not the active/inactive decision (design §14).
 */
object ScheduleEvaluator {

    /**
     * True if any enabled schedule is active at [now].
     *
     * A window is active on a given day when `daysMask` has the bit set for the
     * day the window STARTS. For a normal window (`end > start`) that is the
     * day [now] falls on. For a midnight-crossing window (`end <= start`) the
     * wrap means the window also covers the early hours of the FOLLOWING day,
     * which belong to the day whose start-day bit was set.
     */
    fun shouldBeActiveAt(now: LocalDateTime, schedules: List<Schedule>): Boolean {
        val minute = now.hour * 60 + now.minute
        val dayOfWeek = now.dayOfWeek.value - 1 // 0 = Monday .. 6 = Sunday
        val previousDayOfWeek = (dayOfWeek + 6) % 7

        for (schedule in schedules) {
            if (!schedule.enabled) continue
            val start = schedule.startMinuteOfDay
            val end = schedule.endMinuteOfDay
            if (end > start) {
                // Normal window: [start, end) on the current day.
                if (minute in start until end && isDaySet(schedule.daysMask, dayOfWeek)) {
                    return true
                }
            } else {
                // Midnight-crossing window: [start, 24:00) on the start day,
                // plus [00:00, end) on the following day (which belongs to the
                // day whose start-day bit was set).
                if (minute >= start && isDaySet(schedule.daysMask, dayOfWeek)) {
                    return true
                }
                if (minute < end && isDaySet(schedule.daysMask, previousDayOfWeek)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isDaySet(daysMask: Int, dayIndex: Int): Boolean {
        return (daysMask and (1 shl dayIndex)) != 0
    }
}