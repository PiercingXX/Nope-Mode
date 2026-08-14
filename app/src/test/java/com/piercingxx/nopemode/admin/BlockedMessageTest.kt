package com.piercingxx.nopemode.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The one part of the platform's blocked dialog Nope-Mode controls (design §11).
 *
 * The stock text is "Blocked by work policy / For more info, contact your IT
 * admin", which is wrong on a personal device: there is no work policy and no
 * admin to contact.
 */
class BlockedMessageTest {

    private val eightAm = LocalDateTime.of(2026, 8, 14, 8, 0)

    @Test
    fun `an active block leads with the one word and says when it lifts`() {
        val text = BlockedMessage.shortSupportMessage(isActive = true, endsAt = eightAm)
        assertEquals("Nope. Back at 8:00 AM.", text)
    }

    @Test
    fun `an indefinite block says so instead of inventing a time`() {
        // A ForceOn with no end will not lift at the next schedule boundary, so
        // printing one would be a promise the app does not keep.
        val text = BlockedMessage.shortSupportMessage(isActive = true, endsAt = null)
        assertEquals("Nope. On until you turn it off.", text)
    }

    @Test
    fun `nothing blocked clears the message rather than leaving a stale one`() {
        assertNull(BlockedMessage.shortSupportMessage(isActive = false, endsAt = eightAm))
        assertNull(BlockedMessage.shortSupportMessage(isActive = false, endsAt = null))
    }

    @Test
    fun `the message never mentions work or an IT admin`() {
        val text = BlockedMessage.shortSupportMessage(true, eightAm)!!.lowercase()
        assertFalse("no work policy on a personal phone", text.contains("work"))
        assertFalse("there is no IT admin to contact", text.contains("admin"))
        assertFalse(text.contains("policy"))
    }

    @Test
    fun `the message stays inside the platform limit`() {
        listOf(eightAm, null).forEach { endsAt ->
            val text = BlockedMessage.shortSupportMessage(true, endsAt)!!
            assertTrue("must fit: ${text.length}", text.length <= BlockedMessage.MAX_LENGTH)
        }
    }

    @Test
    fun `times read in 12-hour form to match the shade`() {
        assertEquals(
            "Nope. Back at 8:30 PM.",
            BlockedMessage.shortSupportMessage(true, LocalDateTime.of(2026, 8, 13, 20, 30))
        )
    }
}
