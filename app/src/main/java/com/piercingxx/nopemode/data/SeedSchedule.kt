package com.piercingxx.nopemode.data

/**
 * The pure seed-schedule factory (design §10, §7). Returns the single `Schedule`
 * the seed migration must insert on a fresh install: 20:00 -> 08:00, all seven
 * days, enabled.
 *
 * A Room entity is a plain annotated data class and instantiates fine on the
 * JVM, so this factory is JVM-provable. The *migration execution* itself is
 * device-gated and deferred to operator on-device verification.
 */
object SeedSchedule {

    /** Bit 0 = Monday .. bit 6 = Sunday — all seven days set. */
    const val ALL_DAYS_MASK: Int = 0b1111111

    /** 20:00 as minutes-of-day. */
    const val DEFAULT_START_MINUTE: Int = 1200

    /** 08:00 as minutes-of-day. */
    const val DEFAULT_END_MINUTE: Int = 480

    /**
     * The default schedule the seed migration must insert: active 20:00 -> 08:00
     * (a midnight-crossing window), every day of the week, enabled.
     */
    fun defaultSchedule(): Schedule = Schedule(
        id = 1L,
        startMinuteOfDay = DEFAULT_START_MINUTE,
        endMinuteOfDay = DEFAULT_END_MINUTE,
        daysMask = ALL_DAYS_MASK,
        enabled = true
    )
}