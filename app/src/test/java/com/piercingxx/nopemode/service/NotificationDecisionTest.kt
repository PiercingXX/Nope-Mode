package com.piercingxx.nopemode.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T9 — WS9 FallbackEnforcer: the pure notification decision ([NotificationDecision]).
 *
 * The fallback tier cannot suspend packages, so it suppresses notifications
 * for blocked apps while Nope-Mode is active (design §8.2a). The Android
 * listener ([NopeNotificationListener]) is deferred to the operator's on-device
 * check; this pins the decision logic that is JVM-provable: cancel-on-blocked-
 * active, no-op when inactive / not blocked / suspendable, and the debounce
 * window.
 */
class NotificationDecisionTest {

    private val blocked = setOf("com.example.blocked")

    // ---- cancel-on-blocked-active ----

    @Test
    fun `cancels a blocked package when active and not suspendable`() {
        assertEquals(
            NotificationDecision.Decision.Cancel,
            NotificationDecision.decide(
                packageName = "com.example.blocked",
                active = true,
                blocked = blocked,
                isSuspendable = false,
                preferSnooze = false,
                lastActionAt = null,
                nowMillis = 1_000L,
                debounceMillis = 5_000L,
            ),
        )
    }

    @Test
    fun `snoozes a blocked package when active and snoozing fits`() {
        assertEquals(
            NotificationDecision.Decision.Snooze(NotificationDecision.SNOOZE_DURATION_MILLIS),
            NotificationDecision.decide(
                packageName = "com.example.blocked",
                active = true,
                blocked = blocked,
                isSuspendable = false,
                preferSnooze = true,
                lastActionAt = null,
                nowMillis = 1_000L,
                debounceMillis = 5_000L,
            ),
        )
    }

    // ---- no-op when inactive or not blocked ----

    @Test
    fun `does nothing when inactive`() {
        assertEquals(
            NotificationDecision.Decision.NoOp,
            NotificationDecision.decide(
                packageName = "com.example.blocked",
                active = false,
                blocked = blocked,
                isSuspendable = false,
                preferSnooze = false,
                lastActionAt = null,
                nowMillis = 1_000L,
                debounceMillis = 5_000L,
            ),
        )
    }

    @Test
    fun `does nothing for a package that is not blocked`() {
        assertEquals(
            NotificationDecision.Decision.NoOp,
            NotificationDecision.decide(
                packageName = "com.example.other",
                active = true,
                blocked = blocked,
                isSuspendable = false,
                preferSnooze = false,
                lastActionAt = null,
                nowMillis = 1_000L,
                debounceMillis = 5_000L,
            ),
        )
    }

    @Test
    fun `does nothing for a suspendable package`() {
        assertEquals(
            NotificationDecision.Decision.NoOp,
            NotificationDecision.decide(
                packageName = "com.example.blocked",
                active = true,
                blocked = blocked,
                isSuspendable = true,
                preferSnooze = false,
                lastActionAt = null,
                nowMillis = 1_000L,
                debounceMillis = 5_000L,
            ),
        )
    }

    // ---- debounce window ----

    @Test
    fun `ignores a repeat trigger inside the debounce window`() {
        assertEquals(
            NotificationDecision.Decision.NoOp,
            NotificationDecision.decide(
                packageName = "com.example.blocked",
                active = true,
                blocked = blocked,
                isSuspendable = false,
                preferSnooze = false,
                lastActionAt = 1_000L,
                nowMillis = 3_000L, // 2s later, inside the 5s window
                debounceMillis = 5_000L,
            ),
        )
    }

    @Test
    fun `acts again once the debounce window has elapsed`() {
        assertEquals(
            NotificationDecision.Decision.Cancel,
            NotificationDecision.decide(
                packageName = "com.example.blocked",
                active = true,
                blocked = blocked,
                isSuspendable = false,
                preferSnooze = false,
                lastActionAt = 1_000L,
                nowMillis = 7_000L, // 6s later, outside the 5s window
                debounceMillis = 5_000L,
            ),
        )
    }
}