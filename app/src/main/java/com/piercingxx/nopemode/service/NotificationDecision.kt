package com.piercingxx.nopemode.service

/**
 * WS9 — the pure decision slice of the FallbackEnforcer (design §8.2a).
 * Pure — no `android.*` at this layer, so it is JVM-provable and covered by
 * [NotificationDecisionTest].
 *
 * The fallback tier cannot suspend packages, so it suppresses notifications
 * for blocked apps while Nope-Mode is active. This object is the pure "should
 * I act, and how" — [NopeNotificationListener] maps the decision onto
 * `cancelNotification` / `snoozeNotification`.
 *
 * The listener only covers packages the suspend mechanism cannot reach
 * ([isSuspendable] == false): a suspendable package is already handled by the
 * device-owner tier, and the fallback must not double-suppress it.
 *
 * Debounce: repeated triggers for the same package within [decide]'s
 * `debounceMillis` are ignored, so a rapid re-post cannot spam the system.
 */
object NotificationDecision {

    /** Default snooze length when the notification fits snoozing. */
    const val SNOOZE_DURATION_MILLIS: Long = 60_000L

    /** What the listener should do about a posted notification. */
    sealed interface Decision {
        /** Do nothing — not active, not blocked, or inside the debounce window. */
        data object NoOp : Decision
        /** Cancel the notification outright. */
        data object Cancel : Decision
        /** Snooze the notification for [durationMillis]. */
        data class Snooze(val durationMillis: Long) : Decision
    }

    /**
     * Decide what to do about a notification posted by [packageName].
     *
     * @param packageName the posting app.
     * @param active whether Nope-Mode is derived-active right now.
     * @param blocked the set of user-blocked packages.
     * @param isSuspendable whether the device-owner tier can suspend this
     *   package; if so the listener leaves it alone.
     * @param preferSnooze whether the notification fits snoozing (the listener
     *   decides from the framework flags); cancel is the default.
     * @param lastActionAt the epoch-millis of the last action on this package,
     *   or null if none yet.
     * @param nowMillis current epoch-millis.
     * @param debounceMillis the minimum gap between actions on one package.
     */
    fun decide(
        packageName: String,
        active: Boolean,
        blocked: Set<String>,
        isSuspendable: Boolean,
        preferSnooze: Boolean,
        lastActionAt: Long?,
        nowMillis: Long,
        debounceMillis: Long,
    ): Decision {
        if (!active) return Decision.NoOp
        if (packageName !in blocked) return Decision.NoOp
        if (isSuspendable) return Decision.NoOp
        if (lastActionAt != null && nowMillis - lastActionAt < debounceMillis) return Decision.NoOp
        return if (preferSnooze) Decision.Snooze(SNOOZE_DURATION_MILLIS) else Decision.Cancel
    }
}