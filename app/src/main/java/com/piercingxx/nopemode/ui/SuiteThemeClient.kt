package com.piercingxx.nopemode.ui

import android.content.Context
import android.content.Intent

/**
 * Ask xx-apps to make this in-app pick the suite theme so every family
 * app follows, same as a launcher or xx-apps picker change.
 */
object SuiteThemeClient {

    const val APPS_PACKAGE = "com.piercingxx.apps"
    const val ACTION_SET_SUITE_THEME = "xx.apps.SET_SUITE_THEME"
    const val EXTRA_THEME_NAME = "xx.launcher.extra.THEME_NAME"
    const val EXTRA_BACKGROUND = "xx.launcher.extra.BACKGROUND"
    const val EXTRA_PRESET_KEY = "xx.apps.extra.PRESET_KEY"

    fun requestIntent(presetKey: String, background: Int): Intent =
        Intent(ACTION_SET_SUITE_THEME)
            .setPackage(APPS_PACKAGE)
            .putExtra(EXTRA_PRESET_KEY, presetKey)
            .putExtra(EXTRA_THEME_NAME, BackgroundTheme.LABELS[presetKey] ?: "Custom")
            .putExtra(EXTRA_BACKGROUND, background)

    fun request(context: Context, presetKey: String, background: Int) {
        runCatching { context.packageManager.getPackageInfo(APPS_PACKAGE, 0) }
            .onSuccess { context.sendBroadcast(requestIntent(presetKey, background)) }
    }
}
