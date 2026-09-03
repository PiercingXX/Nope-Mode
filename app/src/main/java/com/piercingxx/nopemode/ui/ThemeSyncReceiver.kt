package com.piercingxx.nopemode.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piercingxx.nopemode.data.SettingsStore

/**
 * `BroadcastReceiver` for PiercingXX-Launcher's theme-change broadcast
 * (`xx.launcher.THEME_CHANGED`).
 *
 * Completes the family theme sync (BRAND-GUIDE.md §3.3): the launcher
 * broadcasts its active background preset's display name; Nope-Mode resolves
 * it back to a preset key via [BackgroundTheme.presetForLabel] and persists it
 * through [SettingsStore.setBackgroundPreset] — the exact store and key the
 * Settings picker writes — so the existing application path does the rest:
 * every screen extends [BrandActivity], which re-reads the preset and repaints
 * on `onResume`. No new theme plumbing, just a second writer to the one store.
 *
 * "Custom" (and any other unrecognised name) is deliberately a no-op. The
 * broadcast also carries the resolved background colour ([EXTRA_BACKGROUND]),
 * but Nope-Mode's model is the seven named presets — [SettingsStore] holds a
 * preset key, not a colour, and BRAND-GUIDE.md §3.3 says to reuse the shared
 * names rather than grow a parallel scheme. Keeping the user's current preset
 * beats snapping a custom launcher colour to the wrong named one.
 *
 * Manifest-declared and exported: the sender is another app, and it targets
 * this package explicitly (`Intent.setPackage`), which Android O+ requires
 * before a manifest-declared receiver sees an implicit broadcast at all. The
 * payload is a display name — worst case a spoofed broadcast switches the
 * background between the same presets the Settings screen already offers.
 *
 * The persistence sink, action string, and name extractor are injectable so a
 * JVM unit test can drive [onReceive] with a real [Intent] without mocking the
 * Android platform (same seams as Txxt's ThemeSyncReceiver).
 */
class ThemeSyncReceiver(
    /**
     * Persists a resolved preset key. Defaults to the same
     * [SettingsStore.setBackgroundPreset] the Settings picker calls, so a
     * broadcast and a manual pick land in the one store [BrandActivity] reads;
     * injectable so a JVM unit test can capture the write in memory.
     */
    private val persistPreset: (Context, String) -> Unit = { context, preset ->
        SettingsStore(context).setBackgroundPreset(preset)
    },
    /**
     * Action string to match against the inbound [Intent]. Defaults to the
     * launcher's action; injectable so a JVM unit test can drive [onReceive]
     * without the Android stub returning null.
     */
    private val action: String = ACTION_THEME_CHANGED,
    /**
     * Extracts the active preset's display name from the [Intent]. Defaults to
     * reading [EXTRA_THEME_NAME]; injectable so a JVM unit test can supply a
     * name without stubbing the extras bundle.
     */
    private val extractThemeName: (Intent) -> String? = { intent ->
        intent.getStringExtra(EXTRA_THEME_NAME)
    },
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != action) return

        val name = extractThemeName(intent) ?: return
        val preset = BackgroundTheme.presetForLabel(name) ?: return
        persistPreset(context, preset)
    }

    companion object {
        /** PiercingXX-Launcher's theme-change broadcast action. */
        const val ACTION_THEME_CHANGED = "xx.launcher.THEME_CHANGED"

        /** String extra: the active preset's display name ("Ocean Drift"). */
        const val EXTRA_THEME_NAME = "xx.launcher.extra.THEME_NAME"

        /**
         * Int extra: the resolved background ARGB, present even for "Custom".
         * Documented but unread — [SettingsStore] stores a preset key, not a
         * raw colour, so Nope-Mode has nowhere honest to put an arbitrary int.
         */
        const val EXTRA_BACKGROUND = "xx.launcher.extra.BACKGROUND"
    }
}
