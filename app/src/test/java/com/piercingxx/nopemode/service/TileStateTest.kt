package com.piercingxx.nopemode.service

import com.piercingxx.nopemode.core.Override
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * T7 — WS8 Quick Settings tile: the pure decision slice ([TileState]).
 *
 * The tile is a single toggle (design §11.1): it reflects whether Nope-Mode is
 * ACTIVE (derived state) and tapping it flips `ForceOn` on or off. The Android
 * service ([NopeTileService]) is deferred to the operator's on-device check;
 * this pins the decision logic that is JVM-provable.
 */
class TileStateTest {

    // ---- state(): the tile tracks DERIVED state, not just the override ----

    @Test
    fun `state is ACTIVE whenever Nope-Mode is derived active`() {
        assertEquals(TileState.State.ACTIVE, TileState.state(true))
    }

    @Test
    fun `state is INACTIVE when Nope-Mode is derived inactive`() {
        assertEquals(TileState.State.INACTIVE, TileState.state(false))
    }

    // ---- toggle(): ForceOn clears, anything else becomes indefinite ForceOn ----

    @Test
    fun `toggle clears an indefinite ForceOn back to None`() {
        val next = TileState.toggle(Override.ForceOn(null))
        assertEquals(Override.None, next)
    }

    @Test
    fun `toggle clears a bounded ForceOn back to None`() {
        val next = TileState.toggle(Override.ForceOn(Instant.parse("2026-08-13T23:59:59Z")))
        assertEquals(Override.None, next)
    }

    @Test
    fun `toggle on None becomes an indefinite ForceOn`() {
        val next = TileState.toggle(Override.None)
        assertTrue(next is Override.ForceOn)
        assertEquals(null, (next as Override.ForceOn).until)
    }

    @Test
    fun `toggle on a Break becomes an indefinite ForceOn`() {
        val next = TileState.toggle(Override.Break(Instant.parse("2026-08-13T23:59:59Z")))
        assertTrue(next is Override.ForceOn)
        assertEquals(null, (next as Override.ForceOn).until)
    }
}