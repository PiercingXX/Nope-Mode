package com.piercingxx.nopemode.service

import com.piercingxx.nopemode.service.ZenPolicyBuilder.CallAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T8 — WS11 Quiet Ringer: the pure zen-policy builder (design §18).
 *
 * Priority DND cannot be ringer-only, so the policy restricts calls to starred
 * contacts and explicitly allows other interruption categories rather than
 * inheriting user DND defaults.
 */
class ZenPolicyBuilderTest {

    // ---- quiet ringer enabled: calls from starred contacts only ----

    @Test
    fun `enabled restricts calls to starred contacts only`() {
        val config = ZenPolicyBuilder.build(quietRingerEnabled = true, allowRepeatCallers = true)
        assertEquals(CallAccess.STARRED_ONLY, config.callAccess)
    }

    // ---- repeat-caller toggle ----

    @Test
    fun `enabled honours the repeat-caller toggle on`() {
        val config = ZenPolicyBuilder.build(quietRingerEnabled = true, allowRepeatCallers = true)
        assertTrue(config.repeatCallersAllowed)
    }

    @Test
    fun `enabled honours the repeat-caller toggle off`() {
        val config = ZenPolicyBuilder.build(quietRingerEnabled = true, allowRepeatCallers = false)
        assertFalse(config.repeatCallersAllowed)
    }

    // ---- DND cannot be ringer-only: allow every non-call category ----

    @Test
    fun `enabled allows other interruptions so user DND defaults are not inherited`() {
        val config = ZenPolicyBuilder.build(quietRingerEnabled = true, allowRepeatCallers = true)
        assertTrue(config.allowOtherInterruptions)
    }

    // ---- quiet ringer disabled: the rule is inert ----

    @Test
    fun `disabled leaves the ringer unrestricted`() {
        val config = ZenPolicyBuilder.build(quietRingerEnabled = false, allowRepeatCallers = true)
        assertEquals(CallAccess.ALL, config.callAccess)
    }

    @Test
    fun `disabled still honours the repeat-caller toggle`() {
        val config = ZenPolicyBuilder.build(quietRingerEnabled = false, allowRepeatCallers = false)
        assertFalse(config.repeatCallersAllowed)
    }

    @Test
    fun `disabled still allows other interruptions`() {
        val config = ZenPolicyBuilder.build(quietRingerEnabled = false, allowRepeatCallers = true)
        assertTrue(config.allowOtherInterruptions)
    }
}