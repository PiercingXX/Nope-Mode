package com.piercingxx.nopemode.data

import com.piercingxx.nopemode.core.Override
import java.time.Instant

/**
 * T2 — OverrideMapper (design §10, WS2): the one place the pure core ([Override])
 * meets storage ([AppState]).
 *
 * [AppState] stores the override as a string kind plus a nullable Unix-millis
 * timestamp; [Override] is the sealed interface the pure core reasons over.
 * This object converts both directions.
 *
 * Pure — no `android.*` at this layer, so it is JVM-provable and covered by
 * [OverrideMapperTest].
 *
 * A `null` row (fresh install, nothing written yet) reads as [Override.None]
 * rather than crashing. `ForceOn(null)` (indefinite) round-trips as a null
 * `overrideUntil` column.
 */
object OverrideMapper {

    /**
     * Map a stored row to the core [Override]. `null` (no row yet) and a
     * `NONE`/unset kind both read as [Override.None].
     */
    fun toOverride(row: AppState?): Override {
        if (row == null) return Override.None
        return when (row.overrideKind) {
            null, AppState.NONE -> Override.None
            AppState.FORCE_ON -> Override.ForceOn(row.overrideUntil?.let(Instant::ofEpochMilli))
            AppState.BREAK -> Override.Break(Instant.ofEpochMilli(row.overrideUntil!!))
            else -> Override.None // unknown kind is treated as no override
        }
    }

    /**
     * Map a core [Override] to a storable [AppState] row. [lastReconcileAt]
     * defaults to now and is preserved by the caller on round-trips.
     */
    fun toAppState(
        override: Override,
        lastReconcileAt: Long = System.currentTimeMillis()
    ): AppState = when (override) {
        is Override.None -> AppState(
            id = 1,
            overrideKind = AppState.NONE,
            overrideUntil = null,
            lastReconcileAt = lastReconcileAt
        )
        is Override.ForceOn -> AppState(
            id = 1,
            overrideKind = AppState.FORCE_ON,
            overrideUntil = override.until?.toEpochMilli(),
            lastReconcileAt = lastReconcileAt
        )
        is Override.Break -> AppState(
            id = 1,
            overrideKind = AppState.BREAK,
            overrideUntil = override.until.toEpochMilli(),
            lastReconcileAt = lastReconcileAt
        )
    }
}