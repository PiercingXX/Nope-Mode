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

    private fun payload(blockedApps: String, schedules: String): String = """
        {
          "version": 1,
          "blockedApps": $blockedApps,
          "schedules": $schedules,
          "settings": {
            "breakDurationMinutes": 5,
            "minIntervalMinutes": 60,
            "breakBudgetPerWindow": 3
          }
        }
    """.trimIndent()

    @Test
    fun `a null element inside a list rejects the whole payload`() {
        // The dangerous case: parseable JSON whose array holds a literal null.
        // Dropping the bad element would silently restore a partial backup.
        assertNull(BackupJson.import(payload("[null]", "[]")))
        assertNull(BackupJson.import(payload("[]", "[null]")))
    }

    @Test
    fun `an element missing a field rejects the whole payload`() {
        assertNull(BackupJson.import(payload("""[{"addedAt": 1000}]""", "[]")))
        assertNull(BackupJson.import(payload("""[{"packageName": "com.example.one"}]""", "[]")))
        // Schedule missing endMinuteOfDay: would otherwise default to 0 and
        // become a real, wrong window rather than a rejected file.
        assertNull(
            BackupJson.import(
                payload(
                    "[]",
                    """[{"id": 1, "startMinuteOfDay": 1200, "daysMask": 127, "enabled": true}]"""
                )
            )
        )
    }

    @Test
    fun `settings missing a field rejects the whole payload`() {
        val json = """
            {
              "version": 1,
              "blockedApps": [],
              "schedules": [],
              "settings": { "breakDurationMinutes": 5 }
            }
        """.trimIndent()
        assertNull(BackupJson.import(json))
    }

    @Test
    fun `a fully specified payload is accepted`() {
        // Guards the tests above: they must fail for the missing field, not
        // because the hand-written payload shape is wrong.
        val restored = BackupJson.import(
            payload(
                """[{"packageName": "com.example.one", "addedAt": 1000}]""",
                """[{"id": 1, "startMinuteOfDay": 1200, "endMinuteOfDay": 480, "daysMask": 127, "enabled": true}]"""
            )
        )

        assertNotNull(restored)
        restored!!
        assertEquals(listOf(BlockedApp("com.example.one", 1000L)), restored.blockedApps)
        assertEquals(
            listOf(Schedule(1L, 1200, 480, 0b1111111, true)),
            restored.schedules
        )
        assertEquals(settings, restored.settings)
    }
}