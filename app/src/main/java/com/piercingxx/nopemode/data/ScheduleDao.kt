package com.piercingxx.nopemode.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * T2 — ScheduleDao (design §10, WS2). Persists the active-window schedules.
 *
 * The DAO is Android-dependent (Room) and its *behavior* is deferred to the
 * operator's on-device check; only the pure mappers are JVM-provable.
 */
@Dao
interface ScheduleDao {

    /** All schedules, ordered by start time. */
    @Query("SELECT * FROM schedule ORDER BY startMinuteOfDay ASC")
    fun observeAll(): Flow<List<Schedule>>

    @Upsert
    suspend fun upsert(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)

    @Query("DELETE FROM schedule")
    suspend fun clear()

    @Insert
    suspend fun insertAll(schedules: List<Schedule>)
}