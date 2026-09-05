package com.piercingxx.nopemode.ui

/**
 * T5 — the honest empty state for the blocked-app picker (design §11).
 *
 * The old behaviour showed "No apps match." whenever the list was empty — a lie
 * on a fresh device, where there is nothing to match against. This decides what
 * the picker should actually say, telling "there are no apps to pick" apart
 * from "your search matched nothing". Pure — no `android.*` — so the decision
 * is JVM-provable ([AppPickerEmptyStateTest]).
 */
object AppPickerEmptyState {

    /** Why the picker list is empty. */
    enum class Kind {
        /** No launchable apps are installed at all — nothing to match. */
        NO_APPS,
        /** Apps exist, but the current search matched none of them. */
        SEARCH_NO_MATCH,
    }

    /**
     * The honest reason the list is empty, or null when it is not empty.
     *
     * [installedCount] is how many launchable apps were discovered; [rowCount]
     * is how many rows the list would show. When apps exist but nothing is
     * shown, the only way there is that a search filtered them all out.
     */
    fun kind(installedCount: Int, rowCount: Int): Kind? = when {
        rowCount > 0 -> null
        installedCount == 0 -> Kind.NO_APPS
        else -> Kind.SEARCH_NO_MATCH
    }
}