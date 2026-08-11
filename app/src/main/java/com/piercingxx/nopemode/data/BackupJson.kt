package com.piercingxx.nopemode.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException

/**
 * T6 — BackupJson: serializes and restores blocked apps, schedules, and settings
 * as Gson JSON (design §10, WS10).
 *
 * Pure — operates on the plain Room data classes (`BlockedApp`, `Schedule`) and a
 * plain `Settings` value; no `android.*` at the tested layer, so it is
 * JVM-provable.
 *
 * `import` rejects malformed input by returning `null` (design §14, WS10) — the
 * caller commits to the database only when a non-null value comes back, so a bad
 * file can never partially overwrite existing state. The payload is parsed into
 * a fresh object and validated as a whole before anything is returned.
 */
object BackupJson {

    /** The current backup payload version. Bump on incompatible shape changes. */
    const val VERSION: Int = 1

    /**
     * User-editable friction settings (design §11.4). Locked while active (§9);
     * this is the pure value type the backup round-trips.
     */
    data class Settings(
        val breakDurationMinutes: Int,
        val minIntervalMinutes: Int,
        val breakBudgetPerWindow: Int
    )

    /**
     * The complete backup payload: everything the app persists that a user
     * would want to carry to a fresh install.
     */
    data class BackupData(
        val version: Int = VERSION,
        val blockedApps: List<BlockedApp>,
        val schedules: List<Schedule>,
        val settings: Settings
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Serialize the current state to a JSON string. */
    fun export(
        blockedApps: List<BlockedApp>,
        schedules: List<Schedule>,
        settings: Settings
    ): String = gson.toJson(
        BackupData(
            version = VERSION,
            blockedApps = blockedApps,
            schedules = schedules,
            settings = settings
        )
    )

    /**
     * Parse a JSON string back into a [BackupData], or `null` if the input is
     * malformed. Never throws for caller input; a malformed payload is rejected
     * wholesale so existing state is left untouched.
     */
    fun import(json: String): BackupData? = try {
        val parsed = gson.fromJson(json, BackupData::class.java)
        if (parsed == null || parsed.schedules == null || parsed.blockedApps == null || parsed.settings == null) {
            null
        } else {
            parsed
        }
    } catch (_: JsonParseException) {
        null
    }
}