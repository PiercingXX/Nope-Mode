package com.piercingxx.nopemode.enforce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3 — SuspendablePackages: the pure exclusion/selection slice of WS5
 * (design §4.1). Self, a device admin, the active launcher, the active IME,
 * and the default SMS handler are excluded with reasons; a normal third-party
 * app is included. Pure — no `android.*` at the tested layer.
 */
class SuspendablePackagesTest {

    private val installed = setOf(
        "com.piercingxx.nopemode",   // self
        "com.example.admin",
        "com.example.launcher",
        "com.example.ime",
        "com.example.sms",
        "com.example.dialer",
        "com.example.installer",
        "com.example.uninstaller",
        "com.example.verifier",
        "com.example.permission",
        "com.example.games",         // normal third-party app
    )

    private val hardBlocked = mapOf(
        "com.piercingxx.nopemode" to "Nope-Mode itself is a device admin",
        "com.example.admin" to "device admin",
        "com.example.launcher" to "active launcher",
        "com.example.ime" to "active input method",
        "com.example.dialer" to "default dialer",
        "com.example.installer" to "package installer",
        "com.example.uninstaller" to "package uninstaller",
        "com.example.verifier" to "package verifier",
        "com.example.permission" to "permission controller",
    )

    private val requiresConfirmation = mapOf(
        "com.example.sms" to "default SMS handler — silences 2FA codes",
    )

    private fun selectAllBlocked(): SuspendablePackages.Selection =
        SuspendablePackages.select(installed, installed, hardBlocked, requiresConfirmation)

    @Test
    fun `self is excluded with a reason`() {
        val sel = selectAllBlocked()
        assertTrue(sel.excluded.containsKey("com.piercingxx.nopemode"))
        assertFalse(sel.suspendable.contains("com.piercingxx.nopemode"))
    }

    @Test
    fun `a device admin is excluded with a reason`() {
        val sel = selectAllBlocked()
        assertTrue(sel.excluded.containsKey("com.example.admin"))
        assertFalse(sel.suspendable.contains("com.example.admin"))
    }

    @Test
    fun `the active launcher is excluded with a reason`() {
        val sel = selectAllBlocked()
        assertTrue(sel.excluded.containsKey("com.example.launcher"))
        assertFalse(sel.suspendable.contains("com.example.launcher"))
    }

    @Test
    fun `the active IME is hard-excluded`() {
        val sel = selectAllBlocked()
        val ime = sel.excluded["com.example.ime"]
        assertTrue(ime is SuspendablePackages.Exclusion.Hard)
        assertFalse(sel.suspendable.contains("com.example.ime"))
    }

    @Test
    fun `the SMS handler is excluded and requires confirmation`() {
        val sel = selectAllBlocked()
        val sms = sel.excluded["com.example.sms"]
        assertTrue(sms is SuspendablePackages.Exclusion.RequiresConfirmation)
        assertFalse(sel.suspendable.contains("com.example.sms"))
    }

    @Test
    fun `a normal third-party app is included`() {
        val sel = selectAllBlocked()
        assertTrue(sel.suspendable.contains("com.example.games"))
        assertFalse(sel.excluded.containsKey("com.example.games"))
    }

    @Test
    fun `a protected package not in the blocked list is not reported`() {
        // The launcher is protected but the user never blocked it — it must not
        // appear in the exclusion map, which is only about blocked packages.
        val sel = SuspendablePackages.select(
            installed,
            blocked = setOf("com.example.games"),
            hardBlocked,
            requiresConfirmation,
        )
        assertFalse(sel.excluded.containsKey("com.example.launcher"))
        assertTrue(sel.suspendable.contains("com.example.games"))
    }

    @Test
    fun `excluded packages carry the inline reason`() {
        val sel = selectAllBlocked()
        val ime = sel.excluded["com.example.ime"] as? SuspendablePackages.Exclusion.Hard
        assertEquals("active input method", ime?.reason)
        val sms = sel.excluded["com.example.sms"] as? SuspendablePackages.Exclusion.RequiresConfirmation
        assertTrue(sms?.reason?.contains("SMS") == true)
    }
}