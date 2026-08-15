package com.piercingxx.nopemode.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The background presets must stay in lockstep with PiercingXX-Launcher's
 * `ThemeManager` (BRAND-GUIDE.md §3.3 — reuse the same names and values).
 *
 * These pin the palette and the contrast rule to the launcher's, so a later
 * edit to one has to be a deliberate change to both rather than a silent drift.
 */
class BackgroundThemeTest {

    private val white = 0xFFFFFFFF.toInt()
    private val darkInk = 0xFF1A1A1A.toInt()

    @Test
    fun `preset values match the launcher exactly`() {
        assertEquals(0xFF000000.toInt(), BackgroundTheme.colors("amoled").background)
        assertEquals(0xFF111827.toInt(), BackgroundTheme.colors("graphite").background)
        assertEquals(0xFF10261B.toInt(), BackgroundTheme.colors("forest").background)
        assertEquals(0xFF0F1C2E.toInt(), BackgroundTheme.colors("ocean").background)
        assertEquals(0xFFF3EEE2.toInt(), BackgroundTheme.colors("paper").background)
        assertEquals(0xFFE6EDF5.toInt(), BackgroundTheme.colors("mist").background)
    }

    @Test
    fun `preset order matches the launcher preview strip`() {
        assertEquals(
            listOf("amoled", "graphite", "forest", "ocean", "paper", "mist"),
            BackgroundTheme.PRESETS.keys.toList()
        )
    }

    @Test
    fun `every preset has a guide name`() {
        assertEquals(BackgroundTheme.PRESETS.keys, BackgroundTheme.LABELS.keys)
        assertEquals("AMOLED Night", BackgroundTheme.LABELS["amoled"])
        assertEquals("Ocean Drift", BackgroundTheme.LABELS["ocean"])
    }

    @Test
    fun `an unknown or missing preset falls back rather than crashing`() {
        assertEquals(BackgroundTheme.colors("amoled"), BackgroundTheme.colors(null))
        assertEquals(BackgroundTheme.colors("amoled"), BackgroundTheme.colors("not-a-preset"))
    }

    @Test
    fun `dark grounds take white type and light grounds take dark type`() {
        assertEquals(white, BackgroundTheme.contrastTextColor(0xFF000000.toInt()))
        assertEquals(white, BackgroundTheme.contrastTextColor(0xFF111827.toInt()))
        assertEquals(darkInk, BackgroundTheme.contrastTextColor(0xFFF3EEE2.toInt()))
        assertEquals(darkInk, BackgroundTheme.contrastTextColor(0xFFE6EDF5.toInt()))
    }

    @Test
    fun `the luminance threshold sits where the launcher put it`() {
        // Luminance 182 is the launcher's cutoff, and it is exclusive: a grey at
        // exactly 182 stays on the white-text side. Pinned because a different
        // threshold would flip mid-tones between the two apps.
        val at182 = 0xFF000000.toInt() or (182 shl 16) or (182 shl 8) or 182
        val at183 = 0xFF000000.toInt() or (183 shl 16) or (183 shl 8) or 183
        assertEquals(white, BackgroundTheme.contrastTextColor(at182))
        assertEquals(darkInk, BackgroundTheme.contrastTextColor(at183))
    }

    @Test
    fun `isLight marks only the light presets`() {
        assertFalse(BackgroundTheme.isLight("amoled"))
        assertFalse(BackgroundTheme.isLight("forest"))
        assertTrue(BackgroundTheme.isLight("paper"))
        assertTrue(BackgroundTheme.isLight("mist"))
    }

    @Test
    fun `body text stops at ninety percent so pure white stays the accent`() {
        // §3.1's reserved-white rule. If body copy reached 100% there would be
        // nothing left for the accent to out-weigh on an Ink ground, which is
        // the whole mechanism now that Signal is white rather than a hue.
        val onDark = BackgroundTheme.bodyTextColor("amoled")
        assertEquals(0xE6, (onDark ushr 24) and 0xFF)
        assertEquals(0xFFFFFF, onDark and 0x00FFFFFF)

        // Inverted on light grounds: 90% Ink body under 100% Ink accent.
        val onLight = BackgroundTheme.bodyTextColor("paper")
        assertEquals(0xE6, (onLight ushr 24) and 0xFF)
        assertEquals(0x1A1A1A, onLight and 0x00FFFFFF)
    }

    @Test
    fun `the accent always out-weighs body text on the same ground`() {
        val signal = 0xFFFFFFFF.toInt()
        for (preset in BackgroundTheme.PRESETS.keys) {
            val accentAlpha = (BackgroundTheme.accentTextColor(preset, signal) ushr 24) and 0xFF
            val bodyAlpha = (BackgroundTheme.bodyTextColor(preset) ushr 24) and 0xFF
            assertTrue("$preset: accent must be heavier than body", accentAlpha > bodyAlpha)
        }
    }

    @Test
    fun `muted text is the body colour at fifty percent, not a new grey`() {
        // §3.2: prefer opacity over new greys — and that has to hold on light
        // presets too, where a hardcoded white-50 would be invisible.
        val onDark = BackgroundTheme.mutedTextColor("amoled")
        assertEquals(0x80, (onDark ushr 24) and 0xFF)
        assertEquals(0xFFFFFF, onDark and 0x00FFFFFF)

        val onLight = BackgroundTheme.mutedTextColor("paper")
        assertEquals(0x80, (onLight ushr 24) and 0xFF)
        assertEquals(0x1A1A1A, onLight and 0x00FFFFFF)
    }

    @Test
    fun `hairlines follow the type colour at ten percent`() {
        assertEquals(0x1A, (BackgroundTheme.lineColor("amoled") ushr 24) and 0xFF)
        assertEquals(0xFFFFFF, BackgroundTheme.lineColor("amoled") and 0x00FFFFFF)
        assertEquals(0x1A1A1A, BackgroundTheme.lineColor("mist") and 0x00FFFFFF)
    }

    @Test
    fun `accent text keeps Signal on dark grounds`() {
        val signal = 0xFFFFFFFF.toInt()
        assertEquals(signal, BackgroundTheme.accentTextColor("amoled", signal))
        assertEquals(signal, BackgroundTheme.accentTextColor("ocean", signal))
    }

    @Test
    fun `accent text falls back to body colour on light grounds`() {
        // Signal is white, so accent type on Paper would be white on cream.
        // Rather than invent an off-guide darker tint, accent text uses the
        // body colour there; the accent still appears on filled controls.
        val signal = 0xFFFFFFFF.toInt()
        assertEquals(darkInk, BackgroundTheme.accentTextColor("paper", signal))
        assertEquals(darkInk, BackgroundTheme.accentTextColor("mist", signal))
    }
}
