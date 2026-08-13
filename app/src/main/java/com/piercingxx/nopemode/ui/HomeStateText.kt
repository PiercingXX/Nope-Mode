package com.piercingxx.nopemode.ui

import com.piercingxx.nopemode.core.Override
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WS7 — the pure "what does the home screen say" slice of the UI (design §8).
 *
 * The home screen has three text regions: a big state headline, an enforcement
 * tier line, and (when not provisioned) the provisioning command. Deriving that
 * text is pure string logic — no `android.*` — so it is JVM-provable without a
 * device, mirroring how [com.piercingxx.nopemode.core.NopeController] keeps the
 * active/inactive decision pure.
 *
 * The headline reflects the derived state: active, inactive, or on a bounded
 * break (with the remaining countdown). The tier line is honest about which
 * enforcement tier is live — silently degrading would make the app claim
 * protection it isn't providing (design §5).
 */
object HomeStateText {

    /** The big headline: what Nope-Mode is doing right now. */
    fun stateText(isActive: Boolean, override: Override, now: Instant = Instant.now()): String {
        if (override is Override.Break && now < override.until) {
            val minutes = Duration.between(now, override.until).toMinutes().coerceAtLeast(0)
            return "On a break — resumes in $minutes min"
        }
        return if (isActive) "Nope-Mode is ON" else "Nope-Mode is OFF"
    }

    /** The enforcement tier line (design §5) — honest about what tier is live. */
    fun tierText(isDeviceOwner: Boolean): String =
        if (isDeviceOwner) {
            "Enforcement: Device owner — apps can be fully suspended."
        } else {
            "Enforcement: Limited — not provisioned as device owner. " +
                "Notifications can only be dismissed after they post, " +
                "so a sound may play first."
        }

    /**
     * The provisioning command, or an empty string when already provisioned.
     * The command can only be run on a device with zero accounts, so it is
     * surfaced verbatim rather than glossed over (design §2.2).
     */
    fun provisionText(isDeviceOwner: Boolean, provisioningCommand: String): String =
        if (isDeviceOwner) "" else provisioningCommand

    /**
     * The remaining break countdown as whole minutes, for the blocked screen.
     * A break already past (or a clock that moved) clamps to zero.
     */
    fun breakCountdownMinutes(until: Instant, now: Instant = Instant.now()): Long =
        Duration.between(now, until).toMinutes().coerceAtLeast(0)

    /** The instant a break ends, for the blocked screen's countdown label. */
    fun breakEndTime(until: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(until, zone)
}