package com.piercingxx.nopemode.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.piercingxx.nopemode.R
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.ui.HomeActivity
import java.time.Instant

/**
 * Persistent countdown while a break is running (design §9). Cancelled when
 * the break expires or the master switch is off.
 */
object BreakNotification {

    const val CHANNEL_ID = "break_countdown"
    const val NOTIFICATION_ID = 1

    fun sync(context: Context, override: Override, enabled: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val running = enabled &&
            override is Override.Break &&
            Instant.now() < override.until
        if (!running) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(nm)
        val until = (override as Override.Break).until
        val home = PendingIntent.getActivity(
            context,
            0,
            Intent(context, HomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_break)
            .setContentTitle(context.getString(R.string.break_notification_title))
            .setContentText(context.getString(R.string.break_notification_text))
            .setContentIntent(home)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(until.toEpochMilli())
            .setShowWhen(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Break countdown",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
