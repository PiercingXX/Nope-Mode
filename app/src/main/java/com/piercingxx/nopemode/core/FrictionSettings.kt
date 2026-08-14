package com.piercingxx.nopemode.core

/**
 * The user-configurable friction and ringer settings (design §9, §18.5).
 *
 * Pure — no `android.*`, so every rule that consumes it stays JVM-provable.
 * [BreakPolicy] used to hardcode the interval and budget internally; threading
 * them through here is what §9 asks for ("both user-configurable"), with the
 * old constants kept as the defaults.
 *
 * Defaults, and why:
 *  - **5 minute break** — Focus Mode's shortest offer, and the one that is
 *    genuinely a break rather than a surrender (design §1).
 *  - **30 minute minimum interval** — stops 5-minute breaks being chained into
 *    an unbounded evening (§9).
 *  - **3 breaks per window** — exhausted means exhausted.
 *  - **Quiet Ringer on**, **repeat callers allowed** — §18.5. A second call
 *    from the same number is the closest thing to an emergency signal the
 *    platform offers, and missing a real emergency is worse than one unwanted
 *    ring.
 */
data class FrictionSettings(
    val breakDurationMinutes: Int = DEFAULT_BREAK_MINUTES,
    val minIntervalMinutes: Int = BreakPolicy.DEFAULT_MIN_INTERVAL_MINUTES,
    val breakBudgetPerWindow: Int = BreakPolicy.DEFAULT_BUDGET,
    val quietRingerEnabled: Boolean = true,
    val allowRepeatCallers: Boolean = true,
) {
    companion object {
        const val DEFAULT_BREAK_MINUTES: Int = 5

        /** The break lengths offered in the UI (design §1, mirroring Focus Mode). */
        val BREAK_CHOICES: List<Int> = listOf(5, 15, 30)
    }
}
