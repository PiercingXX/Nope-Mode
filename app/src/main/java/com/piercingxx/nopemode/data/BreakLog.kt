package com.piercingxx.nopemode.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Log of breaks taken. Used to enforce:
 * - Minimum interval between breaks
 * - Break budget (default 3 per active window)
 */
@Entity(tableName = "break_log")
data class BreakLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val durationMinutes: Int
)
