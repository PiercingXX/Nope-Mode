package com.piercingxx.nopemode.core

import java.time.Instant

/**
 * A user override on top of the schedule (design §6).
 *
 * [Break] is ALWAYS bounded — there is no way to express "off until I say so"
 * (D7). That is the difference between a schedule and a suggestion.
 */
sealed interface Override {

    /** No override in effect; the schedule alone decides. */
    data object None : Override

    /** Force Nope-Mode active. `until == null` means indefinite. */
    data class ForceOn(val until: Instant?) : Override

    /** Take a bounded break; auto-resumes when [until] passes. */
    data class Break(val until: Instant) : Override
}