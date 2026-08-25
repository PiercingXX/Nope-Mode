package com.piercingxx.nopemode.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.piercingxx.nopemode.core.NopeController
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.SettingsStore
import com.piercingxx.nopemode.ui.BlockedActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WS9 — the FallbackEnforcer's foreground-detection arm (design §8.2b).
 *
 * On a window-state change, reads the foreground package; if it is blocked and
 * Nope-Mode is active, launches [BlockedActivity] to cover it. Debounced to
 * avoid launch loops. Deliberately does NOT use
 * `performGlobalAction(GLOBAL_ACTION_BACK)` — that fights the user's
 * navigation and behaves inconsistently across launchers (design §8.2).
 *
 * Android-dependent — behavior is deferred to the operator's on-device check
 * (design §16).
 */
class NopeAccessibilityService : AccessibilityService() {

    private var lastLaunchAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // never cover our own UI
        val now = System.currentTimeMillis()
        if (now - lastLaunchAt < DEBOUNCE_MILLIS) return
        if (!isActive()) return
        if (pkg !in blockedPackages()) return
        lastLaunchAt = now
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onInterrupt() = Unit

    private fun isActive(): Boolean {
        // The master switch gates everything (design R8): with it off, no tier
        // may enforce — same rule HomeActivity and the tile already follow.
        if (!SettingsStore(applicationContext).isEnabled()) return false
        val db = NopeDatabase.get(applicationContext)
        val override = OverrideMapper.toOverride(runBlocking { db.appStateDao().get() })
        val schedules = runBlocking { db.scheduleDao().observeAll().first() }
        return NopeController.derive(LocalDateTime.now(ZoneId.systemDefault()), schedules, override)
    }

    private fun blockedPackages(): Set<String> {
        val db = NopeDatabase.get(applicationContext)
        return runBlocking { db.blockedAppDao().observeAll().first() }
            .map { it.packageName }.toSet()
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 2_000L
    }
}