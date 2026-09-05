package com.piercingxx.nopemode.ui

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher Custom theme sync (BRAND-GUIDE.md §3.3, T1).
 *
 * The launcher's Custom theme has no named preset, so the theme-change
 * broadcast carries its resolved background ARGB in [ThemeSyncReceiver.EXTRA_BACKGROUND].
 * These tests pin the whole path: the receiver reads that colour and persists
 * it through [SettingsStore.setCustomBackground]; [BackgroundTheme] derives the
 * type colour for an arbitrary custom ground with the same contrast rule the
 * presets pin; and [SettingsStore] keeps a custom colour and a named preset
 * mutually exclusive so a later named pick clears the custom override.
 */
class ThemeSyncCustomTest {

    private val context: Context = mockk(relaxed = true)

    /** A receiver whose custom-colour seam records writes into [colors]. */
    private fun customReceiver(colors: MutableList<Int>): ThemeSyncReceiver =
        ThemeSyncReceiver(persistCustomBackground = { _, color -> colors.add(color) })

    /** A launcher Custom theme-change intent carrying [background] ARGB. */
    private fun customIntent(background: Int): Intent =
        spyk(Intent(ThemeSyncReceiver.ACTION_THEME_CHANGED)).apply {
            every { action } returns ThemeSyncReceiver.ACTION_THEME_CHANGED
            every { getStringExtra(ThemeSyncReceiver.EXTRA_THEME_NAME) } returns "Custom"
            every { hasExtra(ThemeSyncReceiver.EXTRA_BACKGROUND) } returns true
            every { getIntExtra(ThemeSyncReceiver.EXTRA_BACKGROUND, 0) } returns background
        }

    // ---- routing: a Custom broadcast persists the resolved colour ----

    @Test
    fun `a Custom broadcast persists the background colour`() {
        val colors = mutableListOf<Int>()
        customReceiver(colors).onReceive(context, customIntent(0xFF123456.toInt()))
        assertEquals(listOf(0xFF123456.toInt()), colors)
    }

    @Test
    fun `a Custom broadcast on a light colour still persists it`() {
        val colors = mutableListOf<Int>()
        customReceiver(colors).onReceive(context, customIntent(0xFFF3EEE2.toInt()))
        assertEquals(listOf(0xFFF3EEE2.toInt()), colors)
    }

    // ---- BackgroundTheme: a custom colour wins over the named preset ----

    @Test
    fun `a custom colour overrides the named preset's ground`() {
        val colors = BackgroundTheme.colors("amoled", 0xFF123456.toInt())
        assertEquals(0xFF123456.toInt(), colors.background)
        // Dark custom ground takes white type, same contrast rule as presets.
        assertEquals(0xFFFFFFFF.toInt(), colors.text)
    }

    @Test
    fun `a light custom colour takes dark type and light system bars`() {
        val colors = BackgroundTheme.colors("amoled", 0xFFF3EEE2.toInt())
        assertEquals(0xFFF3EEE2.toInt(), colors.background)
        assertEquals(0xFF1A1A1A.toInt(), colors.text)
        assertTrue(BackgroundTheme.isLight("amoled", 0xFFF3EEE2.toInt()))
    }

    @Test
    fun `without a custom colour the named preset stands`() {
        assertEquals(0xFF000000.toInt(), BackgroundTheme.colors("amoled").background)
        assertNull(BackgroundTheme.colors("amoled").text.takeIf { it == 0 })
    }
}