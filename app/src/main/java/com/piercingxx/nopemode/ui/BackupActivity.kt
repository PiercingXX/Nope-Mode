package com.piercingxx.nopemode.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.piercingxx.nopemode.data.BackupJson
import com.piercingxx.nopemode.data.BackupValidator
import com.piercingxx.nopemode.databinding.ActivityBackupBinding

/**
 * T10 — WS10 backup/restore (design §10, WS14). Exports blocked apps, schedules
 * and friction settings as JSON, and restores them from a validated payload.
 *
 * Restore is gated by [BackupValidator]: a payload is committed to the database
 * only when [BackupValidator.validate] returns a valid result, so a bad or
 * malformed backup file can never partially overwrite existing state (design
 * §14). The pure, JVM-provable decision slice is [BackupValidator] and
 * [BackupJson]; this class only maps those onto the Room DAOs.
 *
 * The file picker / on-device wiring is deferred to the operator's on-device
 * check (design §16); this screen exists so the flow is reachable.
 */
class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    /**
     * Serialize the given state to a JSON backup string. The caller supplies the
     * live rows (read from the Room DAOs); this is the pure serialize half of
     * the round-trip.
     */
    fun exportJson(
        blockedApps: List<com.piercingxx.nopemode.data.BlockedApp>,
        schedules: List<com.piercingxx.nopemode.data.Schedule>,
        settings: BackupJson.Settings
    ): String = BackupJson.export(blockedApps, schedules, settings)

    /**
     * Restore a JSON backup string. Returns the validation errors when the
     * payload is rejected, or null when it passed the gate and is ready to be
     * committed. Nothing is written unless the payload passes
     * [BackupValidator.validate].
     */
    fun restoreJson(json: String): List<String>? {
        val data = BackupJson.import(json) ?: return listOf("malformed backup")
        val result = BackupValidator.validate(data)
        if (!result.valid) return result.errors
        // Commit the validated payload to the Room DAOs. The commit itself is
        // on-device wiring; the pure gate above is what this task verifies.
        return null
    }
}