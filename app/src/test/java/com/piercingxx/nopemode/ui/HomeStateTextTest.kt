package com.piercingxx.nopemode.ui

import com.piercingxx.nopemode.core.Override
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * T6 — HomeStateText: the pure "what does the home screen say" slice of WS7
 * (design §8). Asserts the state headline, the honest tier line, the
 * provisioning command, and the break countdown — all JVM-provable without a
 * device.
 */
class HomeStateTextTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun instant(secsFromEpoch: Long): Instant = Instant.ofEpochSecond(secsFromEpoch)

    // ---- State headline ----

    @Test
    fun `active with no override reads ON`() {
        assertEquals("Nope-Mode is ON", HomeStateText.stateText(true, Override.None))
    }

    @Test
    fun `inactive with no override reads OFF`() {
        assertEquals("Nope-Mode is OFF", HomeStateText.stateText(false, Override.None))
    }

    @Test
    fun `running break shows the remaining countdown`() {
        val now = instant(0)
        val until = now.plus(Duration.ofMinutes(12))
        val text = HomeStateText.stateText(true, Override.Break(until), now)
        assertEquals("On a break — resumes in 12 min", text)
    }

    @Test
    fun `expired break falls back to active state`() {
        val now = instant(0)
        val until = now.minusSeconds(1)
        assertEquals("Nope-Mode is ON", HomeStateText.stateText(true, Override.Break(until), now))
    }

    @Test
    fun `force on keeps the active headline`() {
        assertEquals("Nope-Mode is ON", HomeStateText.stateText(true, Override.ForceOn(null)))
    }

    // ---- Tier line ----

    @Test
    fun `device owner tier is described as full suspension`() {
        val text = HomeStateText.tierText(true)
        assertTrue(text.contains("Device owner"))
        assertTrue(text.contains("fully suspended"))
    }

    @Test
    fun `fallback tier is described honestly as limited`() {
        val text = HomeStateText.tierText(false)
        assertTrue(text.contains("Limited"))
        assertTrue(text.contains("sound may play first"))
    }

    // ---- Provisioning ----

    @Test
    fun `device owner shows no provisioning command`() {
        assertEquals("", HomeStateText.provisionText(true, "adb shell dpm ..."))
    }

    @Test
    fun `fallback surfaces the provisioning command verbatim`() {
        val cmd = "adb shell dpm set-device-owner com.piercingxx.nopemode/.admin.NopeDeviceAdminReceiver"
        assertEquals(cmd, HomeStateText.provisionText(false, cmd))
    }

    // ---- Break countdown ----

    @Test
    fun `countdown clamps to zero once the break ends`() {
        val now = instant(100)
        val until = instant(50)
        assertEquals(0L, HomeStateText.breakCountdownMinutes(until, now))
    }

    @Test
    fun `countdown reports whole remaining minutes`() {
        val now = instant(0)
        val until = now.plus(Duration.ofMinutes(25).plusSeconds(30))
        assertEquals(25L, HomeStateText.breakCountdownMinutes(until, now))
    }

    @Test
    fun `break end time converts to local date-time`() {
        val until = instant(0)
        assertEquals(until.atZone(zone).toLocalDateTime(), HomeStateText.breakEndTime(until, zone))
    }

    @Test
    fun `zero blocked apps says so explicitly rather than reading as configured`() {
        // "Nope-Mode is ON" over an empty blocked list suspends nothing. Saying
        // only "0 apps blocked" reads as a tally; it has to say what it means.
        val text = HomeStateText.blockedCountText(0)
        assertTrue("must name the consequence, not just the count", text.contains("not suspend"))
    }

    @Test
    fun `blocked count is singular for one and plural beyond`() {
        assertEquals("1 app blocked.", HomeStateText.blockedCountText(1))
        assertEquals("2 apps blocked.", HomeStateText.blockedCountText(2))
        assertEquals("17 apps blocked.", HomeStateText.blockedCountText(17))
    }

    @Test
    fun `quiet ringer warning is silent when disabled by choice`() {
        assertEquals(null, HomeStateText.quietRingerWarning(enabled = false, working = false))
    }

    @Test
    fun `quiet ringer warning names the grant when enabled but inert`() {
        val text = HomeStateText.quietRingerWarning(enabled = true, working = false)
        assertTrue(text!!.contains("Do Not Disturb"))
    }

    @Test
    fun `failed packages are named rather than counted as blocked`() {
        val text = HomeStateText.failedPackagesWarning(setOf("com.foo", "com.bar"))
        assertTrue(text!!.contains("com.bar"))
        assertTrue(text.contains("com.foo"))
        assertTrue(text.contains("still openable"))
    }

    @Test
    fun `fallback warning names whichever grants are missing`() {
        val both = HomeStateText.fallbackGrantWarning(true, listenerEnabled = false, accessibilityEnabled = false)
        assertTrue(both!!.contains("notification access"))
        assertTrue(both.contains("accessibility"))
        assertEquals(null, HomeStateText.fallbackGrantWarning(false, false, false))
        assertEquals(null, HomeStateText.fallbackGrantWarning(true, listenerEnabled = true, accessibilityEnabled = true))
    }

    @Test
    fun `warnings concatenate independent failures`() {
        val text = HomeStateText.warnings(
            quietRingerEnabled = true,
            quietRingerWorking = false,
            exactAlarmDegraded = true,
            failedPackages = setOf("com.x"),
            fallback = true,
            listenerEnabled = false,
            accessibilityEnabled = true,
            reconcileError = "boom",
        )
        assertTrue(text!!.contains("Do Not Disturb"))
        assertTrue(text.contains("Exact alarms"))
        assertTrue(text.contains("com.x"))
        assertTrue(text.contains("notification access"))
        assertTrue(text.contains("boom"))
    }
}