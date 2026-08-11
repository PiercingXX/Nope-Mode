package com.piercingxx.nopemode.core

import com.piercingxx.nopemode.data.Schedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * T1 — ScheduleEvaluator: shouldBeActiveAt with midnight-crossing windows.
 *
 * Covers: normal window, midnight-crossing window (the shipped default),
 * exact boundary minutes 20:00:00 and 08:00:00, day-mask boundaries across the
 * wrap, and DST spring-forward / fall-back inside a window (design §14).
 */
class ScheduleEvaluatorTest {

    // Bit 0 = Monday .. bit 6 = Sunday.
    private val MONDAY = 1 shl 0
    private val TUESDAY = 1 shl 1
    private val WEDNESDAY = 1 shl 2
    private val THURSDAY = 1 shl 3
    private val FRIDAY = 1 shl 4
    private val SATURDAY = 1 shl 5
    private val SUNDAY = 1 shl 6
    private val ALL_DAYS = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY or SATURDAY or SUNDAY

    private val defaultWindow = Schedule(id = 1, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = ALL_DAYS)

    // ---- normal window (end > start) ----

    @Test
    fun `normal window is active inside and inactive outside`() {
        val s = Schedule(id = 1, startMinuteOfDay = 540, endMinuteOfDay = 600, daysMask = MONDAY or TUESDAY)
        // Monday 09:30 is inside [09:00, 10:00).
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 9, 30), listOf(s)))
        // Monday 10:00 is the exclusive end boundary.
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 10, 0), listOf(s)))
        // Monday 08:59 is before start.
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 8, 59), listOf(s)))
        // Wednesday (bit not set) is inactive even inside the window.
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 12, 9, 30), listOf(s)))
    }

    // ---- midnight-crossing window (the default) ----

    @Test
    fun `midnight-crossing window is active before and after midnight`() {
        // Monday 21:00 — inside [20:00, 24:00) of a Monday-start window.
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 21, 0), listOf(defaultWindow)))
        // Tuesday 03:00 — inside [00:00, 08:00) of the Monday-start wrap.
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 11, 3, 0), listOf(defaultWindow)))
        // Tuesday 12:00 — outside the window entirely.
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 11, 12, 0), listOf(defaultWindow)))
    }

    // ---- exact boundary minutes ----

    @Test
    fun `start boundary 20-00-00 is inclusive`() {
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 20, 0), listOf(defaultWindow)))
        // One minute before is inactive.
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 19, 59), listOf(defaultWindow)))
    }

    @Test
    fun `end boundary 08-00-00 is exclusive`() {
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 11, 7, 59), listOf(defaultWindow)))
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 11, 8, 0), listOf(defaultWindow)))
    }

    // ---- day-mask boundaries across the wrap ----

    @Test
    fun `wrap belongs to the day the window started, not the day it ends on`() {
        // Window starts Monday only: active Monday 21:00 and Tuesday 03:00
        // (the early hours of Tuesday belong to the Monday-start window).
        val mondayOnly = Schedule(id = 1, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = MONDAY)
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 21, 0), listOf(mondayOnly)))  // Mon
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 11, 3, 0), listOf(mondayOnly)))   // Tue 03:00 -> Mon wrap
        // Tuesday 21:00 is not covered (Tuesday bit not set).
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 11, 21, 0), listOf(mondayOnly)))
        // Wednesday 03:00 is not covered (only Monday-start windows wrap into Tuesday).
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 12, 3, 0), listOf(mondayOnly)))
    }

    @Test
    fun `wrap across the week boundary from Sunday to Monday`() {
        // Sunday-start window wraps into Monday's early hours.
        val sundayOnly = Schedule(id = 1, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = SUNDAY)
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 9, 21, 0), listOf(sundayOnly)))   // Sun
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 3, 0), listOf(sundayOnly)))   // Mon 03:00 -> Sun wrap
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 21, 0), listOf(sundayOnly))) // Mon 21:00 not covered
    }

    // ---- DST inside a window ----

    @Test
    fun `DST spring-forward inside the window does not change the decision`() {
        // US spring-forward 2026: 2:00 AM Sunday March 8 (clocks jump to 3:00).
        // The window 20:00->08:00 is active throughout the transition.
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 3, 8, 1, 59), listOf(defaultWindow)))
        // 03:00 is wall-clock after the jump; still inside the window's early segment.
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 3, 8, 3, 0), listOf(defaultWindow)))
        // 08:00 remains the exclusive end boundary.
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 3, 8, 8, 0), listOf(defaultWindow)))
    }

    @Test
    fun `DST fall-back inside the window does not change the decision`() {
        // US fall-back 2026: 2:00 AM Sunday November 1 (clocks fall back to 1:00).
        // The window 20:00->08:00 is active throughout.
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 11, 1, 1, 30), listOf(defaultWindow)))
        // 03:00 after the fall-back; still active.
        assertTrue(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 11, 1, 3, 0), listOf(defaultWindow)))
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 11, 1, 8, 0), listOf(defaultWindow)))
    }

    // ---- disabled schedules and empty list ----

    @Test
    fun `disabled schedule is never active`() {
        val disabled = defaultWindow.copy(enabled = false)
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 21, 0), listOf(disabled)))
    }

    @Test
    fun `empty schedule list is inactive`() {
        assertFalse(ScheduleEvaluator.shouldBeActiveAt(LocalDateTime.of(2026, 8, 10, 21, 0), emptyList()))
    }

    @Test
    fun `any enabled schedule being active makes the state active`() {
        val inactive = Schedule(id = 1, startMinuteOfDay = 540, endMinuteOfDay = 600, daysMask = MONDAY)
        assertTrue(
            ScheduleEvaluator.shouldBeActiveAt(
                LocalDateTime.of(2026, 8, 10, 21, 0),
                listOf(inactive, defaultWindow)
            )
        )
    }
}