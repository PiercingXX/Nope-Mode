package com.piercingxx.nopemode.service

/**
 * T8 — WS11 Quiet Ringer: the pure decision slice of the zen policy (design §18).
 * Pure — no `android.*` at this layer, so it is JVM-provable and covered by
 * [ZenPolicyBuilderTest].
 *
 * The `AutomaticZenRule` is a **global** interruption filter. Android has no
 * "ringer only" mode: Priority DND suppresses anything that is not an allowed
 * category. Leaving other fields UNSET inherits the user's DND defaults
 * (AOSP: reminders and events off) and silences apps the user never picked —
 * the §18.1 failure.
 *
 * Closest honest mapping: allow every non-call category the API exposes, and
 * restrict **calls** to starred contacts. Repeat callers follow the user
 * toggle. The UI must say this uses Do Not Disturb.
 */
object ZenPolicyBuilder {

    /** How incoming calls are treated while the rule is active. */
    enum class CallAccess { STARRED_ONLY, ALL }

    /**
     * The pure, framework-free description of the zen rule's policy. The
     * Android wiring ([RingerPolicy]) maps this onto [android.service.notification.ZenPolicy].
     */
    data class Config(
        /** How calls are treated. [CallAccess.STARRED_ONLY] when quieting. */
        val callAccess: CallAccess,
        /** Whether a second call from the same number may ring through. */
        val repeatCallersAllowed: Boolean,
        /**
         * When true, every non-call interruption category is allowed so the
         * rule does not inherit the user's DND defaults. Always true: DND
         * cannot be ringer-only, so this is the least-harmful mapping.
         */
        val allowOtherInterruptions: Boolean,
    )

    /**
     * Build the policy description from the two Quiet Ringer settings.
     *
     * When [quietRingerEnabled] is false the rule is off and calls are
     * unrestricted. When enabled, calls are starred-only; other interruptions
     * are explicitly allowed.
     */
    fun build(quietRingerEnabled: Boolean, allowRepeatCallers: Boolean): Config =
        if (!quietRingerEnabled) {
            Config(
                callAccess = CallAccess.ALL,
                repeatCallersAllowed = allowRepeatCallers,
                allowOtherInterruptions = true,
            )
        } else {
            Config(
                callAccess = CallAccess.STARRED_ONLY,
                repeatCallersAllowed = allowRepeatCallers,
                allowOtherInterruptions = true,
            )
        }
}