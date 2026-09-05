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
 * An unresolvable name is the launcher's "Custom" theme, which has no preset
 * here. Rather than a no-op, the receiver honours the resolved background
 * colour the broadcast carries ([EXTRA_BACKGROUND]) through
 * [SettingsStore.setCustomBackground], and [BrandActivity] paints that custom
 * colour over the named preset. If the broadcast carries no colour either, the
 * user's current preset is left alone — snapping a custom launcher colour to a
 * wrong named one would be worse than keeping what the user already has.
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
     * Persists a launcher Custom theme's background ARGB. Defaults to
     * [SettingsStore.setCustomBackground]; injectable so a JVM unit test can
     * capture the write in memory.
     */
    private val persistCustomBackground: (Context, Int) -> Unit = { context, color ->
        SettingsStore(context).setCustomBackground(color)
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
    /**
     * Extracts the resolved background ARGB from the [Intent]. Defaults to
     * reading [EXTRA_BACKGROUND]; injectable so a JVM unit test can supply a
     * colour without stubbing the extras bundle.
     */
    private val extractBackground: (Intent) -> Int? = { intent ->
        if (intent.hasExtra(EXTRA_BACKGROUND)) intent.getIntExtra(EXTRA_BACKGROUND, 0) else null
    },
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != action) return

        val name = extractThemeName(intent)
        val preset = name?.let { BackgroundTheme.presetForLabel(it) }
        if (preset != null) {
            persistPreset(context, preset)
            return
        }
        // Unresolvable name — the launcher's Custom theme. Honour its resolved
        // background colour if the broadcast carried one; otherwise leave the
        // user's current preset alone rather than snapping to a wrong one.
        val background = extractBackground(intent) ?: return
        persistCustomBackground(context, background)
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
