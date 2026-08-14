package com.piercingxx.nopemode.admin

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The text shown when a suspended app is opened (design §11).
 *
 * On the device-owner tier the dialog itself belongs to the platform — it is
 * `com.android.settings/.enterprise.ActionDisabledByAdminDialog`, rendered by
 * Settings with the system theme. Its layout, colours and typeface are not ours
 * to set, and there is no API that makes them so. Its *words* are: a device
 * owner's short support message replaces the stock
 * "For more info, contact your IT admin".
 *
 * So this is the one part of that screen Nope-Mode controls, and it should not
 * waste it. "Blocked by work policy" is actively wrong here — there is no work
 * policy and no IT admin, just the user and a schedule they set.
 *
 * Voice per BRAND-GUIDE.md §6: blunt, dense, no marketing gloss. Design §11
 * asks the blocked screen to be one word — *Nope.* — and then the useful fact,
 * which is when it lifts.
 *
 * Pure — no `android.*`, so the wording is JVM-provable.
 */
object BlockedMessage {

    /** The platform truncates a long support message, so stay well under it. */
    const val MAX_LENGTH: Int = 200

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    /**
     * The message for the current state, or null when nothing is blocked and
     * the message should be cleared.
     *
     * [endsAt] is when the block lifts; null means it is on indefinitely, which
     * is said plainly rather than dressed up with a time that will not happen.
     */
    fun shortSupportMessage(isActive: Boolean, endsAt: LocalDateTime?): String? {
        if (!isActive) return null
        val tail = endsAt?.let { "Back at ${TIME.format(it)}." } ?: "On until you turn it off."
        return "Nope. $tail".take(MAX_LENGTH)
    }
}
