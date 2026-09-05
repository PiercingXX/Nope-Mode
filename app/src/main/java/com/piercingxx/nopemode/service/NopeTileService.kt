package com.piercingxx.nopemode.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.piercingxx.nopemode.R
import android.os.Build
import com.piercingxx.nopemode.schedule.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    /**
     * The tile's coroutine home. Room work runs on [Dispatchers.IO] inside
     * [TileLoader]; this scope stays on the main thread and only repaints.
     * Cancelled when the service is torn down so a stale repaint cannot outlive
     * the tile.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val loader = TileLoader.from(applicationContext)
        scope.launch {
            loader.toggle()
            // Route through the reconcile loop rather than calling the enforcer
            // directly, so the change goes through the same derived-state path
            // as every other state change (D8).
            runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }
            repaint(loader)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Re-derive the current state and repaint the tile to match it. */
    private fun refresh() {
        val tile = qsTile ?: return
        val loader = TileLoader.from(applicationContext)
        scope.launch { repaint(loader) }
    }

    /** Paint the tile from a freshly derived snapshot. */
    private suspend fun repaint(loader: TileLoader) {
        val tile = qsTile ?: return
        val snapshot = loader.load()
        tile.state = when (TileState.state(snapshot.active)) {
            TileState.State.ACTIVE -> Tile.STATE_ACTIVE
            TileState.State.INACTIVE -> Tile.STATE_INACTIVE
        }
        tile.label = getString(if (snapshot.active) R.string.tile_active else R.string.tile_inactive)
        // Focus Mode's tile shows when it ends; the shade is exactly where that
        // matters, since the alternative is opening the app to find out.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = TileState.subtitle(
                isActive = snapshot.active,
                override = snapshot.override,
                endsAt = snapshot.endsAt,
            )
        }
        tile.updateTile()
    }
}