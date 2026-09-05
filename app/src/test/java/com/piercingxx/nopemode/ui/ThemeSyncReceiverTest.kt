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
import java.io.File

/**
 * The launcher theme-sync receiver (BRAND-GUIDE.md §3.3).
 *
 * Two halves. First, display-name resolution: the broadcast carries the
 * launcher's display name, so [BackgroundTheme.presetForLabel] must invert
 * [BackgroundTheme.LABELS] for every preset — case-insensitively, because the
 * name crossed a process boundary — and refuse "Custom" and strangers rather
 * than snapping them to a wrong preset.
 *
 * Second, the receiver itself: the Android broadcast dispatch is not
 * JVM-testable, but the OS only ever delivers `xx.launcher.THEME_CHANGED` to a
 * component that is (a) declared in the manifest for that action and (b)
 * resolvable as a concrete class, so this locks both, then drives [onReceive]
 * with a MockK-spied [Intent] through the injectable persistence seam to prove
 * a broadcast lands in the same store-shaped sink the Settings picker writes —
 * which is what lets BrandActivity's onResume repaint pick it up unchanged.
 */
class ThemeSyncReceiverTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the repo-root-relative path for robustness.
    private val manifest: File =
        sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }

    private val context: Context = mockk(relaxed = true)

    /** A receiver whose persistence seam records writes into [sink]. */
    private fun receiver(sink: MutableList<String>): ThemeSyncReceiver =
        ThemeSyncReceiver(persistPreset = { _, preset -> sink.add(preset) })

    /** A launcher theme-change intent carrying [name] under the real extra key. */
    private fun themeIntent(name: String?): Intent =
        spyk(Intent(ThemeSyncReceiver.ACTION_THEME_CHANGED)).apply {
            every { action } returns ThemeSyncReceiver.ACTION_THEME_CHANGED
            every { getStringExtra(ThemeSyncReceiver.EXTRA_THEME_NAME) } returns name
        }

    /** A launcher theme-change intent carrying [name] and a background ARGB. */
    private fun themeIntent(name: String?, background: Int): Intent =
        spyk(Intent(ThemeSyncReceiver.ACTION_THEME_CHANGED)).apply {
            every { action } returns ThemeSyncReceiver.ACTION_THEME_CHANGED
            every { getStringExtra(ThemeSyncReceiver.EXTRA_THEME_NAME) } returns name
            every { hasExtra(ThemeSyncReceiver.EXTRA_BACKGROUND) } returns true
            every { getIntExtra(ThemeSyncReceiver.EXTRA_BACKGROUND, 0) } returns background
        }

    // ---- resolution: display name back to preset key ----

    @Test
    fun `every display name resolves back to its own key`() {
        // All seven, driven from LABELS itself so an eighth preset added later
        // is covered without touching this test.
        for ((key, label) in BackgroundTheme.LABELS) {
            assertEquals(key, BackgroundTheme.presetForLabel(label))
        }
        assertEquals(7, BackgroundTheme.LABELS.size)
    }

    @Test
    fun `resolution is case-insensitive`() {
        // The name crossed a process boundary; a casing tweak on the launcher
        // side must not silently break the sync.
        assertEquals("amoled", BackgroundTheme.presetForLabel("amoled night"))
        assertEquals("ocean", BackgroundTheme.presetForLabel("OCEAN DRIFT"))
        assertEquals("graphite", BackgroundTheme.presetForLabel("gRaPhItE"))
    }

    @Test
    fun `resolution tolerates surrounding whitespace`() {
        assertEquals("mist", BackgroundTheme.presetForLabel("  Mist "))
    }

    @Test
    fun `an unknown name resolves to null, not the default`() {
        // Null, not "amoled": the receiver declines to change anything rather
        // than snapping an unrecognised launcher theme to the wrong preset.
        assertNull(BackgroundTheme.presetForLabel("Not A Real Preset"))
        assertNull(BackgroundTheme.presetForLabel("Custom"))
        assertNull(BackgroundTheme.presetForLabel(""))
        assertNull(BackgroundTheme.presetForLabel(null))
    }

    // ---- wiring: the manifest declares the receiver for the broadcast ----

    @Test
    fun `manifest declares the theme-sync receiver component`() {
        assertTrue(
            "AndroidManifest.xml must declare the .ui.ThemeSyncReceiver component",
            manifest.readText().contains(".ui.ThemeSyncReceiver"),
        )
    }

    @Test
    fun `manifest registers the receiver for the launcher theme-changed action`() {
        assertTrue(
            "AndroidManifest.xml must register ThemeSyncReceiver for " +
                ThemeSyncReceiver.ACTION_THEME_CHANGED,
            manifest.readText().contains(ThemeSyncReceiver.ACTION_THEME_CHANGED),
        )
    }

    @Test
    fun `declared theme-sync receiver name resolves to a class`() {
        // Throws ClassNotFoundException if the declared name is not a real class.
        Class.forName("com.piercingxx.nopemode.ui.ThemeSyncReceiver")
    }

    // ---- routing: a broadcast persists the resolved key through the seam ----

    @Test
    fun `a broadcast persists the resolved preset key`() {
        val sink = mutableListOf<String>()
        receiver(sink).onReceive(context, themeIntent("Forest Night"))
        assertEquals(listOf("forest"), sink)
    }

    @Test
    fun `a differently-cased broadcast still persists the right key`() {
        val sink = mutableListOf<String>()
        receiver(sink).onReceive(context, themeIntent("BURGUNDY"))
        assertEquals(listOf("burgundy"), sink)
    }

    @Test
    fun `a Custom theme with a background persists the custom colour`() {
        // The launcher's Custom theme has no preset here; its resolved ARGB is
        // honoured instead, so the two apps still match on a custom choice.
        val sink = mutableListOf<String>()
        val colors = mutableListOf<Int>()
        ThemeSyncReceiver(
            persistPreset = { _, preset -> sink.add(preset) },
            persistCustomBackground = { _, color -> colors.add(color) },
        ).onReceive(context, themeIntent("Custom", 0xFF123456.toInt()))
        assertTrue(sink.isEmpty())
        assertEquals(listOf(0xFF123456.toInt()), colors)
    }

    @Test
    fun `a Custom theme without a background persists nothing`() {
        // No colour in the broadcast either: keep the user's current preset
        // rather than guessing.
        val sink = mutableListOf<String>()
        receiver(sink).onReceive(context, themeIntent("Custom"))
        assertTrue(sink.isEmpty())
    }

    @Test
    fun `an unknown name with a background persists the custom colour`() {
        val colors = mutableListOf<Int>()
        ThemeSyncReceiver(
            persistPreset = { _, _ -> },
            persistCustomBackground = { _, color -> colors.add(color) },
        ).onReceive(context, themeIntent("Not A Real Preset", 0xFFABCDEF.toInt()))
        assertEquals(listOf(0xFFABCDEF.toInt()), colors)
    }

    @Test
    fun `an unknown name persists nothing`() {
        val sink = mutableListOf<String>()
        receiver(sink).onReceive(context, themeIntent("Not A Real Preset"))
        assertTrue(sink.isEmpty())
    }

    @Test
    fun `a broadcast without the name extra persists nothing`() {
        val sink = mutableListOf<String>()
        receiver(sink).onReceive(context, themeIntent(null))
        assertTrue(sink.isEmpty())
    }

    @Test
    fun `a non-theme action persists nothing`() {
        val sink = mutableListOf<String>()
        receiver(sink).onReceive(
            context,
            spyk(Intent("some.other.action")).apply {
                every { action } returns "some.other.action"
            },
        )
        assertTrue(sink.isEmpty())
    }
}
