package com.piercingxx.nopemode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * T1 — WS0 build-correctness: `java.time` on minSdk 24 with core library
 * desugaring.
 *
 * `core/` uses `LocalDateTime` / `Instant` / `Duration`, which are API 26+.
 * `minSdk` is 24, so `app/build.gradle` must enable `coreLibraryDesugaring`
 * (with `desugar_jdk_libs`) or those classes throw `NoClassDefFoundError` on
 * API 24–25 devices. The desugared runtime must produce byte-identical results
 * to the JVM's native `java.time`, so this test pins the exact operations the
 * app's pure logic depends on — the same arithmetic that `ScheduleEvaluator`,
 * `NextBoundary`, and `BreakPolicy` build on.
 *
 * This is a JVM unit test: it runs against the host JDK's `java.time`, which is
 * the same behaviour desugaring guarantees on-device. If desugaring were
 * removed or misconfigured, `assembleDebug` would still compile (the desugar
 * step is what fails), but this test keeps the contract explicit and greppable.
 */
class DesugaredTimeTest {

    // ---- LocalDateTime construction and arithmetic (ScheduleEvaluator/NextBoundary) ----

    @Test
    fun `minute-of-day from LocalDateTime matches the core convention`() {
        // 20:00 -> 1200, 08:00 -> 480, per Schedule.startMinuteOfDay.
        val evening = LocalDateTime.of(2026, 8, 10, 20, 0)
        assertEquals(1200, evening.hour * 60 + evening.minute)
        val morning = LocalDateTime.of(2026, 8, 11, 8, 0)
        assertEquals(480, morning.hour * 60 + morning.minute)
    }

    @Test
    fun `day-of-week value maps to the daysMask bit convention`() {
        // Bit 0 = Monday .. bit 6 = Sunday (dayOfWeek.value - 1).
        assertEquals(0, LocalDate.of(2026, 8, 10).dayOfWeek.value - 1) // Monday
        assertEquals(6, LocalDate.of(2026, 8, 16).dayOfWeek.value - 1) // Sunday
    }

    @Test
    fun `plusDays wraps across a week boundary`() {
        // A Monday-only window's next start is at most 7 days out; NextBoundary
        // scans start days -1..8. Sunday 16 Aug + 1 = Monday 17 Aug.
        val sunday = LocalDate.of(2026, 8, 16)
        assertEquals(LocalDate.of(2026, 8, 17), sunday.plusDays(1))
        assertEquals(LocalDate.of(2026, 8, 10), sunday.minusDays(6))
    }

    @Test
    fun `atTime builds a boundary instant from minute-of-day`() {
        val day = LocalDate.of(2026, 8, 10)
        assertEquals(LocalDateTime.of(2026, 8, 10, 20, 0), day.atTime(1200 / 60, 1200 % 60))
        assertEquals(LocalDateTime.of(2026, 8, 10, 0, 0), day.atTime(0 / 60, 0 % 60))
    }

    @Test
    fun `isAfter distinguishes a strict future boundary`() {
        val now = LocalDateTime.of(2026, 8, 11, 12, 0)
        assertTrue(LocalDateTime.of(2026, 8, 11, 20, 0).isAfter(now))
        assertFalse(LocalDateTime.of(2026, 8, 11, 12, 0).isAfter(now)) // boundary at now is not future
        assertFalse(LocalDateTime.of(2026, 8, 11, 8, 0).isAfter(now))
    }

    // ---- Instant / Duration / ZoneId (BreakPolicy) ----

    @Test
    fun `duration between instants measures elapsed time`() {
        val start = Instant.parse("2026-08-10T19:30:00Z")
        val now = Instant.parse("2026-08-10T20:00:00Z")
        val elapsed = Duration.between(start, now)
        assertEquals(Duration.ofMinutes(30), elapsed)
        assertFalse(elapsed.isNegative)
        assertTrue(elapsed >= Duration.ofMinutes(30))
    }

    @Test
    fun `clock moved backwards yields a negative duration`() {
        val start = Instant.parse("2026-08-10T20:00:00Z")
        val now = Instant.parse("2026-08-10T19:00:00Z")
        assertTrue(Duration.between(start, now).isNegative)
    }

    @Test
    fun `local wall clock converts to epoch millis via system zone`() {
        // BreakPolicy.budgetRemaining compares break timestamps against the
        // window start converted through ZoneId.systemDefault().
        val zone = ZoneId.systemDefault()
        val windowStart = LocalDateTime.of(2026, 8, 10, 20, 0)
        val millis = windowStart.atZone(zone).toInstant().toEpochMilli()
        assertEquals(windowStart, Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime())
    }

    @Test
    fun `local date time round-trips through an instant`() {
        val zone = ZoneId.systemDefault()
        val original = LocalDateTime.of(2026, 8, 10, 20, 0)
        val roundTripped = original.atZone(zone).toInstant().atZone(zone).toLocalDateTime()
        assertEquals(original, roundTripped)
    }
}