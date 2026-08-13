package com.piercingxx.nopemode.service

import com.piercingxx.nopemode.core.Override

/**
 * WS8 — the pure decision slice of the Quick Settings tile (design §11.1).
 * Pure — no `android.*` at this layer, so it is JVM-provable and covered by
 * [TileStateTest].
 *
 * The tile is a single toggle. It reflects whether Nope-Mode is ACTIVE right
 * now (derived state, not just the override), and tapping it flips `ForceOn`
 * on or off. Enforcement itself still routes through the reconcile loop
 * (design §11.1: "Tile click routes through reconcile(), not a direct enforcer
 * call"); this object only decides WHAT the tile shows and WHAT override to
 * write.
 */
object TileState {

    /** What the tile shows in the shade. */
    enum class State { ACTIVE, INACTIVE }

    /**
     * The tile's displayed state. It tracks DERIVED state, so it is ACTIVE
     * whenever Nope-Mode is active — whether by schedule or by a `ForceOn`
     * override. Only the schedule-inactive-with-no-force case is INACTIVE.
     */
    fun state(isActive: Boolean): State =
        if (isActive) State.ACTIVE else State.INACTIVE

    /**
     * The override to write when the tile is tapped — the toggle. A `ForceOn`
     * override is cleared back to [Override.None] (so the schedule decides
     * again); anything else becomes an indefinite [Override.ForceOn].
     */
    fun toggle(override: Override): Override =
        if (override is Override.ForceOn) Override.None else Override.ForceOn(null)
}