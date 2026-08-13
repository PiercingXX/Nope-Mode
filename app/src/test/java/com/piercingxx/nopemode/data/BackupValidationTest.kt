package com.piercingxx.nopemode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T10 — BackupValidationTest (design §14, WS10): the pure gate between a parsed
 * backup payload and a commit to the live database. This is the task's verify.
 *
 * [BackupJson.import] guarantees structural well-formedness; [BackupValidator]
 * rejects payloads that are structurally valid but semantically impossible, so a
 * bad file can never partially overwrite existing state. Covers the happy path
 * and each rejection rule in isolation.
 */
class BackupValidationTest {

    private val blockedApps = listOf(
        BlockedApp(packageName = "com.example.one", addedAt = 1000L)
    )

    private val schedules = listOf(
        Schedule(id = 1L, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = 0b1111111, enabled = true)
    )

    private val settings = BackupJson.Settings(
        breakDurationMinutes = 5,
        minIntervalMinutes = 60,
        breakBudgetPerWindow = 3
    )

    private fun payload(
        blocked: List<BlockedApp> = blockedApps,
        sched: List<Schedule> = schedules,
        set: BackupJson.Settings = settings
    ) = BackupJson.BackupData(
        version = BackupJson.VERSION,
        blockedApps = blocked,
        schedules = sched,
        settings = set
    )

    @Test
    fun `a well-formed payload is valid`() {
        val result = BackupValidator.validate(payload())
        assertTrue("payload must be valid", result.valid)
        assertTrue("valid payload has no errors", result.errors.isEmpty())
    }

    @Test
    fun `empty collections are valid`() {
        val result = BackupValidator.validate(payload(blocked = emptyList(), sched = emptyList()))
        assertTrue(result.valid)
    }

    @Test
    fun `blank blocked-app package name is rejected`() {
        val bad = payload(blocked = listOf(BlockedApp(packageName = "  ", addedAt = 1L)))
        val result = BackupValidator.validate(bad)
        assertFalse("blank package name must fail validation", result.valid)
        assertTrue(result.errors.any { it.contains("blank package name") })
    }

    @Test
    fun `schedule start minute out of range is rejected`() {
        val bad = payload(sched = listOf(Schedule(id = 1L, startMinuteOfDay = 1440, endMinuteOfDay = 480, daysMask = 1)))
        val result = BackupValidator.validate(bad)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("start minute") })
    }

    @Test
    fun `schedule end minute out of range is rejected`() {
        val bad = payload(sched = listOf(Schedule(id = 1L, startMinuteOfDay = 0, endMinuteOfDay = -1, daysMask = 1)))
        val result = BackupValidator.validate(bad)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("end minute") })
    }

    @Test
    fun `schedule days mask out of range is rejected`() {
        val bad = payload(sched = listOf(Schedule(id = 1L, startMinuteOfDay = 0, endMinuteOfDay = 480, daysMask = 0b10000000)))
        val result = BackupValidator.validate(bad)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("days mask") })
    }

    @Test
    fun `midnight-crossing schedule is valid`() {
        // end <= start is a legal midnight-crossing window (design §10).
        val result = BackupValidator.validate(payload(sched = listOf(Schedule(id = 1L, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = 1))))
        assertTrue(result.valid)
    }

    @Test
    fun `non-positive break duration is rejected`() {
        val bad = payload(set = settings.copy(breakDurationMinutes = 0))
        val result = BackupValidator.validate(bad)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("breakDurationMinutes") })
    }

    @Test
    fun `non-positive min interval is rejected`() {
        val bad = payload(set = settings.copy(minIntervalMinutes = -5))
        val result = BackupValidator.validate(bad)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("minIntervalMinutes") })
    }

    @Test
    fun `non-positive break budget is rejected`() {
        val bad = payload(set = settings.copy(breakBudgetPerWindow = 0))
        val result = BackupValidator.validate(bad)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("breakBudgetPerWindow") })
    }

    @Test
    fun `multiple violations are all reported`() {
        val bad = payload(
            blocked = listOf(BlockedApp(packageName = "", addedAt = 1L)),
            sched = listOf(Schedule(id = 1L, startMinuteOfDay = 9999, endMinuteOfDay = 0, daysMask = 1)),
            set = settings.copy(minIntervalMinutes = 0)
        )
        val result = BackupValidator.validate(bad)
        assertFalse(result.valid)
        assertEquals(3, result.errors.size)
    }
}