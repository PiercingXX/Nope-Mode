package com.piercingxx.nopemode.service

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.piercingxx.nopemode.core.NopeController
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WS9 — the FallbackEnforcer's notification-suppression arm (design §8.2a).
 *
 * On every posted notification from a blocked package while Nope-Mode is
 * active, cancels (or snoozes) it. This is the weaker tier: the notification
 * has already been posted by the time the listener sees it, so the alert sound
 * may play before suppression. It delivers "the notification goes away", not
 * "the notification never made a sound", and does not satisfy R2 — the UI must
 * say so (design §8.2, R8).
 *
 * The pure decision is [NotificationDecision]; this class only maps it onto the
 * framework. Android-dependent — behavior is deferred to the operator's
 * on-device check (design §16).
 */
class NopeNotificationListener : NotificationListenerService() {

    /** Epoch-millis of the last action per package, for the debounce window. */
    private val lastActionAt = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val now = System.currentTimeMillis()
        val decision = NotificationDecision.decide(
            packageName = pkg,
            active = isActive(),
            blocked = blockedPackages(),
            isSuspendable = false, // fallback tier: nothing is suspendable
            preferSnooze = sbn.isOngoing.not(),
            lastActionAt = lastActionAt[pkg],
            nowMillis = now,
            debounceMillis = DEBOUNCE_MILLIS,
        )
        when (decision) {
            NotificationDecision.Decision.NoOp -> Unit
            NotificationDecision.Decision.Cancel -> {
                cancelNotification(sbn.key)
                lastActionAt[pkg] = now
            }
            is NotificationDecision.Decision.Snooze -> {
                // snoozeNotification(key, duration) is API 26+; on API 24-25 the
                // notification is cancelled instead (still suppressed, just not
                // re-postable).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    snoozeNotification(sbn.key, decision.durationMillis)
                } else {
                    cancelNotification(sbn.key)
                }
                lastActionAt[pkg] = now
            }
        }
    }

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
        const val DEBOUNCE_MILLIS = 5_000L
        const val TAG = "NopeNotifListener"
    }
}