package com.piercingxx.nopemode.enforce

/**
 * WS5 — pure selection/exclusion logic for the blocked-app picker (design §4.1).
 *
 * The platform refuses to suspend certain packages outright; suspending others
 * is permitted but dangerous. The picker must exclude them at SELECTION time,
 * not fail at enforcement time — and show the reason inline, so the user never
 * assumes a package is blocked when it isn't.
 *
 * Pure — no `android.*` at this layer. The caller discovers the protected
 * packages via PackageManager / DevicePolicyManager (Android-dependent) and
 * hands them in; this object only does the selection math.
 */
object SuspendablePackages {

    /** Why a package cannot be bulk-selected. */
    sealed interface Exclusion {

        /** The platform refuses to suspend it; never selectable. */
        data class Hard(val reason: String) : Exclusion

        /** Permitted, but requires explicit confirmation; never via bulk-select. */
        data class RequiresConfirmation(val reason: String) : Exclusion
    }

    data class Selection(
        /** Packages that can be bulk-selected. */
        val suspendable: Set<String>,
        /** package -> why it is excluded. */
        val excluded: Map<String, Exclusion>,
    )

    /**
     * Compute what of [blocked] (restricted to [installed]) is suspendable.
     *
     * [hardBlocked] maps packages the platform refuses to suspend to the reason
     * shown inline (design §4.1: device admins including self, the active
     * launcher, the package installer/uninstaller/verifier, the default dialer,
     * the permission controller, and the active input method). 
     * [requiresConfirmation] maps packages that the platform permits but which
     * need an explicit confirmation — the default SMS handler, which silences
     * 2FA codes and must never be reachable via bulk-select.
     */
    fun select(
        installed: Set<String>,
        blocked: Set<String>,
        hardBlocked: Map<String, String>,
        requiresConfirmation: Map<String, String>,
    ): Selection {
        val excluded = LinkedHashMap<String, Exclusion>()
        val suspendable = LinkedHashSet<String>()
        for (pkg in installed) {
            if (pkg !in blocked) continue
            val hard = hardBlocked[pkg]
            if (hard != null) {
                excluded[pkg] = Exclusion.Hard(hard)
                continue
            }
            val confirm = requiresConfirmation[pkg]
            if (confirm != null) {
                excluded[pkg] = Exclusion.RequiresConfirmation(confirm)
                continue
            }
            suspendable.add(pkg)
        }
        return Selection(suspendable, excluded)
    }
}