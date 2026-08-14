package com.piercingxx.nopemode.ui

import com.piercingxx.nopemode.data.Schedule

/**
 * WS7 — how a schedule reads on screen (design §11, §7). Pure, so it is
 * JVM-provable.
 *
 * Design §11 asks for two things specifically, both because they are ambiguous
 * and both readings are defensible:
 *
 *  - **Show the wrap explicitly.** A window whose end is at or before its start
 *    crosses midnight, so "20:00 → 08:00" alone is genuinely unclear about
 *    which 08:00 is meant. It is spelled out as "tonight → tomorrow".
 *  - **State which day the mask refers to.** `daysMask` is keyed to the day the
 *    window STARTS. For a wrapping window that means checking Friday gives you
 *    Friday night into Saturday morning — not Friday morning.
 */
object ScheduleText {

    /** Bit 0 = Monday .. bit 6 = Sunday, matching [Schedule.daysMask]. */
    private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** Single-letter chips, in the same bit order. */
    val DAY_INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")

    /** "20:00" from 1200. */
    fun time(minuteOfDay: Int): String {
        val safe = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    /** True when the window crosses midnight (design §7: `end <= start`). */
    fun wraps(schedule: Schedule): Boolean = schedule.endMinuteOfDay <= schedule.startMinuteOfDay

    /**
     * The window line. A wrapping window says so in words rather than leaving
     * the reader to infer it from the numbers.
     */
    fun window(schedule: Schedule): String {
        val start = time(schedule.startMinuteOfDay)
        val end = time(schedule.endMinuteOfDay)
        return if (wraps(schedule)) {
            "$start tonight → $end tomorrow"
        } else {
            "$start → $end"
        }
    }

    /** "Every day", "Weekdays", "Mon, Wed, Fri", or "Never" for an empty mask. */
    fun days(schedule: Schedule): String {
        val set = DAY_LABELS.indices.filter { isDaySet(schedule.daysMask, it) }
        return when {
            set.isEmpty() -> "Never — no days selected"
            set.size == 7 -> "Every day"
            set == listOf(0, 1, 2, 3, 4) -> "Weekdays"
            set == listOf(5, 6) -> "Weekends"
            else -> set.joinToString(", ") { DAY_LABELS[it] }
        }
    }

    /**
     * The clarification about what a day means for this schedule. Only worth
     * saying for a wrapping window, where it actually changes the answer.
     */
    fun dayMeaning(schedule: Schedule): String? =
        if (wraps(schedule)) {
            "Days are the night it starts — Fri means Friday night into Saturday."
        } else {
            null
        }

    /** The full summary line for a row in the list. */
    fun summary(schedule: Schedule): String = "${window(schedule)}  ·  ${days(schedule)}"

    fun isDaySet(daysMask: Int, dayIndex: Int): Boolean = (daysMask and (1 shl dayIndex)) != 0

    fun toggleDay(daysMask: Int, dayIndex: Int): Int = daysMask xor (1 shl dayIndex)
}
