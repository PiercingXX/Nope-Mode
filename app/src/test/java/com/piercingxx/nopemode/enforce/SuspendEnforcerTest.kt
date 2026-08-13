package com.piercingxx.nopemode.enforce

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.piercingxx.nopemode.data.SuspendRecord
import com.piercingxx.nopemode.data.SuspendRecordDao
import io.mockk.MockKStubScope
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS5 — SuspendEnforcer: the device-owner tier of enforcement (design §5, §4.1).
 *
 * JVM-proven slice of the refusal bookkeeping (R8): a package the platform
 * refuses to suspend must be surfaced in [Enforcer.Result.failed] and its
 * `suspend_record` cleaned up so it is never reported as blocked; a null return
 * means full success (empty failed list); and the release path unsuspends
 * before deleting the record.
 *
 * The platform calls are mocked — the real on-device behavior (design §16)
 * stays deferred to the operator's SDK 37 check.
 */
class SuspendEnforcerTest {

    private val dpm = mockk<DevicePolicyManager>()
    private val dao = mockk<SuspendRecordDao>()
    private val context = mockk<Context>()

    private fun enforcer(recorded: Set<String>): SuspendEnforcer {
        every { context.getSystemService(Context.DEVICE_POLICY_SERVICE) } returns dpm
        every { context.applicationContext } returns context
        every { context.packageName } returns "com.piercingxx.nopemode"
        every { dao.observeAll() } returns flowOf(recorded.map { SuspendRecord(it) })
        coEvery { dao.insertAll(any()) } just Runs
        coEvery { dao.deleteByPackages(any()) } just Runs
        return SuspendEnforcer(context, dao)
    }

    /**
     * Stub [DevicePolicyManager.setPackagesSuspended] to return null (all
     * packages suspended). The Java platform type `String[]` is fixed to
     * non-null by MockK's generic inference, so force the nullable return type
     * with an unchecked cast on the stub scope.
     */
    @Suppress("UNCHECKED_CAST")
    private fun stubSuspendedReturnsNull(suspended: Boolean) {
        val stub = every {
            dpm.setPackagesSuspended(any(), any(), suspended)
        } as MockKStubScope<Array<String>?, DevicePolicyManager>
        stub returns null
    }

    @Test
    fun `refused package is surfaced in failed and its suspend record deleted`() {
        val enforcer = enforcer(recorded = emptySet())
        // setPackagesSuspended returns the packages it could NOT suspend.
        every { dpm.setPackagesSuspended(any(), any(), true) } returns
            arrayOf("com.example.games")

        val result = enforcer.apply(setOf("com.example.games"))

        assertTrue(result.failed.contains("com.example.games"))
        assertTrue(!result.success)
        // The refused package must not stay recorded, or it would be reported
        // as blocked on the next cycle (R8).
        coVerify { dao.deleteByPackages(listOf("com.example.games")) }
    }

    @Test
    fun `null return means full success and empty failed list`() {
        val enforcer = enforcer(recorded = emptySet())
        // An empty refusal (the platform suspended every requested package)
        // must surface as full success with no failed packages.
        every { dpm.setPackagesSuspended(any(), any(), true) } returns emptyArray()

        val result = enforcer.apply(setOf("com.example.games"))

        assertEquals(emptySet<String>(), result.failed)
        assertTrue(result.success)
        // Nothing was refused, so nothing is cleaned up (the delete call is
        // empty and removes nothing).
        coVerify { dao.deleteByPackages(emptyList()) }
    }

    @Test
    fun `release path unsuspends before deleting the record`() {
        val enforcer = enforcer(recorded = setOf("com.example.games"))
        stubSuspendedReturnsNull(suspended = false)

        val result = enforcer.apply(desired = emptySet())

        // Unsuspend the released package (suspended = false)...
        verify { dpm.setPackagesSuspended(any(), arrayOf("com.example.games"), false) }
        // ...then delete its record so it is no longer recoverable-as-suspended.
        coVerify { dao.deleteByPackages(listOf("com.example.games")) }
        assertTrue(result.failed.isEmpty())
    }
}