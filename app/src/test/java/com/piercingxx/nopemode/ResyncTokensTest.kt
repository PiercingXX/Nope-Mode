package com.piercingxx.nopemode

import com.piercingxx.nopemode.ui.BackgroundTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The token resync (BRAND-GUIDE.md §7.7 — consume the tokens, don't retype
 * hexes). Pins [ResyncTokens.CURRENT] to the values in
 * `piercingxx-branding/tokens/android-colors.xml`, verifies the old-set guard
 * and the resync, and proves the app's runtime preset set is actually fed from
 * the resync source rather than a second hardcoded copy.
 */
class ResyncTokensTest {

    @Test
    fun `current tokens match the branding repo's android-colors`() {
        // Source of truth: piercingxx-branding/tokens/android-colors.xml §3.3.
        assertEquals(0xFF000000.toInt(), ResyncTokens.CURRENT["amoled"])
        assertEquals(0xFF131316.toInt(), ResyncTokens.CURRENT["graphite"])
        assertEquals(0xFF10261B.toInt(), ResyncTokens.CURRENT["forest"])
        assertEquals(0xFF0F1C2E.toInt(), ResyncTokens.CURRENT["ocean"])
        assertEquals(0xFF2A1018.toInt(), ResyncTokens.CURRENT["burgundy"])
        assertEquals(0xFFF3EEE2.toInt(), ResyncTokens.CURRENT["paper"])
        assertEquals(0xFFE6EDF5.toInt(), ResyncTokens.CURRENT["mist"])
    }

    @Test
    fun `the old set is the pre-reserved-white palette`() {
        // Only graphite differs in the grounds; the rework's real churn was the
        // product accents and status colours, which live in brand_colors.xml.
        assertEquals(0xFF111827.toInt(), ResyncTokens.OLD["graphite"])
        assertEquals(ResyncTokens.CURRENT["amoled"], ResyncTokens.OLD["amoled"])
        assertEquals(ResyncTokens.CURRENT["paper"], ResyncTokens.OLD["paper"])
    }

    @Test
    fun `isOld flags only the old set`() {
        assertTrue(ResyncTokens.isOld(ResyncTokens.OLD))
        assertFalse(ResyncTokens.isOld(ResyncTokens.CURRENT))
    }

    @Test
    fun `resync replaces the old set with the current tokens`() {
        val resynced = ResyncTokens.resyncIfOld(ResyncTokens.OLD)
        assertEquals(ResyncTokens.CURRENT, resynced)
        assertEquals(0xFF131316.toInt(), resynced["graphite"])
    }

    @Test
    fun `resync leaves an already-current palette alone`() {
        // A palette that is already current is not snapped to a stale copy.
        val current = ResyncTokens.CURRENT
        assertSame(current, ResyncTokens.resyncIfOld(current))
    }

    @Test
    fun `the app's preset grounds are fed from the resync source`() {
        // The wiring: BackgroundTheme must read its grounds from ResyncTokens,
        // not a second hardcoded copy, so a rework upstream lands everywhere.
        assertEquals(ResyncTokens.CURRENT, BackgroundTheme.PRESETS.mapValues { it.value.background })
    }
}