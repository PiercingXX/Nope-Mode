package com.piercingxx.nopemode.core

import com.piercingxx.nopemode.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * T4 — NextBoundary: the next instant at which the active/inactive state flips
 * (design §7.2). Pure, no `android.*` at the tested layer.
 *
 * Covers: a boundary inside the current window, the next-day wrap, and a
 * schedule that is currently inactive.
 */
class NextBoundaryTest {

    // Bit 0 = Monday .. bit 6 = Sunday.
    private val MONDAY = 1 shl 0
    private val TUESDAY = 1 shl 1
    private val WEDNESDAY = 1 shl 2
    private val THURSDAY = 1 shl 3
    private val FRIDAY = 1 shl 4
    private val SATURDAY = 1 shl 5
    private val SUNDAY = 1 shl 6
    private val ALL_DAYS = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY or SATURDAY or SUNDAY

    // Monday-start 20:00 -> 08:00 midnight-crossing default window.
    private val defaultWindow = Schedule(id = 1, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = ALL_DAYS)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): LocalDateTime = LocalDateTime.of(y, mo, d, h, mi)

    // ---- boundary inside the current window ----

    @Test
    fun `next flip inside an active window is the window end`() {
        // Monday 23:00 — active. The next flip is the wrap end at Tuesday 08:00.
        assertEquals(
            at(2026, 8, 11, 8, 0),
            NextBoundary.next(at(2026, 8, 10, 23, 0), listOf(defaultWindow))
        )
    }

    @Test
    fun `next flip from the early wrap hours is the wrap end`() {
        // Tuesday 03:00 — still inside the Monday-start wrap. Next flip is 08:00.
        assertEquals(
            at(2026, 8, 11, 8, 0),
            NextBoundary.next(at(2026, 8, 11, 3, 0), listOf(defaultWindow))
        )
    }

    @Test
    fun `normal window next flip is the exclusive end`() {
        // Monday 09:30 inside [09:00, 10:00). Next flip is 10:00.
        val s = Schedule(id = 1, startMinuteOfDay = 540, endMinuteOfDay = 600, daysMask = MONDAY or TUESDAY)
        assertEquals(
            at(2026, 8, 10, 10, 0),
            NextBoundary.next(at(2026, 8, 10, 9, 30), listOf(s))
        )
    }

    // ---- the next-day wrap ----

    @Test
    fun `next flip from an inactive midday is the next window start`() {
        // Tuesday 12:00 — inactive. The next flip is Tuesday 20:00 (window start).
        assertEquals(
            at(2026, 8, 11, 20, 0),
            NextBoundary.next(at(2026, 8, 11, 12, 0), listOf(defaultWindow))
        )
    }

    @Test
    fun `next flip at the exact end boundary is the next start`() {
        // Tuesday 08:00 — exactly the exclusive end. Now inactive; next flip is 20:00.
        assertEquals(
            at(2026, 8, 11, 20, 0),
            NextBoundary.next(at(2026, 8, 11, 8, 0), listOf(defaultWindow))
        )
    }

    @Test
    fun `next flip from a single-day window wraps across the week`() {
        // Monday-only window. At Monday 21:00 (active) the next flip is Tuesday
        // 08:00 (the wrap end); at Tuesday 12:00 (inactive) the next flip is next
        // Monday 20:00.
        val mondayOnly = Schedule(id = 1, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = MONDAY)
        assertEquals(
            at(2026, 8, 11, 8, 0),
            NextBoundary.next(at(2026, 8, 10, 21, 0), listOf(mondayOnly))
        )
        assertEquals(
            at(2026, 8, 17, 20, 0),
            NextBoundary.next(at(2026, 8, 11, 12, 0), listOf(mondayOnly))
        )
    }

    // ---- a schedule that is currently inactive ----

    @Test
    fun `next flip for an inactive schedule is its next start`() {
        // A window active only 09:00-10:00 on Monday. At Monday 12:00 it is
        // inactive; the next flip is next Monday 09:00.
        val s = Schedule(id = 1, startMinuteOfDay = 540, endMinuteOfDay = 600, daysMask = MONDAY)
        assertEquals(
            at(2026, 8, 17, 9, 0),
            NextBoundary.next(at(2026, 8, 10, 12, 0), listOf(s))
        )
    }

    // ---- edge cases ----

    @Test
    fun `no enabled schedules yields null`() {
        assertNull(NextBoundary.next(at(2026, 8, 10, 21, 0), emptyList()))
    }

    @Test
    fun `disabled schedules yield null`() {
        val disabled = defaultWindow.copy(enabled = false)
        assertNull(NextBoundary.next(at(2026, 8, 10, 21, 0), listOf(disabled)))
    }

    @Test
    fun `earliest boundary across overlapping schedules wins`() {
        // Active window 20:00->08:00 plus a short 22:00-23:00 window. At Monday
        // 21:00 the next flip is 22:00 (the short window's start).
        val short = Schedule(id = 2, startMinuteOfDay = 1320, endMinuteOfDay = 1380, daysMask = ALL_DAYS)
        assertEquals(
            at(2026, 8, 10, 22, 0),
            NextBoundary.next(at(2026, 8, 10, 21, 0), listOf(defaultWindow, short))
        )
    }
}