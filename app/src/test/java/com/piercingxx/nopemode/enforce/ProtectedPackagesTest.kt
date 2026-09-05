package com.piercingxx.nopemode.enforce

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3 — ProtectedPackages.discover: the device half of WS5 (design §4.1), driven
 * with a mocked Context. The family clock `com.piercingxx.xxclock` is
 * hard-blocked, and the existing self/launcher/IME/dialer protections stay
 * intact. The platform lookups are stubbed so the test focuses on Nope-Mode's
 * own policy rather than Android's.
 */
class ProtectedPackagesTest {

    private val pm = mockk<PackageManager>()
    private val context = mockk<Context>()

    private fun discover(): ProtectedPackages.Protections {
        every { context.packageName } returns "com.piercingxx.nopemode"
        every { context.packageManager } returns pm
        // No active admins, no resolvable launcher/IME/installer/dialer/permission
        // controller, no default SMS — so the only hard-blocks are self and the
        // family clock.
        every { context.getSystemService(Context.DEVICE_POLICY_SERVICE) } returns
            mockk<DevicePolicyManager>().apply { every { activeAdmins } returns null }
        every { context.getSystemService(Context.TELECOM_SERVICE) } returns null
        every { context.contentResolver } returns mockk()
        every { pm.resolveActivity(any<Intent>(), any<Int>()) } returns null
        return ProtectedPackages.discover(context)
    }

    @Test
    fun `the family clock is hard-blocked with a reason`() {
        val protections = discover()
        val reason = protections.hardBlocked["com.piercingxx.xxclock"]
        assertTrue(reason != null)
        assertEquals(
            "The family clock — it must stay reachable",
            reason,
        )
    }

    @Test
    fun `self stays hard-blocked`() {
        val protections = discover()
        assertTrue(protections.hardBlocked.containsKey("com.piercingxx.nopemode"))
    }
}