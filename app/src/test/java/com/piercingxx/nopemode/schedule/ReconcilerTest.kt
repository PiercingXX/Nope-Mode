package com.piercingxx.nopemode.schedule

import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.data.Schedule
import com.piercingxx.nopemode.schedule.Reconciler.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * T4 — Reconciler: the pure derive-and-diff core of WS6 (design §7.1). Asserts
 * idempotence, the suspend/release diff, the override-expiry boundary armed
 * alongside the schedule boundary, and that an indefinite `ForceOn(null)` arms
 * no schedule boundary but does arm the override-expiry path.
 */
class ReconcilerTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val ALL_DAYS = (1 shl 7) - 1

    // A Monday-start 20:00 -> 08:00 midnight-crossing window (the default).
    private val defaultWindow = Schedule(id = 1, startMinuteOfDay = 1200, endMinuteOfDay = 480, daysMask = ALL_DAYS)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): LocalDateTime = LocalDateTime.of(y, mo, d, h, mi)

    private fun instant(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        at(y, mo, d, h, mi).atZone(zone).toInstant()

    // ---- Idempotence ----

    @Test
    fun `reconcile twice yields the same plan`() {
        val inputs = {
            Reconciler.reconcile(
                now = at(2026, 8, 10, 21, 0),
                schedules = listOf(defaultWindow),
                override = Override.None,
                blocked = setOf("com.example.games", "com.example.social"),
                currentSuspended = setOf("com.example.games"),
                tier = Tier.DEVICE_OWNER,
            )
        }
        assertEquals(inputs(), inputs())
    }

    // ---- Diff: suspend newly-blocked, release newly-unblocked ----

    @Test
    fun `suspends newly-blocked packages when active`() {
        val plan = Reconciler.reconcile(
            now = at(2026, 8, 10, 21, 0), // inside the window -> active
            schedules = listOf(defaultWindow),
            override = Override.None,
            blocked = setOf("com.example.games", "com.example.social"),
            currentSuspended = setOf("com.example.games"),
            tier = Tier.DEVICE_OWNER,
        )
        assertTrue(plan.active)
        assertEquals(setOf("com.example.social"), plan.toSuspend)
        assertEquals(emptySet<String>(), plan.toRelease)
    }

    @Test
    fun `releases newly-unblocked packages when inactive`() {
        val plan = Reconciler.reconcile(
            now = at(2026, 8, 10, 12, 0), // outside the window -> inactive
            schedules = listOf(defaultWindow),
            override = Override.None,
            blocked = setOf("com.example.games", "com.example.social"),
            currentSuspended = setOf("com.example.games", "com.example.social"),
            tier = Tier.DEVICE_OWNER,
        )
        assertTrue(!plan.active)
        assertEquals(emptySet<String>(), plan.toSuspend)
        assertEquals(setOf("com.example.games", "com.example.social"), plan.toRelease)
    }

    @Test
    fun `no diff when desired already matches the record`() {
        val plan = Reconciler.reconcile(
            now = at(2026, 8, 10, 21, 0),
            schedules = listOf(defaultWindow),
            override = Override.None,
            blocked = setOf("com.example.games"),
            currentSuspended = setOf("com.example.games"),
            tier = Tier.DEVICE_OWNER,
        )
        assertEquals(emptySet<String>(), plan.toSuspend)
        assertEquals(emptySet<String>(), plan.toRelease)
    }

    // ---- Fallback tier cannot suspend ----

    @Test
    fun `fallback tier releases everything and suspends nothing`() {
        val plan = Reconciler.reconcile(
            now = at(2026, 8, 10, 21, 0), // active, but fallback cannot suspend
            schedules = listOf(defaultWindow),
            override = Override.None,
            blocked = setOf("com.example.games"),
            currentSuspended = setOf("com.example.games"),
            tier = Tier.FALLBACK,
        )
        assertTrue(plan.active)
        assertEquals(emptySet<String>(), plan.toSuspend)
        assertEquals(setOf("com.example.games"), plan.toRelease)
    }

    // ---- Boundaries ----

    @Test
    fun `override-expiry boundary is armed alongside the schedule boundary`() {
        // Break running until 21:20 while the schedule is active. Both the next
        // schedule boundary (Tuesday 08:00, the wrap end) and the override expiry
        // (21:20) must be armed.
        val breakUntil = instant(2026, 8, 10, 21, 20)
        val plan = Reconciler.reconcile(
            now = at(2026, 8, 10, 21, 0),
            schedules = listOf(defaultWindow),
            override = Override.Break(breakUntil),
            blocked = setOf("com.example.games"),
            currentSuspended = emptySet(),
            tier = Tier.DEVICE_OWNER,
        )
        assertNotNull(plan.nextBoundary)
        assertEquals(breakUntil, plan.overrideExpiry)
    }

    @Test
    fun `ForceOn null arms no schedule boundary but does arm the override-expiry path`() {
        // Indefinite ForceOn pins state active, so the schedule boundary is not
        // armed — but the override-expiry path is armed at the next schedule
        // boundary as a safety net.
        val plan = Reconciler.reconcile(
            now = at(2026, 8, 10, 12, 0), // schedule inactive, ForceOn forces active
            schedules = listOf(defaultWindow),
            override = Override.ForceOn(null),
            blocked = setOf("com.example.games"),
            currentSuspended = emptySet(),
            tier = Tier.DEVICE_OWNER,
        )
        assertTrue(plan.active)
        assertNull(plan.nextBoundary)
        // The next schedule boundary is Monday 20:00; the override-expiry path
        // is armed there so the alarm re-fires and re-derives.
        assertEquals(instant(2026, 8, 10, 20, 0), plan.overrideExpiry)
    }

    @Test
    fun `bounded ForceOn arms the override-expiry boundary`() {
        val until = instant(2026, 8, 10, 22, 0)
        val plan = Reconciler.reconcile(
            now = at(2026, 8, 10, 21, 0),
            schedules = listOf(defaultWindow),
            override = Override.ForceOn(until),
            blocked = setOf("com.example.games"),
            currentSuspended = emptySet(),
            tier = Tier.DEVICE_OWNER,
        )
        assertTrue(plan.active)
        assertEquals(until, plan.overrideExpiry)
        assertNotNull(plan.nextBoundary)
    }

    @Test
    fun `no override arms no override-expiry boundary`() {
        val plan = Reconciler.reconcile(
            now = at(2026, 8, 10, 21, 0),
            schedules = listOf(defaultWindow),
            override = Override.None,
            blocked = setOf("com.example.games"),
            currentSuspended = emptySet(),
            tier = Tier.DEVICE_OWNER,
        )
        assertNull(plan.overrideExpiry)
        assertNotNull(plan.nextBoundary)
    }
}