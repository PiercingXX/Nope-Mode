package com.piercingxx.nopemode.schedule

import com.piercingxx.nopemode.schedule.Reconciler.Plan
import java.time.Instant
import java.time.ZoneId

/**
 * WS6 — the pure "which single boundary to arm" decision (design §7.2).
 *
 * The scheduler arms exactly ONE exact alarm at a time ("next boundary only;
 * recompute and re-arm after each fire"). [Plan] can carry two candidate
 * triggers — the next schedule boundary and the override-expiry instant — and
 * this object picks the earlier of the two. Pure — no `android.*`, so it is
 * JVM-provable and covered by [AlarmModeTest].
 */
object AlarmMode {

    /**
     * The single instant to arm next, or null when there is nothing to arm (no
     * schedule boundary and no override expiry). Boundaries are compared in the
     * [zone] the plan was derived in.
     */
    fun nextTrigger(plan: Plan, zone: ZoneId): Instant? {
        val boundary = plan.nextBoundary?.atZone(zone)?.toInstant()
        val expiry = plan.overrideExpiry
        return earlier(boundary, expiry)
    }

    private fun earlier(a: Instant?, b: Instant?): Instant? = when {
        a == null -> b
        b == null -> a
        else -> if (a.isBefore(b)) a else b
    }
}