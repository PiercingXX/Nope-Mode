package com.piercingxx.nopemode.data

import com.piercingxx.nopemode.core.Override
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * T2 — OverrideMapperTest (design §10, WS2). The mappers are the pure part of
 * the data layer and are JVM-provable; this is the task's verify.
 *
 * Covers the [AppState] ↔ [Override] round-trip both directions, including
 * `ForceOn(null)` (indefinite), and the fresh-install case where a `null` row
 * reads as [Override.None].
 */
class OverrideMapperTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `null row reads as Override None`() {
        assertEquals(Override.None, OverrideMapper.toOverride(null))
    }

    @Test
    fun `row with unset kind reads as Override None`() {
        val row = AppState(id = 1, overrideKind = null, overrideUntil = null, lastReconcileAt = now)
        assertEquals(Override.None, OverrideMapper.toOverride(row))
    }

    @Test
    fun `row with NONE kind reads as Override None`() {
        val row = AppState(id = 1, overrideKind = AppState.NONE, overrideUntil = null, lastReconcileAt = now)
        assertEquals(Override.None, OverrideMapper.toOverride(row))
    }

    @Test
    fun `None round-trips`() {
        val override = Override.None
        assertEquals(override, OverrideMapper.toOverride(OverrideMapper.toAppState(override, now)))
    }

    @Test
    fun `ForceOn indefinite round-trips`() {
        val override = Override.ForceOn(null)
        assertEquals(override, OverrideMapper.toOverride(OverrideMapper.toAppState(override, now)))
    }

    @Test
    fun `ForceOn bounded round-trips`() {
        val override = Override.ForceOn(Instant.ofEpochMilli(now + 60_000))
        assertEquals(override, OverrideMapper.toOverride(OverrideMapper.toAppState(override, now)))
    }

    @Test
    fun `Break round-trips`() {
        val override = Override.Break(Instant.ofEpochMilli(now + 300_000))
        assertEquals(override, OverrideMapper.toOverride(OverrideMapper.toAppState(override, now)))
    }

    @Test
    fun `ForceOn indefinite stores null overrideUntil`() {
        val row = OverrideMapper.toAppState(Override.ForceOn(null), now)
        assertEquals(AppState.FORCE_ON, row.overrideKind)
        assertEquals(null, row.overrideUntil)
    }

    @Test
    fun `Break stores its until as epoch millis`() {
        val until = Instant.ofEpochMilli(now + 300_000)
        val row = OverrideMapper.toAppState(Override.Break(until), now)
        assertEquals(AppState.BREAK, row.overrideKind)
        assertEquals(until.toEpochMilli(), row.overrideUntil)
    }

    @Test
    fun `mapper preserves lastReconcileAt`() {
        val row = OverrideMapper.toAppState(Override.None, lastReconcileAt = now)
        assertEquals(now, row.lastReconcileAt)
        assertTrue("id must stay the single-row id 1", row.id == 1L)
    }
}