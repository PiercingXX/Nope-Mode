package com.piercingxx.nopemode.service

import com.piercingxx.nopemode.core.Override
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    /**
     * The tile's second line: when it turns off, the way Focus Mode's tile reads
     * "Off at 5:00 PM".
     *
     * A tile that only says "on" makes you open the app to learn the one thing
     * you actually want from the shade — when it ends. An indefinite `ForceOn`
     * has no end, and saying so is the honest answer rather than inventing the
     * next schedule boundary it will not obey.
     *
     * [endsAt] is the next boundary at which the active state flips off, already
     * derived by the caller; null means there is no such boundary.
     */
    fun subtitle(
        isActive: Boolean,
        override: Override,
        endsAt: LocalDateTime?,
        formatter: DateTimeFormatter = DEFAULT_TIME_FORMAT,
    ): String {
        if (!isActive) return "Off"
        if (override is Override.ForceOn && override.until == null) return "Always on"
        if (override is Override.Break) return "On a break"
        return endsAt?.let { "Off at ${formatter.format(it)}" } ?: "Always on"
    }

    /** 12-hour with AM/PM, matching how the shade renders times. */
    private val DEFAULT_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a")
}