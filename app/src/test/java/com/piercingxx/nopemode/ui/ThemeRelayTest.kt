package com.piercingxx.nopemode.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live-repaint relay (T2).
 *
 * [BrandActivity] repaints on `onResume`, which misses a theme change that
 * arrives while the screen is already foregrounded. [ThemeRelay] closes that
 * gap: while the activity is started it registers a receiver for the launcher's
 * theme-change broadcast and fires [ThemeRelay.onThemeChanged] — which
 * [BrandActivity] wires to `applyTheme()` — so the visible page repaints in
 * place instead of waiting for the next resume.
 *
 * The Android broadcast dispatch itself is not JVM-testable, but the relay's
 * seams let a test capture the exact receiver the activity registers and drive
 * its [BroadcastReceiver.onReceive] directly, proving the relay (a) registers
 * and unregisters one receiver across start/stop and (b) fires the callback
 * only for the launcher's theme action.
 */
class ThemeRelayTest {

    private val context: Context = mockk(relaxed = true)

    /** A relay whose register/unregister seams capture the receiver and filter. */
    private class Captured {
        val relay: ThemeRelay
        var receiver: BroadcastReceiver? = null
        var filter: IntentFilter? = null
        val unregistered = mutableListOf<BroadcastReceiver>()

        init {
            relay = ThemeRelay(
                mockk(relaxed = true),
                register = { r, f ->
                    receiver = r
                    filter = f
                },
                unregister = { r -> unregistered.add(r) },
            )
        }
    }

    /** A launcher theme-change intent carrying the real action. */
    private fun themeIntent(): Intent =
        spyk(Intent(ThemeSyncReceiver.ACTION_THEME_CHANGED)).apply {
            every { action } returns ThemeSyncReceiver.ACTION_THEME_CHANGED
        }

    // ---- lifecycle: one receiver registered on start, unregistered on stop ----

    @Test
    fun `start registers a receiver for the theme-changed action`() {
        val captured = Captured()
        captured.relay.start()
        assertTrue("relay must register a receiver", captured.receiver != null)
    }

    @Test
    fun `stop unregisters the exact receiver start registered`() {
        val captured = Captured()
        captured.relay.start()
        captured.relay.stop()
        assertEquals(listOf(captured.receiver), captured.unregistered)
    }

    // ---- relay behaviour: the theme broadcast repaints, others do not ----

    @Test
    fun `a theme-changed broadcast fires onThemeChanged`() {
        val captured = Captured()
        var fired = 0
        captured.relay.onThemeChanged = { fired++ }
        captured.relay.start()
        captured.receiver!!.onReceive(context, themeIntent())
        assertEquals(1, fired)
    }

    @Test
    fun `a non-theme broadcast does not fire onThemeChanged`() {
        val captured = Captured()
        var fired = 0
        captured.relay.onThemeChanged = { fired++ }
        captured.relay.start()
        captured.receiver!!.onReceive(
            context,
            spyk(Intent("some.other.action")).apply {
                every { action } returns "some.other.action"
            },
        )
        assertEquals(0, fired)
    }

    @Test
    fun `the relay listens for the same action the manifest receiver does`() {
        // The relay must react to the same broadcast the launcher sends to the
        // manifest-declared ThemeSyncReceiver, or the live repaint and the
        // persisted preset could disagree about what a theme change is.
        assertEquals(
            ThemeSyncReceiver.ACTION_THEME_CHANGED,
            ThemeRelay(mockk(relaxed = true)).defaultAction(),
        )
    }
}