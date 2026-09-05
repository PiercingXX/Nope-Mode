package com.piercingxx.nopemode.service

import android.content.Context
import com.piercingxx.nopemode.core.NextBoundary
import com.piercingxx.nopemode.core.NopeController
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.data.AppStateDao
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.ScheduleDao
import com.piercingxx.nopemode.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * T4 — the QS tile's data access, moved off the main thread (design §11.1).
 *
 * [NopeTileService] used to call the Room DAOs directly via `runBlocking` on
 * the main thread — a StrictMode violation and a jank source whenever the tile
 * is tapped or re-listed. This class owns every Room read and write the tile
 * needs and runs them on [Dispatchers.IO], so the service's coroutine can stay
 * on the main thread and only touch the pure decision slice ([TileState],
 * [NopeController], [NextBoundary]).
 *
 * The DAOs and [SettingsStore] are constructor-injected (mirroring
 * [com.piercingxx.nopemode.schedule.AlarmScheduler]) so the loader is
 * JVM-provable with mocked DAOs — see [TileLoaderTest]. [from] wires it from a
 * live [Context].
 */
class TileLoader(
    private val appStateDao: AppStateDao,
    private val scheduleDao: ScheduleDao,
    private val settings: SettingsStore,
    private val zone: ZoneId,
) {

    /** Everything the tile needs to paint itself, derived in one shot. */
    data class Snapshot(
        val enabled: Boolean,
        val override: Override,
        /** Derived active state — schedule and override combined. */
        val active: Boolean,
        /** Active by schedule alone, ignoring any override (for the toggle math). */
        val activeBySchedule: Boolean,
        /** The next boundary at which active state flips off; null when none. */
        val endsAt: LocalDateTime?,
    )

    /**
     * Re-derive the tile's current state from storage. All Room access happens
     * on [Dispatchers.IO].
     */
    suspend fun load(now: LocalDateTime = LocalDateTime.now(zone)): Snapshot {
        val (override, schedules, enabled) = withContext(Dispatchers.IO) {
            Triple(
                OverrideMapper.toOverride(appStateDao.get()),
                scheduleDao.observeAll().first(),
                settings.isEnabled(),
            )
        }
        val active = enabled && NopeController.derive(now, schedules, override)
        val activeBySchedule = NopeController.derive(now, schedules, Override.None)
        val endsAt = if (enabled) NextBoundary.next(now, schedules) else null
        return Snapshot(enabled, override, active, activeBySchedule, endsAt)
    }

    /**
     * The tile-tap path: read current state, compute the toggle via
     * [TileState.onTap], write the master flag and override, then return the
     * freshly derived state. The caller is responsible for routing through the
     * reconcile loop ([com.piercingxx.nopemode.schedule.AlarmScheduler]) so the
     * change goes through the same derived-state path as every other change
     * (D8).
     */
    suspend fun toggle(now: LocalDateTime = LocalDateTime.now(zone)): Snapshot {
        val current = load(now)
        val tap = TileState.onTap(current.active, current.override, current.activeBySchedule)
        withContext(Dispatchers.IO) {
            settings.setEnabled(tap.enabled)
            appStateDao.upsert(OverrideMapper.toAppState(tap.override))
        }
        return load(now)
    }

    companion object {
        /** Wire a loader for the tile from a live [Context]. */
        fun from(context: Context): TileLoader {
            val appContext = context.applicationContext
            val db = NopeDatabase.get(appContext)
            return TileLoader(
                appStateDao = db.appStateDao(),
                scheduleDao = db.scheduleDao(),
                settings = SettingsStore(appContext),
                zone = ZoneId.systemDefault(),
            )
        }
    }
}