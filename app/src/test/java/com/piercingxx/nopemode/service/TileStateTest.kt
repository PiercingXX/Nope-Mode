package com.piercingxx.nopemode.service

import com.piercingxx.nopemode.core.Override
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

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

    @Test
    fun `tapping while off turns it on and re-enables the master`() {
        // The reported bug: with the master off, reconcile ignores overrides, so
        // writing ForceOn alone left the tile unable to turn anything on.
        val tap = TileState.onTap(isActive = false, override = Override.None, activeBySchedule = false)
        assertTrue("must re-enable the master", tap.enabled)
        assertTrue(tap.override is Override.ForceOn)
    }

    @Test
    fun `tapping while off during a schedule window still turns it on`() {
        val tap = TileState.onTap(isActive = false, override = Override.None, activeBySchedule = true)
        assertTrue(tap.enabled)
        assertTrue(tap.override is Override.ForceOn)
    }

    @Test
    fun `tapping while forced on clears the force and keeps the master`() {
        val tap = TileState.onTap(
            isActive = true,
            override = Override.ForceOn(null),
            activeBySchedule = false,
        )
        assertTrue("outside a window, clearing the force is enough", tap.enabled)
        assertEquals(Override.None, tap.override)
    }

    @Test
    fun `tapping off inside a schedule window actually turns it off`() {
        // Clearing the override alone would leave the schedule holding it on,
        // so the tile would claim it turned something off that kept running.
        val tap = TileState.onTap(
            isActive = true,
            override = Override.ForceOn(null),
            activeBySchedule = true,
        )
        assertFalse("must drop the master or the tile lies", tap.enabled)
        assertEquals(Override.None, tap.override)
    }

    @Test
    fun `subtitle names the end time when there is one`() {
        val endsAt = LocalDateTime.of(2026, 8, 14, 8, 0)
        assertEquals(
            "Off at 8:00 AM",
            TileState.subtitle(isActive = true, override = Override.None, endsAt = endsAt)
        )
    }

    @Test
    fun `subtitle says always on for an indefinite force`() {
        assertEquals(
            "Always on",
            TileState.subtitle(
                isActive = true,
                override = Override.ForceOn(null),
                endsAt = LocalDateTime.of(2026, 8, 14, 8, 0),
            )
        )
    }

    @Test
    fun `subtitle says off when inactive`() {
        assertEquals(
            "Off",
            TileState.subtitle(isActive = false, override = Override.None, endsAt = null)
        )
    }
}
