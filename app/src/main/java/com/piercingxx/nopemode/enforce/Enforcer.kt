package com.piercingxx.nopemode.enforce

/**
 * WS5 — the seam between "what should be true" and "what is true" (design §5).
 *
 * [NopeController] computes the desired set of suspended packages and hands it
 * to an [Enforcer]; the enforcer makes the platform match. There are two tiers,
 * chosen whenever admin state changes:
 *
 *     if (dpm.isDeviceOwnerApp(packageName)) SuspendEnforcer else FallbackEnforcer
 *
 * The active tier must be visible in the UI at all times — a user on the
 * fallback tier is getting weaker protection and has to know it (R8).
 */
interface Enforcer {

    /**
     * Make the device match [desired].
     *
     * The result carries the packages that FAILED, not just a boolean (R8): a
     * package the platform refused to suspend must never be reported as blocked.
     */
    fun apply(desired: Set<String>): Result

    /**
     * The outcome of an [apply] call.
     *
     * [failed] holds every package that should be suspended but was not. An
     * empty [failed] means the desired set was fully enforced.
     */
    data class Result(
        val failed: Set<String> = emptySet(),
    ) {
        /** True when every desired package was enforced. */
        val success: Boolean get() = failed.isEmpty()
    }
}