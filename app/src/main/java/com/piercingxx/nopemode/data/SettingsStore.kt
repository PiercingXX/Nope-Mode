package com.piercingxx.nopemode.data

import android.content.Context
import com.piercingxx.nopemode.core.FrictionSettings

/**
 * Persistence for [FrictionSettings] (design §9, §18.5).
 *
 * SharedPreferences rather than a Room table on purpose: these are five scalar
 * user preferences with no relations and no history, and adding a table would
 * mean a schema migration for something `getInt` already does. `RingerPolicy`
 * already keeps its rule id the same way.
 *
 * Writes are guarded by [FrictionSettings] semantics, not by this class — §9
 * says friction settings are editable only while Nope-Mode is INACTIVE, and the
 * caller (the Settings screen) is what knows whether it is. Enforcing it here
 * would also block a restore-from-backup mid-window.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The master switch (design §11). Off means Nope-Mode enforces nothing at
     * all, whatever the schedule says.
     *
     * This is deliberately NOT a friction setting and is NOT locked while
     * active. Design §9 locks the frictions so the 2am workaround cannot be
     * "raise the budget" — but a product with no off switch is a trap, not a
     * discipline tool, and the schedule window is exactly when a user is most
     * likely to need out. Focus Mode can be turned off too.
     */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * The background preset (BRAND-GUIDE.md §3.3), keyed by the same names
     * PiercingXX-Launcher uses so the two apps agree on what "graphite" means.
     */
    fun backgroundPreset(): String =
        prefs.getString(KEY_BG_PRESET, null) ?: "amoled"

    fun setBackgroundPreset(preset: String) {
        prefs.edit().putString(KEY_BG_PRESET, preset).apply()
    }

    fun load(): FrictionSettings {
        val defaults = FrictionSettings()
        return FrictionSettings(
            breakDurationMinutes = prefs.getInt(KEY_BREAK_MINUTES, defaults.breakDurationMinutes),
            minIntervalMinutes = prefs.getInt(KEY_MIN_INTERVAL, defaults.minIntervalMinutes),
            breakBudgetPerWindow = prefs.getInt(KEY_BUDGET, defaults.breakBudgetPerWindow),
            quietRingerEnabled = prefs.getBoolean(KEY_QUIET_RINGER, defaults.quietRingerEnabled),
            allowRepeatCallers = prefs.getBoolean(KEY_REPEAT_CALLERS, defaults.allowRepeatCallers),
        )
    }

    fun save(settings: FrictionSettings) {
        prefs.edit()
            .putInt(KEY_BREAK_MINUTES, settings.breakDurationMinutes)
            .putInt(KEY_MIN_INTERVAL, settings.minIntervalMinutes)
            .putInt(KEY_BUDGET, settings.breakBudgetPerWindow)
            .putBoolean(KEY_QUIET_RINGER, settings.quietRingerEnabled)
            .putBoolean(KEY_REPEAT_CALLERS, settings.allowRepeatCallers)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "nopemode_settings"
        const val KEY_BREAK_MINUTES = "break_minutes"
        const val KEY_MIN_INTERVAL = "min_interval_minutes"
        const val KEY_BUDGET = "break_budget"
        const val KEY_QUIET_RINGER = "quiet_ringer_enabled"
        const val KEY_REPEAT_CALLERS = "allow_repeat_callers"
        const val KEY_ENABLED = "master_enabled"
        const val KEY_BG_PRESET = "background_preset"
    }
}
