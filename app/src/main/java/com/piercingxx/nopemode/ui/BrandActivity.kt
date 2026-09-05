package com.piercingxx.nopemode.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.piercingxx.nopemode.R
import com.piercingxx.nopemode.data.SettingsStore

/**
 * Applies the selected background preset to a screen (BRAND-GUIDE.md §3.3).
 *
 * PiercingXX-Launcher themes at runtime rather than through resource
 * qualifiers, because the preset is a user setting rather than a system
 * configuration. Nope-Mode does the same so the two look identical on the same
 * choice, and so switching preset does not need an app restart.
 *
 * Only the ground and the type colour move. Signal white is untouched on every
 * preset — §3.1's one accent does not become two because the background changed.
 */
abstract class BrandActivity : AppCompatActivity() {

    /**
     * Live-repaint relay: while this screen is started it listens for the
     * launcher's theme-change broadcast and repaints in place, so a preset that
     * changes while the screen is already foregrounded does not wait for the
     * next `onResume` to show. Created lazily so `onStart`/`onStop` register and
     * unregister the one receiver across the activity's started lifetime.
     */
    private val themeRelay by lazy {
        ThemeRelay(this).apply { onThemeChanged = { applyTheme() } }
    }

    protected val preset: String
        get() = SettingsStore(this).backgroundPreset()

    /**
     * A launcher Custom theme's ARGB synced via [ThemeSyncReceiver], or null on
     * a named preset. Exposed so subclasses can derive text colours from the
     * same custom ground [applyTheme] paints, rather than only from the preset.
     */
    protected val customBackground: Int?
        get() = SettingsStore(this).customBackground()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        // Live-repaint: while started, a launcher theme-change broadcast repaints
        // immediately rather than waiting for the next onResume.
        themeRelay.start()
    }

    override fun onStop() {
        themeRelay.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    /** Repaint after `setContentView`; safe to call again on resume. */
    protected fun applyTheme() {
        val colors = BackgroundTheme.colors(preset, customBackground)
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        content.setBackgroundColor(colors.background)
        // Paint the layout's own root too. A layout that sets its own
        // background paints over android.R.id.content, which is how the first
        // attempt left a light preset with dark text on a still-black page.
        content.getChildAt(0)?.setBackgroundColor(colors.background)
        val root: View = content

        window.statusBarColor = colors.background
        window.navigationBarColor = colors.background
        // Light grounds need dark system-bar icons or they vanish.
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = BackgroundTheme.isLight(preset, customBackground)
            isAppearanceLightNavigationBars = BackgroundTheme.isLight(preset, customBackground)
        }

        retintText(
            root as? ViewGroup ?: return,
            BackgroundTheme.bodyTextColor(preset, customBackground),
        )
    }

    /**
     * Re-tint text that the layout painted with the AMOLED-era literals.
     *
     * Each view's ORIGINAL colour is stashed in a tag the first time it is seen,
     * and every later decision is made from that rather than from whatever is on
     * screen now. Matching on the current colour is one-way: after a light
     * preset repainted body text to #1A1A1A, nothing matched the dark literals
     * any more and switching back to AMOLED left the page dark-grey on black.
     *
     * Only the brand text colours are swapped. Anything deliberately coloured
     * keeps its own: warn-amber status, because §3.5 status colours are semantic
     * and must not follow the background, and anything at pure white, because
     * the reserved-white rule (§3.1) means 100% white IS the accent — retinting
     * it to the body colour is exactly what would make the accent stop reading.
     */
    private fun retintText(group: ViewGroup, textColor: Int) {
        val body = getColor(R.color.pxx_white_90)
        val muted = getColor(R.color.pxx_white_50)
        val mutedTarget = BackgroundTheme.mutedTextColor(preset, customBackground)

        fun walk(view: View) {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
            if (view is TextView) {
                val original = view.getTag(R.id.tag_original_text_color) as? Int
                    ?: view.currentTextColor.also {
                        view.setTag(R.id.tag_original_text_color, it)
                    }
                when (original) {
                    body -> view.setTextColor(textColor)
                    muted -> view.setTextColor(mutedTarget)
                }
            }
        }
        walk(group)
    }
}
