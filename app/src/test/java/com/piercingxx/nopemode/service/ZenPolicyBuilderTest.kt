package com.piercingxx.nopemode.service

import com.piercingxx.nopemode.service.ZenPolicyBuilder.CallAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T8 — WS11 Quiet Ringer: the pure zen-policy builder (design §18).
 *
 * The `AutomaticZenRule` is a global interruption filter, so the policy must
 * restrict the ringer only and leave every notification category untouched
 * (§18.1) — otherwise it would quiet apps the user never selected. This pins:
 * the starred-calls-only policy, the repeat-caller toggle on/off, and that no
 * notification-category field is ever set.
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

    // ---- the rule must not become a general notification filter (§18.1) ----

    @Test
    fun `enabled never touches a notification category`() {
        val config = ZenPolicyBuilder.build(quietRingerEnabled = true, allowRepeatCallers = true)
        assertFalse(config.notificationCategoriesTouched)
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
    fun `disabled never touches a notification category`() {
        val config = ZenPolicyBuilder.build(quietRingerEnabled = false, allowRepeatCallers = true)
        assertFalse(config.notificationCategoriesTouched)
    }
}