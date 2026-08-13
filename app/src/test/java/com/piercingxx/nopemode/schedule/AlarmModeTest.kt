package com.piercingxx.nopemode.schedule

import com.piercingxx.nopemode.schedule.Reconciler.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * T5 — AlarmMode: the pure "which single boundary to arm" decision of WS6
 * (design §7.2). Given a [Plan] with up to two candidate triggers (the next
 * schedule boundary and the override-expiry instant), [AlarmMode.nextTrigger]
 * arms the earlier of the two — exactly one alarm, never two. Pure — no
 * `android.*` at the tested layer.
 */
class AlarmModeTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): LocalDateTime =
        LocalDateTime.of(y, mo, d, h, mi)

    private fun instant(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        at(y, mo, d, h, mi).atZone(zone).toInstant()

    private fun plan(
        nextBoundary: LocalDateTime?,
        overrideExpiry: Instant?,
    ): Plan = Plan(
        active = true,
        toSuspend = emptySet(),
        toRelease = emptySet(),
        nextBoundary = nextBoundary,
        overrideExpiry = overrideExpiry,
    )

    @Test
    fun `arms the earlier of boundary and override expiry`() {
        val boundary = at(2026, 8, 11, 8, 0)   // Tuesday 08:00
        val expiry = instant(2026, 8, 10, 21, 20) // break ends 21:20 — sooner
        assertEquals(expiry, AlarmMode.nextTrigger(plan(boundary, expiry), zone))
    }

    @Test
    fun `arms the boundary when it comes before the override expiry`() {
        val boundary = at(2026, 8, 10, 21, 0)
        val expiry = instant(2026, 8, 10, 22, 0)
        assertEquals(boundary.atZone(zone).toInstant(), AlarmMode.nextTrigger(plan(boundary, expiry), zone))
    }

    @Test
    fun `arms the boundary when there is no override expiry`() {
        val boundary = at(2026, 8, 11, 8, 0)
        assertEquals(boundary.atZone(zone).toInstant(), AlarmMode.nextTrigger(plan(boundary, null), zone))
    }

    @Test
    fun `arms the override expiry when there is no schedule boundary`() {
        // An indefinite ForceOn pins state active: no schedule boundary is armed,
        // but the override-expiry safety net still is.
        val expiry = instant(2026, 8, 10, 20, 0)
        assertEquals(expiry, AlarmMode.nextTrigger(plan(null, expiry), zone))
    }

    @Test
    fun `arms nothing when neither boundary nor expiry is set`() {
        assertNull(AlarmMode.nextTrigger(plan(null, null), zone))
    }
}