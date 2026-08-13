package com.piercingxx.nopemode.schedule

import com.piercingxx.nopemode.core.NextBoundary
import com.piercingxx.nopemode.core.NopeController
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.data.Schedule
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WS6 — the pure derive-and-diff core (design §7.1). `reconcile()` is the seam
 * the whole app hangs off: given the world as it is, it decides what should be
 * true and returns the plan to get there. Pure — no `android.*`; the `Enforcer`
 * is injected at the Android layer (T5), so the decision is testable without a
 * device.
 *
 * Inputs are (now, schedules, override, blocked list, current suspend_record,
 * current tier). Active state is derived via [NopeController.derive] — state is
 * always obtained by calling this, never by accumulating alarm events (D8), so a
 * missed, late, or duplicated alarm cannot corrupt state.
 *
 * The plan also computes the boundaries to arm:
 *  - [Plan.nextBoundary] — the next schedule boundary, via [NextBoundary.next].
 *    It is null when an indefinite `ForceOn(null)` pins state active, because a
 *    schedule boundary cannot then flip the state.
 *  - [Plan.overrideExpiry] — the instant the current override expires and the
 *    state must be re-derived, a flip [NextBoundary] does not model (it only
 *    knows schedules). For an indefinite `ForceOn(null)` there is no expiry, but
 *    the override-expiry path is still armed at the next schedule boundary as a
 *    safety net so the alarm re-fires and re-derives in case the override was
 *    removed.
 */
object Reconciler {

    /** The enforcement tier (design §5). Suspension is only possible on the
     *  device-owner tier; the fallback tier cannot use `setPackagesSuspended`. */
    enum class Tier { DEVICE_OWNER, FALLBACK }

    /**
     * The decision: what to do now and when to wake up next.
     *
     * [toSuspend] and [toRelease] are the diff of the desired suspended set
     * against the current `suspend_record`. An empty diff means nothing to do.
     */
    data class Plan(
        val active: Boolean,
        val toSuspend: Set<String>,
        val toRelease: Set<String>,
        val nextBoundary: LocalDateTime?,
        val overrideExpiry: Instant?,
    )

    /**
     * Compute the reconciliation plan for the given world state.
     *
     * The desired suspended set is the [blocked] list while [active] — but only
     * on the [Tier.DEVICE_OWNER] tier; on [Tier.FALLBACK] nothing can be
     * suspended, so everything currently suspended is released.
     */
    fun reconcile(
        now: LocalDateTime,
        schedules: List<Schedule>,
        override: Override,
        blocked: Set<String>,
        currentSuspended: Set<String>,
        tier: Tier,
    ): Plan {
        val active = NopeController.derive(now, schedules, override)

        val desired: Set<String> = when {
            tier == Tier.FALLBACK -> emptySet()
            active -> blocked
            else -> emptySet()
        }
        val toSuspend = desired - currentSuspended
        val toRelease = currentSuspended - desired

        val scheduleBoundary = NextBoundary.next(now, schedules)
        val nextBoundary = if (override is Override.ForceOn && override.until == null) {
            // Indefinite ForceOn pins state active: a schedule boundary cannot
            // flip it, so do not arm the schedule boundary.
            null
        } else {
            scheduleBoundary
        }
        val overrideExpiry = overrideExpiry(now, override, scheduleBoundary)

        return Plan(
            active = active,
            toSuspend = toSuspend,
            toRelease = toRelease,
            nextBoundary = nextBoundary,
            overrideExpiry = overrideExpiry,
        )
    }

    private fun overrideExpiry(
        now: LocalDateTime,
        override: Override,
        scheduleBoundary: LocalDateTime?,
    ): Instant? {
        val nowInstant = now.atZone(ZoneId.systemDefault()).toInstant()
        return when (override) {
            is Override.Break ->
                if (nowInstant < override.until) override.until else null
            is Override.ForceOn -> {
                val until = override.until
                if (until != null && nowInstant < until) {
                    until
                } else if (until == null) {
                    // Indefinite: no expiry, but arm the override-expiry path at
                    // the next schedule boundary as a safety net so the alarm
                    // re-fires and re-derives in case the override was removed.
                    scheduleBoundary?.atZone(ZoneId.systemDefault())?.toInstant()
                } else {
                    null // already expired -> re-derive from schedule
                }
            }
            Override.None -> null
        }
    }
}