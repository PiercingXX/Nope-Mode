package com.piercingxx.nopemode.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Whether the fallback tier's two services are actually enabled.
 * Listener and accessibility self-derive; they do nothing if the user never
 * granted them in system Settings.
 */
object FallbackGrants {

    fun notificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, NopeNotificationListener::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.isNotificationListenerAccessGranted(cn)
        } else {
            enabledInSecureSetting(context, "enabled_notification_listeners", cn)
        }
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val cn = ComponentName(context, NopeAccessibilityService::class.java)
        return enabledInSecureSetting(
            context,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            cn,
        )
    }

    private fun enabledInSecureSetting(
        context: Context,
        key: String,
        cn: ComponentName,
    ): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, key) ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it) == cn }
    }
}
