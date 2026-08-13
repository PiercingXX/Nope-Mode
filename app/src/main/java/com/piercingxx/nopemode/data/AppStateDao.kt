package com.piercingxx.nopemode.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * T2 — AppStateDao (design §10, WS2). Persists the single override row.
 *
 * The table holds exactly one row (`id = 1`). A `null` read — a fresh install
 * with no row yet — maps to `Override.None` rather than crashing (see
 * [OverrideMapper]).
 */
@Dao
interface AppStateDao {

    /** Observe the single override row; `null` when none has been written yet. */
    @Query("SELECT * FROM app_state WHERE id = 1")
    fun observe(): Flow<AppState?>

    /** Read the single override row synchronously; `null` on a fresh install. */
    @Query("SELECT * FROM app_state WHERE id = 1")
    suspend fun get(): AppState?

    @Upsert
    suspend fun upsert(state: AppState)
}