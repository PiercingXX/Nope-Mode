package com.piercingxx.nopemode.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Relays the launcher's theme-change broadcast to a live [BrandActivity].
 *
 * [BrandActivity] repaints on `onResume`, which is enough when the theme changed
 * while the screen was away. But a theme change that arrives while the screen is
 * already foregrounded never triggers a resume, so the visible page would sit
 * with the old preset until the user left and came back. [ThemeRelay] closes
 * that gap: while an activity is started it registers a receiver for the same
 * [ThemeSyncReceiver.ACTION_THEME_CHANGED] the launcher sends, and fires
 * [onThemeChanged] so the activity can repaint in place.
 *
 * The register/unregister seams are injectable so a JVM unit test can capture
 * the receiver and drive [onReceive] without the Android platform.
 */
class ThemeRelay(
    private val context: Context,
    private val action: String = ThemeSyncReceiver.ACTION_THEME_CHANGED,
    private val register: (BroadcastReceiver, IntentFilter) -> Unit = { receiver, filter ->
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    },
    private val unregister: (BroadcastReceiver) -> Unit = { receiver ->
        context.unregisterReceiver(receiver)
    },
) {
    /** Invoked when a theme-change broadcast arrives while the relay is started. */
    var onThemeChanged: () -> Unit = {}

    /** The broadcast action this relay listens for. */
    fun defaultAction(): String = action

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == action) onThemeChanged()
        }
    }

    /** Register the relay; call from `onStart`. */
    fun start() = register(receiver, IntentFilter(action))

    /** Unregister the relay; call from `onStop`. */
    fun stop() = unregister(receiver)
}