package com.piercingxx.nopemode.backup

import com.piercingxx.suite.backup.BackupContents
import com.piercingxx.suite.backup.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Round-trips a snapshot the shape [NopeModeBackupProvider] builds: the
 * user-settings prefs and `nope-mode.db` (schedules, blocked-app list),
 * with the two device-tied prefs files ([NopeModeBackupProvider]'s doc
 * comment explains why) left out of the archive entirely. Exercises
 * [Snapshot] directly, as BK-1 asks — the provider itself needs a live
 * Android context that this module's plain JUnit tests don't have.
 */
class NopeModeBackupProviderTest {

    @Test
    fun `snapshot captures settings prefs and the schedules db, excludes device-tied prefs, then restores them`() {
        val dataDir = Files.createTempDirectory("nopemode-data").toFile()
        val prefsDir = File(dataDir, "shared_prefs").apply { mkdirs() }
        val dbDir = File(dataDir, "databases").apply { mkdirs() }

        // A user setting that should travel...
        File(prefsDir, "nopemode_settings.xml")
            .writeText("<map><int name=\"break_minutes\" value=\"5\" /></map>")
        // ...and two device-tied files that must not (a stale zen rule id, or
        // a stale reconcile-diagnostic, would misfire on another phone).
        File(prefsDir, "nopemode_ringer.xml")
            .writeText("<map><string name=\"zen_rule_id\">stale-rule-from-old-phone</string></map>")
        File(prefsDir, "nopemode_reconcile.xml")
            .writeText("<map><boolean name=\"exact_alarm_degraded\" value=\"true\" /></map>")
        // The schedules / blocked-app-list database. No real SQLite header on
        // purpose, so Snapshot.checkpoint's isSqlite() check skips it and the
        // plain-JVM test never touches the (stubbed) android SQLiteDatabase.
        File(dbDir, "nope-mode.db").writeBytes(ByteArray(1024) { 3 })

        val snap = Snapshot(dataDir)
        val defaults = snap.defaultContents()
        assertEquals(
            listOf("nopemode_reconcile.xml", "nopemode_ringer.xml", "nopemode_settings.xml"),
            defaults.prefs.map { it.name }.sorted(),
        )

        // Mirrors NopeModeBackupProvider.contents(): defaults minus the
        // device-tied prefs.
        val deviceTied = setOf("nopemode_ringer.xml", "nopemode_reconcile.xml")
        val contents = BackupContents(
            prefs = defaults.prefs.filter { it.name !in deviceTied },
            databases = defaults.databases,
            files = emptyList(),
            exports = emptyMap(),
        )
        assertEquals(listOf("nopemode_settings.xml"), contents.prefs.map { it.name })
        assertEquals(listOf("nope-mode.db"), contents.databases.map { it.name })

        val archive = File(dataDir.parentFile, "nopemode-snap.tar.gz")
        val sha = snap.build(contents, "{\"schema\":1}", archive)
        assertEquals(64, sha.length)
        assertTrue(archive.length() > 0)

        // Wreck the data dir like a fresh phone before restoring: the setting
        // is gone, a stale ringer rule id from *this* device sits where the
        // archive should never touch it, and the db is gone.
        File(prefsDir, "nopemode_settings.xml").delete()
        File(prefsDir, "nopemode_ringer.xml").writeText("<map><string name=\"zen_rule_id\">this-devices-own-rule</string></map>")
        File(dbDir, "nope-mode.db").delete()

        val meta = snap.apply(archive) { _, _ -> error("no exports expected") }

        assertEquals("{\"schema\":1}", meta)
        assertTrue(
            "the setting came back",
            File(prefsDir, "nopemode_settings.xml").readText().contains("break_minutes"),
        )
        assertEquals(1024, File(dbDir, "nope-mode.db").length().toInt())
        assertFalse(
            "prefs are replaced wholesale: the archive never carried a ringer " +
                "file, so this device's own (pre-restore) one is gone too, not left behind",
            File(prefsDir, "nopemode_ringer.xml").exists(),
        )
    }
}
