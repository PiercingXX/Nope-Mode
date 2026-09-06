package com.piercingxx.nopemode.ui

/**
 * T5 — the honest empty state for the blocked-app picker (design §11).
 *
 * The old behaviour showed "No apps match." whenever the list was empty — a lie
 * on a fresh device, where there is nothing to match against, and a lie on
 * GrapheneOS when QUERY_ALL_PACKAGES is denied and the query returns nothing.
 * This decides what the picker should actually say. Pure — no `android.*` — so
 * the decision is JVM-provable ([AppPickerEmptyStateTest]).
 */
object AppPickerEmptyState {

    /** Why the picker list is empty. */
    enum class Kind {
        /** No launchable apps are installed at all — nothing to match. */
        NO_APPS,
        /** Apps exist, but the current search matched none of them. */
        SEARCH_NO_MATCH,
        /**
         * The package query returned nothing because QUERY_ALL_PACKAGES is
         * denied (GrapheneOS special-app-access toggle).
         */
        QUERY_ALL_PACKAGES_DENIED,
    }

    /**
     * The honest reason the list is empty, or null when it is not empty.
     *
     * [installedCount] is how many launchable apps were discovered; [rowCount]
     * is how many rows the list would show. [queryAllPackagesGranted] is the
     * QUERY_ALL_PACKAGES check. An empty query under a denial is not "no apps
     * found" and not a search miss.
     */
    fun kind(
        installedCount: Int,
        rowCount: Int,
        queryAllPackagesGranted: Boolean = true,
    ): Kind? = when {
        rowCount > 0 -> null
        installedCount == 0 && !queryAllPackagesGranted -> Kind.QUERY_ALL_PACKAGES_DENIED
        installedCount == 0 -> Kind.NO_APPS
        else -> Kind.SEARCH_NO_MATCH
    }
}