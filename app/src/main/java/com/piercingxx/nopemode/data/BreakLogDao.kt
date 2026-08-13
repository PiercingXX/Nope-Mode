package com.piercingxx.nopemode.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * T2 — BreakLogDao (design §10, WS2). Persists breaks taken, used to enforce
 * the minimum interval between breaks and the break budget per active window.
 */
@Dao
interface BreakLogDao {

    @Insert
    suspend fun insert(log: BreakLog)

    /** Count of breaks started at or after [sinceMillis]. */
    @Query("SELECT COUNT(*) FROM break_log WHERE startedAt >= :sinceMillis")
    suspend fun breaksSince(sinceMillis: Long): Int

    /** The most recent break, or `null` if none has ever been taken. */
    @Query("SELECT * FROM break_log ORDER BY startedAt DESC LIMIT 1")
    suspend fun mostRecent(): BreakLog?
}