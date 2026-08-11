package com.piercingxx.nopemode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5 — SeedSchedule: the pure seed-schedule factory (design §10, §7). Pure — a
 * Room entity is a plain data class and instantiates fine on the JVM; no
 * `android.*` at the tested layer.
 *
 * Asserts the exact default values: start 20:00, end 08:00, all seven days set,
 * enabled.
 */
class SeedScheduleTest {

    @Test
    fun `default schedule starts at 20 00`() {
        assertEquals(1200, SeedSchedule.defaultSchedule().startMinuteOfDay)
    }

    @Test
    fun `default schedule ends at 08 00`() {
        assertEquals(480, SeedSchedule.defaultSchedule().endMinuteOfDay)
    }

    @Test
    fun `default schedule is a midnight-crossing window`() {
        val s = SeedSchedule.defaultSchedule()
        assertTrue("end (08:00) must be <= start (20:00) for the wrap", s.endMinuteOfDay <= s.startMinuteOfDay)
    }

    @Test
    fun `default schedule has all seven days set`() {
        assertEquals(SeedSchedule.ALL_DAYS_MASK, SeedSchedule.defaultSchedule().daysMask)
        // Sanity: every day bit 0..6 is set.
        assertEquals(0b1111111, SeedSchedule.defaultSchedule().daysMask)
    }

    @Test
    fun `default schedule is enabled`() {
        assertTrue(SeedSchedule.defaultSchedule().enabled)
    }
}