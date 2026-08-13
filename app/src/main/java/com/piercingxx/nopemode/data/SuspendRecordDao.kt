package com.piercingxx.nopemode.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * T2 — SuspendRecordDao (design §10, WS2). Crash safety net for suspended
 * packages (design §5).
 *
 * On boot, anything present here but no longer scheduled to be blocked is
 * released. Without this table a crash mid-activation can leave apps
 * permanently suspended with no UI to recover them.
 */
@Dao
interface SuspendRecordDao {

    /** Every package currently recorded as suspended. */
    @Query("SELECT * FROM suspend_record")
    fun observeAll(): Flow<List<SuspendRecord>>

    @Insert
    suspend fun insertAll(records: List<SuspendRecord>)

    @Query("DELETE FROM suspend_record WHERE packageName IN (:packageNames)")
    suspend fun deleteByPackages(packageNames: List<String>)

    /** Remove every record — used when a package is released or on full reset. */
    @Query("DELETE FROM suspend_record")
    suspend fun clear()
}