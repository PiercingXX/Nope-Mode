package com.piercingxx.nopemode.data

/**
 * T10 — BackupValidator (design §14, WS10): the pure gate between a parsed
 * backup payload and a commit to the live database.
 *
 * [BackupJson.import] guarantees a payload is structurally well-formed (every
 * required field present, valid JSON). This validator goes one step further and
 * rejects payloads that are structurally valid but semantically impossible —
 * out-of-range schedule minutes, an unknown days mask, a non-positive friction
 * setting. A payload that fails validation must never overwrite existing state
 * (design §14): the caller commits only when [validate] returns a valid result.
 *
 * Pure — no `android.*` at this layer, so it is JVM-provable and covered by
 * [BackupValidationTest].
 */
object BackupValidator {

    /** The verdict on a candidate payload. */
    data class Result(
        val valid: Boolean,
        val errors: List<String> = emptyList()
    ) {
        companion object {
            fun ok() = Result(valid = true)

            fun invalid(vararg errors: String) = Result(valid = false, errors = errors.toList())
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60 // 1440
    private const val DAYS_MASK_ALL = 0b1111111 // bit 0 = Mon .. bit 6 = Sun

    /**
     * Validate a whole payload. Returns a valid result only when every blocked
     * app, schedule and setting is within its legal domain.
     */
    fun validate(data: BackupJson.BackupData): Result {
        val errors = mutableListOf<String>()

        if (data.version > BackupJson.VERSION) {
            errors += "backup version ${data.version} is newer than this app"
        }
        if (data.version < 1) {
            errors += "backup version ${data.version} is invalid"
        }

        data.blockedApps.forEach { app ->
            if (app.packageName.isBlank()) {
                errors += "blocked app has a blank package name"
            }
        }

        data.schedules.forEach { s ->
            if (s.startMinuteOfDay !in 0 until MINUTES_PER_DAY) {
                errors += "schedule ${s.id} start minute out of range: ${s.startMinuteOfDay}"
            }
            if (s.endMinuteOfDay !in 0 until MINUTES_PER_DAY) {
                errors += "schedule ${s.id} end minute out of range: ${s.endMinuteOfDay}"
            }
            if (s.daysMask !in 0..DAYS_MASK_ALL) {
                errors += "schedule ${s.id} days mask out of range: ${s.daysMask}"
            }
        }

        val settings = data.settings
        if (settings.breakDurationMinutes <= 0) {
            errors += "breakDurationMinutes must be positive"
        }
        if (settings.minIntervalMinutes <= 0) {
            errors += "minIntervalMinutes must be positive"
        }
        if (settings.breakBudgetPerWindow <= 0) {
            errors += "breakBudgetPerWindow must be positive"
        }

        return if (errors.isEmpty()) Result.ok() else Result(valid = false, errors = errors)
    }
}