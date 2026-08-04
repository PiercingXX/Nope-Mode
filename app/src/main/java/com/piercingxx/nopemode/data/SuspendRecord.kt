package com.piercingxx.nopemode.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Crash safety net for suspended packages.
 * 
 * On boot, anything present here but no longer scheduled to be blocked is released.
 * Without this table a crash mid-activation can leave apps permanently suspended
 * with no UI to recover them.
 */
@Entity(tableName = "suspend_record")
data class SuspendRecord(
    @PrimaryKey val packageName: String,
    val suspendedAt: Long = System.currentTimeMillis()
)
