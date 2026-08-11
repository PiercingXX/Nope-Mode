package com.piercingxx.nopemode.core

import com.piercingxx.nopemode.data.BreakLog
import com.piercingxx.nopemode.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * T3 — BreakPolicy: minimum interval, break budget, budget reset at window end
 * (design §9). Pure, no `android.*` at the tested layer.
 */
class BreakPolicyTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val ALL_DAYS = (1 shl 7) - 1

    // Monday-start 20:00 -> 08:00 midnight-crossing default window.
    private val defaultWindow = Schedule(id = 1, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = ALL_DAYS)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): LocalDateTime = LocalDateTime.of(y, mo, d, h, mi)

    private fun instant(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        at(y, mo, d, h, mi).atZone(zone).toInstant()

    private fun breakLog(y: Int, mo: Int, d: Int, h: Int, mi: Int): BreakLog =
        BreakLog(startedAt = at(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli(), durationMinutes = 5)

    // ---- minimum interval enforcement ----

    @Test
    fun `first break is always allowed`() {
        assertTrue(BreakPolicy.minIntervalElapsed(instant(2026, 8, 10, 21, 0), null))
    }

    @Test
    fun `break within the minimum interval is blocked`() {
        val last = instant(2026, 8, 10, 21, 0)
        // 10 minutes later — under the 30-minute default.
        assertFalse(BreakPolicy.minIntervalElapsed(instant(2026, 8, 10, 21, 10), last))
    }

    @Test
    fun `break exactly at the minimum interval is allowed`() {
        val last = instant(2026, 8, 10, 21, 0)
        // Exactly 30 minutes later.
        assertTrue(BreakPolicy.minIntervalElapsed(instant(2026, 8, 10, 21, 30), last))
    }

    @Test
    fun `break after the minimum interval is allowed`() {
        val last = instant(2026, 8, 10, 21, 0)
        assertTrue(BreakPolicy.minIntervalElapsed(instant(2026, 8, 10, 22, 0), last))
    }

    @Test
    fun `isBreakAllowed respects the minimum interval`() {
        val last = instant(2026, 8, 10, 21, 0)
        // Active window, budget fresh, but only 5 min since the last break.
        val breaks = listOf(breakLog(2026, 8, 10, 21, 0))
        assertFalse(BreakPolicy.isBreakAllowed(at(2026, 8, 10, 21, 5), listOf(defaultWindow), breaks, last))
    }

    // ---- budget exhaustion ----

    @Test
    fun `budget starts full`() {
        assertEquals(3, BreakPolicy.budgetRemaining(at(2026, 8, 10, 21, 0), listOf(defaultWindow), emptyList()))
    }

    @Test
    fun `budget decreases with each break in the window`() {
        val breaks = listOf(
            breakLog(2026, 8, 10, 21, 0),
            breakLog(2026, 8, 10, 22, 0),
            breakLog(2026, 8, 10, 23, 0),
        )
        assertEquals(0, BreakPolicy.budgetRemaining(at(2026, 8, 10, 23, 30), listOf(defaultWindow), breaks))
    }

    @Test
    fun `budget never goes below zero`() {
        val breaks = listOf(
            breakLog(2026, 8, 10, 21, 0),
            breakLog(2026, 8, 10, 22, 0),
            breakLog(2026, 8, 10, 23, 0),
            breakLog(2026, 8, 10, 23, 30),
        )
        assertEquals(0, BreakPolicy.budgetRemaining(at(2026, 8, 10, 23, 45), listOf(defaultWindow), breaks))
    }

    @Test
    fun `exhausted budget blocks a break`() {
        val breaks = listOf(
            breakLog(2026, 8, 10, 21, 0),
            breakLog(2026, 8, 10, 22, 0),
            breakLog(2026, 8, 10, 23, 0),
        )
        // 3 breaks used, budget 0, interval elapsed — still blocked by budget.
        assertFalse(
            BreakPolicy.isBreakAllowed(at(2026, 8, 10, 23, 30), listOf(defaultWindow), breaks, instant(2026, 8, 10, 23, 0))
        )
    }

    @Test
    fun `a break is allowed while budget remains and interval elapsed`() {
        val breaks = listOf(breakLog(2026, 8, 10, 21, 0))
        assertTrue(
            BreakPolicy.isBreakAllowed(at(2026, 8, 10, 22, 0), listOf(defaultWindow), breaks, instant(2026, 8, 10, 21, 0))
        )
    }

    // ---- budget reset at window end ----

    @Test
    fun `budget resets when a new window begins`() {
        // Breaks taken in the Monday-evening window (20:00 -> 08:00).
        val mondayBreaks = listOf(
            breakLog(2026, 8, 10, 21, 0),
            breakLog(2026, 8, 10, 22, 0),
            breakLog(2026, 8, 10, 23, 0),
        )
        // Tuesday evening, 21:00 — a NEW window (Tuesday's 20:00). Budget is fresh.
        assertEquals(3, BreakPolicy.budgetRemaining(at(2026, 8, 11, 21, 0), listOf(defaultWindow), mondayBreaks))
    }

    @Test
    fun `breaks in a previous window do not count against the current one`() {
        val mondayBreaks = listOf(
            breakLog(2026, 8, 10, 21, 0),
            breakLog(2026, 8, 10, 22, 0),
            breakLog(2026, 8, 10, 23, 0),
        )
        // Tuesday 21:30 — new window, 3 old breaks ignored, so a break is allowed.
        assertTrue(
            BreakPolicy.isBreakAllowed(
                at(2026, 8, 11, 21, 30),
                listOf(defaultWindow),
                mondayBreaks,
                instant(2026, 8, 10, 23, 0)
            )
        )
    }

    // ---- inactive ----

    @Test
    fun `no break is allowed while Nope-Mode is inactive`() {
        // Monday 12:00 — outside the window.
        assertFalse(BreakPolicy.isBreakAllowed(at(2026, 8, 10, 12, 0), listOf(defaultWindow), emptyList(), null))
    }

    // ---- friction settings lock ----

    @Test
    fun `friction settings editable only while inactive`() {
        assertTrue(BreakPolicy.canEditFrictionSettings(false))
        assertFalse(BreakPolicy.canEditFrictionSettings(true))
    }

    @Test
    fun `defaults are 30 minute interval and budget 3`() {
        assertEquals(30, BreakPolicy.DEFAULT_MIN_INTERVAL_MINUTES)
        assertEquals(3, BreakPolicy.DEFAULT_BUDGET)
    }
}