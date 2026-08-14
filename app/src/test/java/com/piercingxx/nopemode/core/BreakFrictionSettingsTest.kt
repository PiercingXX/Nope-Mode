package com.piercingxx.nopemode.core

import com.piercingxx.nopemode.data.BreakLog
import com.piercingxx.nopemode.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WS7 — the frictions are now user-configurable (design §9), which the old
 * hardcoded constants made impossible. These cover the settings actually being
 * honoured, and the refusal reasons the UI shows instead of a dead button.
 */
class BreakFrictionSettingsTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** The shipped default: 20:00 -> 08:00, every day. */
    private val nightly = listOf(
        Schedule(id = 1L, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = 0b1111111, enabled = true)
    )

    /** 22:00 on a Thursday — inside the window. */
    private val insideWindow: LocalDateTime = LocalDateTime.of(2026, 8, 13, 22, 0)

    /** 12:00 on a Thursday — outside it. */
    private val outsideWindow: LocalDateTime = LocalDateTime.of(2026, 8, 13, 12, 0)

    private fun breakAt(at: LocalDateTime, minutes: Int = 5) =
        BreakLog(startedAt = at.atZone(zone).toInstant().toEpochMilli(), durationMinutes = minutes)

    @Test
    fun `a custom budget is honoured, not the default`() {
        val breaks = listOf(breakAt(insideWindow.minusHours(1)))
        // One break taken. Default budget 3 would leave 2; a budget of 1 leaves 0.
        assertEquals(2, BreakPolicy.budgetRemaining(insideWindow, nightly, breaks, budget = 3))
        assertEquals(0, BreakPolicy.budgetRemaining(insideWindow, nightly, breaks, budget = 1))
    }

    @Test
    fun `a custom minimum interval is honoured`() {
        val now = insideWindow.atZone(zone).toInstant()
        val tenMinutesAgo = now.minus(Duration.ofMinutes(10))

        assertFalse(
            "30 min interval must still block at 10 min",
            BreakPolicy.minIntervalElapsed(now, tenMinutesAgo, minIntervalMinutes = 30)
        )
        assertTrue(
            "a 5 min interval must allow it",
            BreakPolicy.minIntervalElapsed(now, tenMinutesAgo, minIntervalMinutes = 5)
        )
    }

    @Test
    fun `a zero budget refuses every break`() {
        val settings = FrictionSettings(breakBudgetPerWindow = 0)
        assertFalse(
            BreakPolicy.isBreakAllowed(insideWindow, nightly, emptyList(), null, settings)
        )
    }

    @Test
    fun `refusal names the exhausted budget`() {
        val settings = FrictionSettings(breakBudgetPerWindow = 1)
        val breaks = listOf(breakAt(insideWindow.minusHours(1)))
        val reason = BreakPolicy.breakBlockedReason(insideWindow, nightly, breaks, null, settings)
        assertNotNull(reason)
        assertTrue("must mention the budget: $reason", reason!!.contains("No breaks left"))
    }

    @Test
    fun `refusal names the wait when the interval has not elapsed`() {
        val settings = FrictionSettings(minIntervalMinutes = 30)
        val lastBreak = insideWindow.minusMinutes(10)
        val reason = BreakPolicy.breakBlockedReason(
            insideWindow,
            nightly,
            listOf(breakAt(lastBreak)),
            lastBreak.atZone(zone).toInstant(),
            settings,
        )
        assertNotNull(reason)
        // 30 - 10 = 20 minutes left to wait.
        assertTrue("must name the remaining wait: $reason", reason!!.contains("20 min"))
    }

    @Test
    fun `refusal explains that an inactive Nope-Mode has nothing to break from`() {
        val reason = BreakPolicy.breakBlockedReason(outsideWindow, nightly, emptyList(), null)
        assertNotNull(reason)
        assertTrue("must say it is inactive: $reason", reason!!.contains("not active"))
    }

    @Test
    fun `no reason is given when a break is genuinely allowed`() {
        assertNull(BreakPolicy.breakBlockedReason(insideWindow, nightly, emptyList(), null))
        assertTrue(BreakPolicy.isBreakAllowed(insideWindow, nightly, emptyList(), null))
    }

    @Test
    fun `defaults match the documented design values`() {
        val defaults = FrictionSettings()
        assertEquals(5, defaults.breakDurationMinutes)
        assertEquals(30, defaults.minIntervalMinutes)
        assertEquals(3, defaults.breakBudgetPerWindow)
        // §18.5: both default on, because missing a real emergency is worse
        // than one unwanted ring.
        assertTrue(defaults.quietRingerEnabled)
        assertTrue(defaults.allowRepeatCallers)
    }

    @Test
    fun `the offered break lengths mirror Focus Mode`() {
        assertEquals(listOf(5, 15, 30), FrictionSettings.BREAK_CHOICES)
    }

    @Test
    fun `friction settings are locked while active`() {
        assertFalse(BreakPolicy.canEditFrictionSettings(isActive = true))
        assertTrue(BreakPolicy.canEditFrictionSettings(isActive = false))
    }
}
