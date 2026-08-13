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

    /**
     * Parse-time mirror of [BackupData] with every field nullable.
     *
     * Gson populates fields reflectively and does not honour Kotlin's null
     * safety or default values: a missing key leaves a declared-non-null field
     * holding `null`, and a JSON array may contain literal `null` elements.
     * Parsing into this DTO first makes those states expressible, so the
     * validation below is a check the compiler can see rather than one it
     * flags as always-false.
     *
     * Every field is required. The exporter always writes all of them, so a
     * payload missing any key did not come from [export] intact.
     */
    private data class BackupDto(
        val version: Int?,
        val blockedApps: List<BlockedAppDto?>?,
        val schedules: List<ScheduleDto?>?,
        val settings: SettingsDto?
    )

    private data class BlockedAppDto(val packageName: String?, val addedAt: Long?)

    private data class ScheduleDto(
        val id: Long?,
        val startMinuteOfDay: Int?,
        val endMinuteOfDay: Int?,
        val daysMask: Int?,
        val enabled: Boolean?
    )

    private data class SettingsDto(
        val breakDurationMinutes: Int?,
        val minIntervalMinutes: Int?,
        val breakBudgetPerWindow: Int?
    )

    private fun BlockedAppDto.toEntity(): BlockedApp? {
        val name = packageName ?: return null
        val added = addedAt ?: return null
        return BlockedApp(packageName = name, addedAt = added)
    }

    private fun ScheduleDto.toEntity(): Schedule? {
        return Schedule(
            id = id ?: return null,
            startMinuteOfDay = startMinuteOfDay ?: return null,
            endMinuteOfDay = endMinuteOfDay ?: return null,
            daysMask = daysMask ?: return null,
            enabled = enabled ?: return null
        )
    }

    private fun SettingsDto.toValue(): Settings? {
        return Settings(
            breakDurationMinutes = breakDurationMinutes ?: return null,
            minIntervalMinutes = minIntervalMinutes ?: return null,
            breakBudgetPerWindow = breakBudgetPerWindow ?: return null
        )
    }

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
    fun import(json: String): BackupData? {
        val dto = try {
            gson.fromJson(json, BackupDto::class.java)
        } catch (_: JsonParseException) {
            null
        } ?: return null

        val version = dto.version ?: return null
        val settings = dto.settings?.toValue() ?: return null
        // mapNotNull would silently drop a bad element; a single null must
        // reject the whole payload, so map and bail on the first one.
        val blockedApps = dto.blockedApps?.map { it?.toEntity() ?: return null } ?: return null
        val schedules = dto.schedules?.map { it?.toEntity() ?: return null } ?: return null

        return BackupData(
            version = version,
            blockedApps = blockedApps,
            schedules = schedules,
            settings = settings
        )
    }
}