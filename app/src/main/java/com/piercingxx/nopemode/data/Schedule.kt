package com.piercingxx.nopemode.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A schedule for when Nope-Mode should be active.
 * 
 * Windows cross midnight: end <= start means the window wraps into the next day.
 * daysMask refers to the day the window STARTS (bit 0 = Monday .. bit 6 = Sunday).
 */
@Entity(tableName = "schedule")
data class Schedule(
    @PrimaryKey val id: Long,
    val startMinuteOfDay: Int,   // 20:00 -> 1200, 08:00 -> 480
    val endMinuteOfDay: Int,     // can be <= start for midnight-crossing
    val daysMask: Int,           // bit 0 = Monday .. bit 6 = Sunday
    val enabled: Boolean = true
)
