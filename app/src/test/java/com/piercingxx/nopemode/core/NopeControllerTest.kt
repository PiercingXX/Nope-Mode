package com.piercingxx.nopemode.core

import com.piercingxx.nopemode.data.Schedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * T2 — NopeController.derive: the full §6 override truth table, plus the
 * clock-moved-backwards guard (design §14).
 */
class NopeControllerTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val ALL_DAYS = (1 shl 7) - 1

    // A Monday-start 20:00 -> 08:00 midnight-crossing window (the default).
    private val defaultWindow = Schedule(id = 1, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = ALL_DAYS)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): LocalDateTime = LocalDateTime.of(y, mo, d, h, mi)

    private fun instant(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        at(y, mo, d, h, mi).atZone(zone).toInstant()

    // ---- None: the schedule alone decides ----

    @Test
    fun `None with inactive schedule is inactive`() {
        // Monday 12:00 — outside the 20:00->08:00 window.
        assertFalse(NopeController.derive(at(2026, 8, 10, 12, 0), listOf(defaultWindow), Override.None))
    }

    @Test
    fun `None with active schedule is active`() {
        // Monday 21:00 — inside the window.
        assertTrue(NopeController.derive(at(2026, 8, 10, 21, 0), listOf(defaultWindow), Override.None))
    }

    // ---- ForceOn(null): active indefinitely ----

    @Test
    fun `ForceOn indefinite is active regardless of schedule`() {
        // Schedule inactive, but ForceOn(null) forces active.
        assertTrue(NopeController.derive(at(2026, 8, 10, 12, 0), listOf(defaultWindow), Override.ForceOn(null)))
        // Schedule active, ForceOn(null) still active.
        assertTrue(NopeController.derive(at(2026, 8, 10, 21, 0), listOf(defaultWindow), Override.ForceOn(null)))
    }

    // ---- ForceOn(t): active while now < t ----

    @Test
    fun `ForceOn before expiry is active`() {
        val until = instant(2026, 8, 10, 22, 0)
        assertTrue(NopeController.derive(at(2026, 8, 10, 21, 0), listOf(defaultWindow), Override.ForceOn(until)))
    }

    @Test
    fun `ForceOn at expiry falls back to schedule`() {
        val until = instant(2026, 8, 10, 22, 0)
        // now == until: ForceOn expired. Schedule at 22:00 Monday is still active
        // (inside 20:00->08:00 wrap), so result is active via schedule.
        assertTrue(NopeController.derive(at(2026, 8, 10, 22, 0), listOf(defaultWindow), Override.ForceOn(until)))
        // now > until and schedule inactive -> inactive.
        assertFalse(NopeController.derive(at(2026, 8, 10, 12, 0), listOf(defaultWindow), Override.ForceOn(instant(2026, 8, 10, 11, 0))))
    }

    @Test
    fun `ForceOn expiry with inactive schedule is inactive`() {
        // ForceOn expired and schedule inactive at noon.
        val until = instant(2026, 8, 10, 11, 0)
        assertFalse(NopeController.derive(at(2026, 8, 10, 12, 0), listOf(defaultWindow), Override.ForceOn(until)))
    }

    // ---- Break(t): inactive while break running ----

    @Test
    fun `Break running keeps Nope-Mode inactive even when schedule is active`() {
        // Monday 21:00 schedule active, but a break until 21:20 (within the 30-min
        // max) holds it off.
        val until = instant(2026, 8, 10, 21, 20)
        assertFalse(NopeController.derive(at(2026, 8, 10, 21, 0), listOf(defaultWindow), Override.Break(until)))
    }

    // ---- Break(t): expired -> auto-resume from schedule ----

    @Test
    fun `Break expired auto-resumes from schedule`() {
        // Break until 21:30, now 21:45 (schedule still active) -> active.
        val until = instant(2026, 8, 10, 21, 30)
        assertTrue(NopeController.derive(at(2026, 8, 10, 21, 45), listOf(defaultWindow), Override.Break(until)))
    }

    @Test
    fun `Break expired with schedule over stays inactive`() {
        // Break until 07:30, now 12:00 (schedule inactive) -> inactive.
        val until = instant(2026, 8, 11, 7, 30)
        assertFalse(NopeController.derive(at(2026, 8, 11, 12, 0), listOf(defaultWindow), Override.Break(until)))
    }

    // ---- clock-moved-backwards guard (design §14) ----

    @Test
    fun `Break too far in the future is cancelled by the clock guard`() {
        // A Break.until more than MAX_BREAK_DURATION ahead of now is implausible
        // (clock moved backwards). The break is cancelled and the schedule decides.
        val implausiblyFar = instant(2026, 8, 10, 23, 0) // 2h ahead of 21:00
        // Schedule active at 21:00 -> active (break cancelled).
        assertTrue(NopeController.derive(at(2026, 8, 10, 21, 0), listOf(defaultWindow), Override.Break(implausiblyFar)))
    }

    @Test
    fun `Break within max duration is respected`() {
        // A Break 20 minutes ahead is plausible and must hold the break.
        val withinMax = instant(2026, 8, 10, 21, 20) // 20 min ahead of 21:00
        assertFalse(NopeController.derive(at(2026, 8, 10, 21, 0), listOf(defaultWindow), Override.Break(withinMax)))
        // A break exactly at the max boundary (30 min) is still plausible.
        val atBoundary = instant(2026, 8, 10, 21, 30)
        assertFalse(NopeController.derive(at(2026, 8, 10, 21, 0), listOf(defaultWindow), Override.Break(atBoundary)))
    }

    @Test
    fun `clock guard cancels a break even when schedule is inactive`() {
        // Break implausibly far in the future at noon (schedule inactive) -> inactive.
        val implausiblyFar = instant(2026, 8, 10, 14, 0) // 2h ahead of 12:00
        assertFalse(NopeController.derive(at(2026, 8, 10, 12, 0), listOf(defaultWindow), Override.Break(implausiblyFar)))
    }

    @Test
    fun `MAX_BREAK_DURATION is 30 minutes`() {
        assertTrue(NopeController.MAX_BREAK_DURATION == Duration.ofMinutes(30))
    }
}