package com.piercingxx.nopemode.ui

import com.piercingxx.nopemode.enforce.SuspendablePackages.Exclusion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS7 — AppPicker: the pure half of the blocked-app picker (design §11, §4.1).
 *
 * The rule that carries the weight here is §4.1's: an app the platform will
 * refuse must be *visible and disabled with a reason*, never silently missing,
 * so the user cannot believe an app is blocked when it isn't (R8).
 */
class AppPickerTest {

    private val installed = listOf(
        AppPicker.InstalledApp("com.example.chat", "Chat"),
        AppPicker.InstalledApp("com.example.launcher", "Pixel Launcher"),
        AppPicker.InstalledApp("com.example.keyboard", "Keyboard"),
        AppPicker.InstalledApp("com.example.sms", "Messages"),
        AppPicker.InstalledApp("com.example.news", "news reader"),
    )

    private val hardBlocked = mapOf(
        "com.example.launcher" to "Your home screen",
        "com.example.keyboard" to "Your active keyboard",
    )

    private val requiresConfirmation = mapOf("com.example.sms" to "Your SMS app")

    private fun rows(blocked: Set<String> = emptySet(), query: String = "") =
        AppPicker.rows(installed, blocked, hardBlocked, requiresConfirmation, query)

    @Test
    fun `every installed app appears, including excluded ones`() {
        assertEquals(
            installed.map { it.packageName }.toSet(),
            rows().map { it.packageName }.toSet()
        )
    }

    @Test
    fun `hard-excluded apps are listed but not selectable, with a reason`() {
        val launcher = rows().first { it.packageName == "com.example.launcher" }
        assertFalse("a hard exclusion must not be selectable", launcher.selectable)
        assertEquals("Your home screen", launcher.reason)
    }

    @Test
    fun `confirmation-gated apps stay selectable but carry a reason`() {
        val sms = rows().first { it.packageName == "com.example.sms" }
        assertTrue("SMS is permitted, just gated", sms.selectable)
        assertEquals("Your SMS app", sms.reason)
        assertTrue(sms.exclusion is Exclusion.RequiresConfirmation)
    }

    @Test
    fun `a freely selectable app has no reason`() {
        val chat = rows().first { it.packageName == "com.example.chat" }
        assertTrue(chat.selectable)
        assertNull(chat.reason)
    }

    @Test
    fun `blocked apps render checked`() {
        val chat = rows(blocked = setOf("com.example.chat")).first { it.packageName == "com.example.chat" }
        assertTrue(chat.checked)
    }

    @Test
    fun `a hard-excluded app never renders checked even if the blocked list says so`() {
        // The dangerous case: a stale row claiming an app is blocked that the
        // platform would refuse. Showing it checked would be reporting success
        // for a package that is not enforced (R8).
        val stale = rows(blocked = setOf("com.example.launcher"))
            .first { it.packageName == "com.example.launcher" }
        assertFalse(stale.checked)
    }

    @Test
    fun `sorting is case-insensitive by label and stable across checking`() {
        val labels = rows().map { it.label }
        assertEquals(listOf("Chat", "Keyboard", "Messages", "news reader", "Pixel Launcher"), labels)
        assertEquals(labels, rows(blocked = setOf("com.example.chat")).map { it.label })
    }

    @Test
    fun `search matches label and package, case-insensitively`() {
        assertEquals(listOf("Chat"), rows(query = "cha").map { it.label })
        assertEquals(listOf("Chat"), rows(query = "CHAT").map { it.label })
        assertEquals(listOf("Keyboard"), rows(query = "example.keyb").map { it.label })
        assertTrue(rows(query = "nothing matches this").isEmpty())
    }

    @Test
    fun `blank search shows everything`() {
        assertEquals(installed.size, rows(query = "   ").size)
    }

    @Test
    fun `staleBlocked names exactly the blocked apps that became protected`() {
        val stale = AppPicker.staleBlocked(
            blocked = setOf("com.example.chat", "com.example.keyboard"),
            hardBlocked = hardBlocked,
        )
        assertEquals(setOf("com.example.keyboard"), stale)
    }

    @Test
    fun `staleBlocked is empty when nothing blocked is protected`() {
        assertTrue(AppPicker.staleBlocked(setOf("com.example.chat"), hardBlocked).isEmpty())
    }
}
