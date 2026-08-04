package com.piercingxx.nopemode.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-selected app to block.
 */
@Entity(tableName = "blocked_app")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val addedAt: Long = System.currentTimeMillis()
)
