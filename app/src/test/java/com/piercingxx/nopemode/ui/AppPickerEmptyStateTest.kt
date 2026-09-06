package com.piercingxx.nopemode.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T5 — AppPickerEmptyState: the honest empty-state decision for the blocked-app
 * picker (design §11).
 *
 * The rule that carries the weight: an empty list must say *why* it is empty.
 * "No apps match." is a lie on a fresh device with nothing installed and a lie
 * when GrapheneOS has denied QUERY_ALL_PACKAGES; the empty state must
 * distinguish those from "your search matched nothing".
 */
class AppPickerEmptyStateTest {

    @Test
    fun `a non-empty list shows no empty state`() {
        assertNull(AppPickerEmptyState.kind(installedCount = 5, rowCount = 3))
    }

    @Test
    fun `no installed apps is no-apps, not a search miss`() {
        // The lie the old code told: a fresh device with nothing launchable
        // would claim "No apps match." when there was nothing to match against.
        assertEquals(
            AppPickerEmptyState.Kind.NO_APPS,
            AppPickerEmptyState.kind(installedCount = 0, rowCount = 0),
        )
    }

    @Test
    fun `apps exist but nothing is shown is a search miss`() {
        assertEquals(
            AppPickerEmptyState.Kind.SEARCH_NO_MATCH,
            AppPickerEmptyState.kind(installedCount = 5, rowCount = 0),
        )
    }

    @Test
    fun `empty query under QUERY_ALL_PACKAGES denial is not a search miss`() {
        assertEquals(
            AppPickerEmptyState.Kind.QUERY_ALL_PACKAGES_DENIED,
            AppPickerEmptyState.kind(
                installedCount = 0,
                rowCount = 0,
                queryAllPackagesGranted = false,
            ),
        )
    }

    @Test
    fun `a filtered empty list stays a search miss even if QUERY_ALL_PACKAGES is denied`() {
        assertEquals(
            AppPickerEmptyState.Kind.SEARCH_NO_MATCH,
            AppPickerEmptyState.kind(
                installedCount = 5,
                rowCount = 0,
                queryAllPackagesGranted = false,
            ),
        )
    }

    @Test
    fun `granted QUERY_ALL_PACKAGES and no apps is still no-apps`() {
        assertEquals(
            AppPickerEmptyState.Kind.NO_APPS,
            AppPickerEmptyState.kind(
                installedCount = 0,
                rowCount = 0,
                queryAllPackagesGranted = true,
            ),
        )
    }
}