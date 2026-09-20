package com.piercingxx.nopemode

/**
 * Resyncs Nope-Mode's color tokens from `piercingxx-branding` when the app is
 * still on the old palette (BRAND-GUIDE.md §3, §7.7 — consume the tokens, don't
 * retype hexes).
 *
 * The app's runtime token set is the background-preset ground colours in
 * [com.piercingxx.nopemode.ui.BackgroundTheme.PRESETS]. That set can fall behind
 * the branding repo when a palette rework lands upstream (the reserved-white
 * rework renamed `graphite` from `#111827` to `#131316`, replaced the
 * cyan/emerald/sky product accents with the reserved-white system, and
 * re-derived the status colours). This object is the single source of truth for
 * those tokens and the guard that catches a stale copy.
 *
 * Pure — no `android.*`, so the palette and the old-set detection are
 * JVM-provable. [com.piercingxx.nopemode.ui.BackgroundTheme] reads its preset
 * grounds from [CURRENT] rather than a second hardcoded copy, so a rework in
 * the branding repo is applied here once and every screen follows.
 */
object ResyncTokens {

    /**
     * The current branding tokens: the seven background-preset ground colours
     * from `piercingxx-branding/tokens/android-colors.xml` (§3.3), keyed by the
     * same preset keys [com.piercingxx.nopemode.ui.BackgroundTheme.PRESETS]
     * uses.
     */
    val CURRENT: Map<String, Int> = mapOf(
        "amoled" to 0xFF000000.toInt(),
        "graphite" to 0xFF131316.toInt(),
        "forest" to 0xFF10261B.toInt(),
        "ocean" to 0xFF0F1C2E.toInt(),
        "burgundy" to 0xFF2A1018.toInt(),
        "paper" to 0xFFF3EEE2.toInt(),
        "mist" to 0xFFE6EDF5.toInt(),
    )

    /**
     * The old set the app shipped before the reserved-white rework: the same
     * seven keys, with the pre-rework grounds. Only `graphite` differs in the
     * background set — the rework's real churn was the product accents and
     * status colours, which live in `brand_colors.xml`, not in the presets —
     * but the whole map is kept so the guard is exact rather than a single
     * spot-check.
     */
    val OLD: Map<String, Int> = mapOf(
        "amoled" to 0xFF000000.toInt(),
        "graphite" to 0xFF111827.toInt(),
        "forest" to 0xFF10261B.toInt(),
        "ocean" to 0xFF0F1C2E.toInt(),
        "burgundy" to 0xFF2A1018.toInt(),
        "paper" to 0xFFF3EEE2.toInt(),
        "mist" to 0xFFE6EDF5.toInt(),
    )

    /** True when [colors] is still the old palette rather than [CURRENT]. */
    fun isOld(colors: Map<String, Int>): Boolean = colors == OLD

    /**
     * The resync: if [colors] is the old set, return the current branding
     * tokens; otherwise return [colors] unchanged. A palette that is already
     * current is left alone — snapping a live theme to a stale copy would be
     * the opposite of a resync.
     */
    fun resyncIfOld(colors: Map<String, Int>): Map<String, Int> =
        if (isOld(colors)) CURRENT else colors
}