package com.piercingxx.nopemode.ui

import com.piercingxx.nopemode.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS7 — ScheduleText (design §11, §7).
 *
 * Design §11 singles out two things to state explicitly, both because the
 * alternative reading is equally defensible: that a window wraps past midnight,
 * and which day a `daysMask` bit refers to. These cover both.
 */
class ScheduleTextTest {

    private fun schedule(
        start: Int,
        end: Int,
        days: Int = 0b1111111,
        enabled: Boolean = true,
    ) = Schedule(id = 1L, startMinuteOfDay = start, endMinuteOfDay = end, daysMask = days, enabled = enabled)

    @Test
    fun `time formats minute-of-day as a clock time`() {
        assertEquals("20:00", ScheduleText.time(1200))
        assertEquals("08:00", ScheduleText.time(480))
        assertEquals("00:00", ScheduleText.time(0))
        assertEquals("23:59", ScheduleText.time(1439))
    }

    @Test
    fun `time clamps rather than rendering an impossible clock`() {
        assertEquals("23:59", ScheduleText.time(9999))
        assertEquals("00:00", ScheduleText.time(-5))
    }

    @Test
    fun `a window ending at or before its start wraps`() {
        assertTrue("20:00 -> 08:00 crosses midnight", ScheduleText.wraps(schedule(1200, 480)))
        // end == start is the degenerate wrap case design §7 defines as wrapping.
        assertTrue(ScheduleText.wraps(schedule(600, 600)))
        assertFalse("09:00 -> 17:00 is a normal window", ScheduleText.wraps(schedule(540, 1020)))
    }

    @Test
    fun `a wrapping window says so in words`() {
        // The whole point: "20:00 -> 08:00" alone leaves which 08:00 ambiguous.
        assertEquals("20:00 tonight → 08:00 tomorrow", ScheduleText.window(schedule(1200, 480)))
    }

    @Test
    fun `a normal window is not dressed up as a wrap`() {
        assertEquals("09:00 → 17:00", ScheduleText.window(schedule(540, 1020)))
    }

    @Test
    fun `day meaning is stated only when it changes the answer`() {
        val wrapping = ScheduleText.dayMeaning(schedule(1200, 480))
        assertNotNull(wrapping)
        assertTrue("must explain the night it starts", wrapping!!.contains("Friday night"))
        // For a same-day window there is no ambiguity to resolve.
        assertNull(ScheduleText.dayMeaning(schedule(540, 1020)))
    }

    @Test
    fun `day sets read as names or shorthands`() {
        assertEquals("Every day", ScheduleText.days(schedule(1200, 480, 0b1111111)))
        assertEquals("Weekdays", ScheduleText.days(schedule(1200, 480, 0b0011111)))
        assertEquals("Weekends", ScheduleText.days(schedule(1200, 480, 0b1100000)))
        assertEquals("Mon, Wed, Fri", ScheduleText.days(schedule(1200, 480, 0b0010101)))
    }

    @Test
    fun `an empty mask says the schedule never runs`() {
        // Silently showing nothing would read as "every day" to a skimming eye.
        val text = ScheduleText.days(schedule(1200, 480, 0))
        assertTrue("must say it never runs: $text", text.contains("Never"))
    }

    @Test
    fun `toggling a day flips only that bit`() {
        assertEquals(0b1111110, ScheduleText.toggleDay(0b1111111, 0))
        assertEquals(0b1111111, ScheduleText.toggleDay(0b1111110, 0))
        assertEquals(0b0000001, ScheduleText.toggleDay(0, 0))
        assertEquals(0b1000000, ScheduleText.toggleDay(0, 6))
    }

    @Test
    fun `day bits are Monday-first`() {
        assertTrue(ScheduleText.isDaySet(0b0000001, 0))
        assertFalse(ScheduleText.isDaySet(0b0000001, 1))
        assertTrue(ScheduleText.isDaySet(0b1000000, 6))
        assertEquals(7, ScheduleText.DAY_INITIALS.size)
    }

    @Test
    fun `summary carries both the window and the days`() {
        val text = ScheduleText.summary(schedule(1200, 480))
        assertTrue(text.contains("tonight"))
        assertTrue(text.contains("Every day"))
    }
}
