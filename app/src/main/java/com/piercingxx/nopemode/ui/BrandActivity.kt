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
 * Only the ground and the type colour move. Signal green is untouched on every
 * preset — §3.1's one accent does not become two because the background changed.
 */
abstract class BrandActivity : AppCompatActivity() {

    protected val preset: String
        get() = SettingsStore(this).backgroundPreset()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    /** Repaint after `setContentView`; safe to call again on resume. */
    protected fun applyTheme() {
        val colors = BackgroundTheme.colors(preset)
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
            isAppearanceLightStatusBars = BackgroundTheme.isLight(preset)
            isAppearanceLightNavigationBars = BackgroundTheme.isLight(preset)
        }

        retintText(root as? ViewGroup ?: return, colors.text)
    }

    /**
     * Re-tint text that the layout painted with the AMOLED-era literals.
     *
     * Only the two brand text colours are swapped: anything deliberately
     * coloured — Signal green headings, warn-amber status — is left alone, since
     * §3.5 status colours are semantic and must not follow the background.
     */
    private fun retintText(group: ViewGroup, textColor: Int) {
        val paper = getColor(R.color.pxx_paper)
        val body = getColor(R.color.pxx_white_90)
        val muted = getColor(R.color.pxx_white_50)
        val mutedTarget = BackgroundTheme.mutedTextColor(preset)

        fun walk(view: View) {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
            if (view is TextView) {
                when (view.currentTextColor) {
                    paper, body -> view.setTextColor(textColor)
                    muted -> view.setTextColor(mutedTarget)
                }
            }
        }
        walk(group)
    }
}
