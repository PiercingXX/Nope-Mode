package com.piercingxx.nopemode.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * T2 — BlockedAppDao (design §10, WS2). Persists the user's blocked-app list.
 *
 * The DAO is Android-dependent (Room) and its *behavior* is deferred to the
 * operator's on-device check; only the pure mappers are JVM-provable.
 */
@Dao
interface BlockedAppDao {

    /** All blocked apps, ordered by when they were added (oldest first). */
    @Query("SELECT * FROM blocked_app ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<BlockedApp>>

    @Insert
    suspend fun insert(app: BlockedApp)

    @Delete
    suspend fun delete(app: BlockedApp)

    @Query("DELETE FROM blocked_app WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}