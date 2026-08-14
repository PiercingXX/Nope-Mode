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
        /**
         * A caution shown inline on an otherwise freely selectable row. This is
         * information, never a gate — the user owns the device.
         */
        val advisory: String? = null,
    ) {
        /**
         * Only a hard exclusion blocks selection, and only because the platform
         * itself refuses. Everything else is the user's decision to make.
         */
        val selectable: Boolean get() = exclusion !is Exclusion.Hard

        /** Shown under the label: why it is blocked, or what to watch out for. */
        val reason: String?
            get() = when (exclusion) {
                is Exclusion.Hard -> exclusion.reason
                is Exclusion.RequiresConfirmation -> exclusion.reason
                null -> advisory
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
        advisories: Map<String, String> = emptyMap(),
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
                    advisory = advisories[app.packageName],
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** A list entry: either a section heading or an app. */
    sealed interface Item {
        data class Header(val title: String) : Item
        data class App(val row: Row) : Item
    }

    /**
     * The list split the way Focus Mode presents it: the apps you have already
     * chosen at the top under their own heading, everything else below.
     *
     * Selected-first matters because the chosen set is the thing you come back
     * to change; making it hunt through 58 alphabetical entries to find the four
     * that are on is the difference between a list and a control panel.
     *
     * [collapsed] shows only the chosen apps plus the heading for the rest,
     * mirroring the "Show all N apps" affordance. A search [query] always
     * expands, since filtering already narrows the list.
     */
    fun sections(
        installed: List<InstalledApp>,
        blocked: Set<String>,
        hardBlocked: Map<String, String>,
        requiresConfirmation: Map<String, String>,
        advisories: Map<String, String> = emptyMap(),
        query: String = "",
        collapsed: Boolean = false,
        chosenHeader: String = "Your distracting apps",
        moreHeader: String = "Select more apps",
    ): List<Item> {
        val rows = rows(installed, blocked, hardBlocked, requiresConfirmation, advisories, query)
        val (chosen, rest) = rows.partition { it.checked }

        val items = mutableListOf<Item>()
        if (chosen.isNotEmpty()) {
            items += Item.Header(chosenHeader)
            items += chosen.map { Item.App(it) }
        }
        if (rest.isNotEmpty()) {
            items += Item.Header(moreHeader)
            if (!collapsed || query.isNotBlank()) {
                items += rest.map { Item.App(it) }
            }
        }
        return items
    }

    /** How many apps the "show all" affordance would reveal. */
    fun hiddenCount(
        installed: List<InstalledApp>,
        blocked: Set<String>,
        hardBlocked: Map<String, String>,
    ): Int = installed.count { it.packageName !in blocked || it.packageName in hardBlocked }

    /**
     * Packages that must be dropped from an existing blocked list because the
     * platform would refuse them. Used to prune rows left behind when a package
     * becomes protected after it was blocked — for instance the user switching
     * to a launcher or keyboard they had already selected.
     */
    fun staleBlocked(blocked: Set<String>, hardBlocked: Map<String, String>): Set<String> =
        blocked.filter { it in hardBlocked }.toSet()
}
