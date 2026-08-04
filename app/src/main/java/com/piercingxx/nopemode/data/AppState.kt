package com.piercingxx.nopemode.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * The current override state (if any).
 * 
 * State is derived, never accumulated — a pure function of (now, schedules, override).
 */
@Entity(tableName = "app_state")
data class AppState(
    @PrimaryKey val id: Long = 1,
    val overrideKind: String?,      // "NONE", "FORCE_ON", "BREAK"
    val overrideUntil: Long?,       // Unix timestamp, null for indefinite ForceOn
    val lastReconcileAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val NONE = "NONE"
        const val FORCE_ON = "FORCE_ON"
        const val BREAK = "BREAK"
    }
}
