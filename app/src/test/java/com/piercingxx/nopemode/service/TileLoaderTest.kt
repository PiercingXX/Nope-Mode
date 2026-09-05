package com.piercingxx.nopemode.service

import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.data.AppState
import com.piercingxx.nopemode.data.AppStateDao
import com.piercingxx.nopemode.data.Schedule
import com.piercingxx.nopemode.data.ScheduleDao
import com.piercingxx.nopemode.data.SettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * T4 — TileLoader: the QS tile's Room access, run off the main thread. The DAOs
 * and [SettingsStore] are injected (mirroring AlarmScheduler), so the loader is
 * JVM-provable with mocked storage. The mutable stubs let the second `load`
 * inside [TileLoader.toggle] observe the write, exactly as a real Room round
 * trip would.
 */
class TileLoaderTest {

    private val appStateDao = mockk<AppStateDao>()
    private val scheduleDao = mockk<ScheduleDao>()
    private val settings = mockk<SettingsStore>()
    private val zone = ZoneId.of("UTC")

    private fun stubSchedules(vararg schedules: Schedule) {
        every { scheduleDao.observeAll() } returns flowOf(schedules.toList())
    }

    /** Stub storage with mutable state so reads reflect writes. */
    private fun stubState(initialOverride: AppState?, initialEnabled: Boolean) {
        var stored = initialOverride
        var enabled = initialEnabled
        coEvery { appStateDao.get() } coAnswers { stored }
        coEvery { appStateDao.upsert(any()) } coAnswers { stored = firstArg(); Unit }
        every { settings.isEnabled() } answers { enabled }
        every { settings.setEnabled(any()) } answers { enabled = firstArg(); Unit }
    }

    @Test
    fun `load reflects a ForceOn override as active`() = runBlocking {
        stubState(
            initialOverride = AppState(id = 1, overrideKind = AppState.FORCE_ON, overrideUntil = null),
            initialEnabled = true,
        )
        stubSchedules()

        val snapshot = TileLoader(appStateDao, scheduleDao, settings, zone)
            .load(now = LocalDateTime.of(2026, 9, 5, 12, 0))

        assertTrue(snapshot.enabled)
        assertTrue(snapshot.active)
        assertNull(snapshot.endsAt)
        assertEquals(Override.ForceOn(null), snapshot.override)
    }

    @Test
    fun `load is inactive when the master switch is off`() = runBlocking {
        stubState(
            initialOverride = AppState(id = 1, overrideKind = AppState.FORCE_ON, overrideUntil = null),
            initialEnabled = false,
        )
        stubSchedules()

        val snapshot = TileLoader(appStateDao, scheduleDao, settings, zone)
            .load(now = LocalDateTime.of(2026, 9, 5, 12, 0))

        assertFalse(snapshot.enabled)
        assertFalse(snapshot.active)
    }

    @Test
    fun `toggle when inactive writes an indefinite ForceOn and re-enables`() = runBlocking {
        stubState(initialOverride = null, initialEnabled = false)
        stubSchedules()

        val snapshot = TileLoader(appStateDao, scheduleDao, settings, zone)
            .toggle(now = LocalDateTime.of(2026, 9, 5, 12, 0))

        verify { settings.setEnabled(true) }
        coVerify { appStateDao.upsert(match { it.overrideKind == AppState.FORCE_ON && it.overrideUntil == null }) }
        assertTrue(snapshot.enabled)
        assertTrue(snapshot.active)
        assertEquals(Override.ForceOn(null), snapshot.override)
    }

    @Test
    fun `toggle when active by ForceOn clears the override and drops the master`() = runBlocking {
        stubState(
            initialOverride = AppState(id = 1, overrideKind = AppState.FORCE_ON, overrideUntil = null),
            initialEnabled = true,
        )
        // The schedule alone keeps it active, so turning off must drop the master
        // too — otherwise the tile would say "off" while Nope-Mode kept running.
        stubSchedules(Schedule(id = 1, startMinuteOfDay = 0, endMinuteOfDay = 1439, daysMask = 0x7F))

        val snapshot = TileLoader(appStateDao, scheduleDao, settings, zone)
            .toggle(now = LocalDateTime.of(2026, 9, 5, 12, 0))

        verify { settings.setEnabled(false) }
        coVerify { appStateDao.upsert(match { it.overrideKind == AppState.NONE }) }
        assertFalse(snapshot.active)
        assertEquals(Override.None, snapshot.override)
    }
}