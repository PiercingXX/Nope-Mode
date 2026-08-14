package com.piercingxx.nopemode.ui

/**
 * The named background presets (BRAND-GUIDE.md §3.3), mirroring
 * PiercingXX-Launcher's `ThemeManager` so the two apps theme identically.
 *
 * Values, order and the contrast rule are copied from the launcher deliberately:
 * §3.3 says to reuse the same names and values anywhere a product offers
 * background choices, and a second implementation that drifts is worse than no
 * second implementation.
 *
 * Pure — no `android.*`, so the palette and the contrast maths are JVM-provable.
 * Signal green stays the accent on every preset; only the ground and the type
 * colour change (§3.1, the rule of one accent).
 */
object BackgroundTheme {

    data class Colors(val background: Int, val text: Int)

    const val DEFAULT: String = "amoled"

    /** In display order, matching the launcher's preview strip. */
    val PRESETS: Map<String, Colors> = linkedMapOf(
        "amoled" to Colors(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
        "graphite" to Colors(0xFF111827.toInt(), 0xFFFFFFFF.toInt()),
        "forest" to Colors(0xFF10261B.toInt(), 0xFFFFFFFF.toInt()),
        "ocean" to Colors(0xFF0F1C2E.toInt(), 0xFFFFFFFF.toInt()),
        "paper" to Colors(0xFFF3EEE2.toInt(), 0xFF1A1A1A.toInt()),
        "mist" to Colors(0xFFE6EDF5.toInt(), 0xFF1A1A1A.toInt()),
    )

    /** Human labels, from the guide's evocative two-word names (§3.3). */
    val LABELS: Map<String, String> = linkedMapOf(
        "amoled" to "AMOLED Night",
        "graphite" to "Graphite",
        "forest" to "Forest Night",
        "ocean" to "Ocean Drift",
        "paper" to "Paper",
        "mist" to "Mist",
    )

    /** An unknown or missing preset falls back to the default rather than crashing. */
    fun colors(preset: String?): Colors = PRESETS[preset] ?: PRESETS.getValue(DEFAULT)

    /**
     * White on dark grounds, near-black on light ones.
     *
     * Same weights and the same 182 threshold as the launcher. Kept identical on
     * purpose: a different threshold would put the two apps on opposite sides of
     * the decision for a mid-tone background.
     */
    fun contrastTextColor(background: Int): Int {
        val r = (background shr 16) and 0xFF
        val g = (background shr 8) and 0xFF
        val b = background and 0xFF
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return if (luminance > 182) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
    }

    /** True when the preset is light enough to need dark system-bar icons. */
    fun isLight(preset: String?): Boolean = contrastTextColor(colors(preset).background) != 0xFFFFFFFF.toInt()

    /**
     * Secondary text: the same hue as the body colour at 50% alpha, matching
     * §3.2's "prefer opacity over new greys" on light presets as well as dark.
     */
    fun mutedTextColor(preset: String?): Int = (colors(preset).text and 0x00FFFFFF) or 0x80000000.toInt()

    /**
     * The colour for accent TEXT on a given preset.
     *
     * Signal green is a light colour, so green type on Paper or Mist is green
     * on cream and barely readable. §3.1 governs where the accent goes, not
     * whether it must be used where it cannot be read, and §5 forbids recolouring
     * the mark — so rather than invent an off-guide darker green, accent text
     * falls back to the body colour on light grounds. The accent is still
     * present there on filled controls, where it sits under Ink type and has
     * plenty of contrast.
     */
    fun accentTextColor(preset: String?, signal: Int): Int =
        if (isLight(preset)) colors(preset).text else signal

    /** Hairline: the body colour at 10% (§3.2 `line`). */
    fun lineColor(preset: String?): Int = (colors(preset).text and 0x00FFFFFF) or 0x1A000000
}
