package com.piercingxx.nopemode.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.piercingxx.nopemode.R
import android.os.Build
import com.piercingxx.nopemode.core.NextBoundary
import com.piercingxx.nopemode.core.NopeController
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.SettingsStore
import com.piercingxx.nopemode.schedule.AlarmScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WS8 — the Quick Settings tile (design §11.1). A single toggle that reflects
 * whether Nope-Mode is ACTIVE (derived state) and flips `ForceOn` on or off.
 *
 * The tile tracks derived state even when it changed elsewhere: on every
 * [onStartListening] it re-derives and repaints, so it stays in sync with the
 * app. Tapping writes the toggled override and routes through the reconcile
 * loop ([AlarmScheduler.reconcileAndApply]) rather than calling the enforcer
 * directly, so the change goes through the same derived-state path as every
 * other state change (D8).
 *
 * Android-dependent — behavior is deferred to the operator's on-device check
 * (design §16). The pure decision slice is [TileState].
 */
class NopeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val db = NopeDatabase.get(applicationContext)
        val appStateDao = db.appStateDao()
        val settings = SettingsStore(applicationContext)
        val current = runBlocking { OverrideMapper.toOverride(appStateDao.get()) }
        val schedules = runBlocking { db.scheduleDao().observeAll().first() }
        val now = LocalDateTime.now(ZoneId.systemDefault())

        val activeBySchedule = NopeController.derive(now, schedules, Override.None)
        val isActive = settings.isEnabled() && NopeController.derive(now, schedules, current)

        val tap = TileState.onTap(isActive, current, activeBySchedule)
        settings.setEnabled(tap.enabled)
        runBlocking { appStateDao.upsert(OverrideMapper.toAppState(tap.override)) }
        // Route through reconcile so enforcement follows the derived state.
        AlarmScheduler.from(applicationContext).reconcileAndApply()
        refresh()
    }

    /** Re-derive the current state and repaint the tile to match it. */
    private fun refresh() {
        val tile = qsTile ?: return
        val db = NopeDatabase.get(applicationContext)
        val override = runBlocking { OverrideMapper.toOverride(db.appStateDao().get()) }
        val schedules = runBlocking { db.scheduleDao().observeAll().first() }
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val enabled = SettingsStore(applicationContext).isEnabled()
        val active = enabled &&
            NopeController.derive(now, schedules, override)
        tile.state = when (TileState.state(active)) {
            TileState.State.ACTIVE -> Tile.STATE_ACTIVE
            TileState.State.INACTIVE -> Tile.STATE_INACTIVE
        }
        tile.label = getString(if (active) R.string.tile_active else R.string.tile_inactive)
        // Focus Mode's tile shows when it ends; the shade is exactly where that
        // matters, since the alternative is opening the app to find out.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = TileState.subtitle(
                isActive = active,
                override = override,
                endsAt = if (enabled) NextBoundary.next(now, schedules) else null,
            )
        }
        tile.updateTile()
    }
}