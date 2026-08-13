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
}