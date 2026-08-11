package com.piercingxx.nopemode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T6 — BackupJson: Gson round-trip of blocked apps, schedules, settings
 * (design §10, WS10). Pure — operates on the plain data classes; no `android.*`
 * at the tested layer.
 *
 * Covers a full round-trip (export → import yields identical state) and rejection
 * of malformed input without corrupting existing state (design §14, WS10).
 */
class BackupJsonTest {

    private val blockedApps = listOf(
        BlockedApp(packageName = "com.example.one", addedAt = 1000L),
        BlockedApp(packageName = "com.example.two", addedAt = 2000L)
    )

    private val schedules = listOf(
        Schedule(id = 1L, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = 0b1111111, enabled = true),
        Schedule(id = 2L, startMinuteOfDay = 540, endMinuteOfDay = 600, daysMask = 0b0000010, enabled = false)
    )

    private val settings = BackupJson.Settings(
        breakDurationMinutes = 5,
        minIntervalMinutes = 60,
        breakBudgetPerWindow = 3
    )

    @Test
    fun `export then import yields identical state`() {
        val json = BackupJson.export(blockedApps, schedules, settings)
        val restored = BackupJson.import(json)

        assertNotNull("round-trip must restore a payload", restored)
        restored!!

        assertEquals("blocked apps round-trip", blockedApps, restored.blockedApps)
        assertEquals("schedules round-trip", schedules, restored.schedules)
        assertEquals("settings round-trip", settings, restored.settings)
        assertEquals("version round-trips", BackupJson.VERSION, restored.version)
    }

    @Test
    fun `round-trip handles empty collections`() {
        val json = BackupJson.export(emptyList(), emptyList(), settings)
        val restored = BackupJson.import(json)

        assertNotNull(restored)
        restored!!
        assertTrue(restored.blockedApps.isEmpty())
        assertTrue(restored.schedules.isEmpty())
        assertEquals(settings, restored.settings)
    }

    @Test
    fun `malformed json is rejected with null`() {
        assertNull(BackupJson.import("this is not json"))
        assertNull(BackupJson.import(""))
        assertNull(BackupJson.import("{ \"unterminated\": "))
    }

    @Test
    fun `json missing required fields is rejected`() {
        // Well-formed JSON but not a valid BackupData payload — must be rejected,
        // not silently accepted with nulls.
        assertNull(BackupJson.import("""{"version": 1}"""))
        assertNull(BackupJson.import("""{"blockedApps": [], "schedules": []}"""))
    }
}