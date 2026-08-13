package com.piercingxx.nopemode.service

/**
 * T8 — WS11 Quiet Ringer: the pure decision slice of the zen policy (design §18).
 * Pure — no `android.*` at this layer, so it is JVM-provable and covered by
 * [ZenPolicyBuilderTest].
 *
 * The `AutomaticZenRule` is a **global** interruption filter. It must never be
 * used to silence app notifications generally — the blocked-app list already
 * silences the apps it covers, and quieting anything else would break the
 * app's central promise that it only touches what you picked (§18.1). So this
 * builder only ever restricts the **ringer**:
 *
 * - Calls ring through from **starred contacts only**.
 * - Repeat callers ring through iff the user toggle is on (a built-in DND
 *   category, not something Nope-Mode implements).
 * - Every other category is left at its default and reported as untouched.
 */
object ZenPolicyBuilder {

    /** How incoming calls are treated while the rule is active. */
    enum class CallAccess { STARRED_ONLY, ALL }

    /**
     * The pure, framework-free description of the zen rule's policy. The
     * Android wiring ([RingerPolicy]) maps this onto
     * `android.app.NotificationManager.Policy`; keeping the description pure
     * lets the decision logic be unit-tested on the JVM.
     */
    data class Config(
        /** How calls are treated. [CallAccess.STARRED_ONLY] when quieting. */
        val callAccess: CallAccess,
        /** Whether a second call from the same number may ring through. */
        val repeatCallersAllowed: Boolean,
        /**
         * True iff any notification-category field (messages, reminders,
         * alarms, events, media, system, etc.) was touched. The rule exists to
         * restrict the ringer only, so this must stay false (§18.1).
         */
        val notificationCategoriesTouched: Boolean,
    )

    /**
     * Build the policy description from the two Quiet Ringer settings.
     *
     * When [quietRingerEnabled] is false the rule is off entirely and nothing
     * is restricted — the config reports the ringer unrestricted. When enabled
     * the ringer is limited to starred contacts, repeat callers follow the
     * user's [allowRepeatCallers] toggle, and no notification category is ever
     * touched.
     */
    fun build(quietRingerEnabled: Boolean, allowRepeatCallers: Boolean): Config =
        if (!quietRingerEnabled) {
            Config(
                callAccess = CallAccess.ALL,
                repeatCallersAllowed = allowRepeatCallers,
                notificationCategoriesTouched = false,
            )
        } else {
            Config(
                callAccess = CallAccess.STARRED_ONLY,
                repeatCallersAllowed = allowRepeatCallers,
                notificationCategoriesTouched = false,
            )
        }
}