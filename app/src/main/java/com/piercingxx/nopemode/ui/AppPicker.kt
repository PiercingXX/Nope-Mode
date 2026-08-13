package com.piercingxx.nopemode.ui

import com.piercingxx.nopemode.enforce.SuspendablePackages.Exclusion

/**
 * WS7 — the pure half of the blocked-app picker (design §11, §4.1).
 *
 * [SuspendablePackages.select] answers "of the apps already blocked, which can
 * actually be suspended?". The picker needs the other direction: given every
 * installed app, show each one with its current state and, when it cannot be
 * selected, the reason inline. Design §4.1 is explicit that exclusion happens at
 * SELECTION time and is never silent — an app the platform will refuse must be
 * visible and disabled, not missing from the list.
 *
 * Pure — no `android.*`. The activity discovers installed apps and protected
 * packages via PackageManager ([ProtectedPackages]) and hands them in.
 */
object AppPicker {

    /** One installed app as the picker knows it, before any policy is applied. */
    data class InstalledApp(val packageName: String, val label: String)

    /** One row of the picker. */
    data class Row(
        val packageName: String,
        val label: String,
        val checked: Boolean,
        /** Null when freely selectable. */
        val exclusion: Exclusion?,
    ) {
        /** Hard exclusions can never be toggled. */
        val selectable: Boolean get() = exclusion !is Exclusion.Hard

        /** Shown under the label when the app is not freely selectable. */
        val reason: String?
            get() = when (exclusion) {
                is Exclusion.Hard -> exclusion.reason
                is Exclusion.RequiresConfirmation -> exclusion.reason
                null -> null
            }
    }

    /**
     * Build the picker list.
     *
     * Sorted by label, case-insensitively, so the order does not shift as apps
     * are checked. [query] filters on label and package name; blank shows all.
     * Every installed app appears — excluded ones are present and disabled with
     * [Row.reason] set, never dropped (design §4.1).
     */
    fun rows(
        installed: List<InstalledApp>,
        blocked: Set<String>,
        hardBlocked: Map<String, String>,
        requiresConfirmation: Map<String, String>,
        query: String = "",
    ): List<Row> {
        val needle = query.trim().lowercase()
        return installed
            .filter {
                needle.isEmpty() ||
                    it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
            .map { app ->
                val exclusion = hardBlocked[app.packageName]?.let { Exclusion.Hard(it) }
                    ?: requiresConfirmation[app.packageName]?.let { Exclusion.RequiresConfirmation(it) }
                Row(
                    packageName = app.packageName,
                    label = app.label,
                    // A hard-excluded app can never be genuinely blocked, so it
                    // must never render checked even if a stale row says it is.
                    checked = app.packageName in blocked && exclusion !is Exclusion.Hard,
                    exclusion = exclusion,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /**
     * Packages that must be dropped from an existing blocked list because the
     * platform would refuse them. Used to prune rows left behind when a package
     * becomes protected after it was blocked — for instance the user switching
     * to a launcher or keyboard they had already selected.
     */
    fun staleBlocked(blocked: Set<String>, hardBlocked: Map<String, String>): Set<String> =
        blocked.filter { it in hardBlocked }.toSet()
}
