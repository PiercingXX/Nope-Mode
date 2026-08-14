package com.piercingxx.nopemode.enforce

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * WS5 — the Android half of design §4.1: which packages the picker must refuse
 * or gate, discovered from the live device.
 *
 * [SuspendablePackages] does the selection math and stays pure; everything that
 * needs a PackageManager, a DevicePolicyManager or a Settings lookup lives here.
 *
 * Suspending any of these breaks the user's ability to undo the block:
 *  - **Nope-Mode itself** — no way back into the app to turn it off.
 *  - **Any active device admin** — the platform refuses regardless.
 *  - **The active launcher** — no home screen to launch anything from.
 *  - **The active IME** — a suspended keyboard leaves no way to type out of it.
 *    Design §4.1 calls this out specifically as a hard block.
 *  - **The package installer / verifier** — no way to sideload a fix.
 *  - **The default dialer** — no emergency calls.
 *  - **The permission controller** — no way to grant or revoke anything.
 *
 * The default SMS handler is different: suspending it is permitted and
 * occasionally wanted, but it silences 2FA codes. It is gated behind an explicit
 * confirmation and is never reachable by bulk-select.
 */
object ProtectedPackages {

    /** What the picker needs to know about the device, in one shot. */
    data class Protections(
        /** package -> reason; never selectable. */
        val hardBlocked: Map<String, String>,
        /** package -> reason; selectable only with explicit confirmation. */
        val requiresConfirmation: Map<String, String>,
        /**
         * package -> caution shown inline. Freely selectable — this is
         * information, not a gate. The user owns the device and decides.
         */
        val advisories: Map<String, String> = emptyMap(),
    )

    fun discover(context: Context): Protections {
        val pm = context.packageManager
        val hard = LinkedHashMap<String, String>()

        hard[context.packageName] = "Nope-Mode itself — suspending it would leave no way to turn it off"

        activeAdminPackages(context).forEach { pkg ->
            if (pkg != context.packageName) {
                hard.putIfAbsent(pkg, "An active device admin — the platform refuses to suspend it")
            }
        }
        homeLauncher(pm)?.let {
            hard.putIfAbsent(it, "Your home screen — suspending it would leave nothing to launch apps from")
        }
        activeInputMethod(context)?.let {
            hard.putIfAbsent(it, "Your active keyboard — suspending it would leave no way to type")
        }
        packageInstaller(pm)?.let {
            hard.putIfAbsent(it, "The package installer — needed to install or remove apps")
        }
        defaultDialer(context)?.let {
            // Verified on-device 2026-08-13: the platform logs
            // 'Cannot suspend package "com.android.dialer": is the default dialer'
            // and leaves it running. This is AOSP refusing, not Nope-Mode's
            // policy, so say so — and name the way around it, because there is
            // one and it is the user's to take.
            hard.putIfAbsent(
                it,
                "Android itself refuses to suspend whichever app is your default dialer. " +
                    "To block this one, make a different app your default dialer first.",
            )
        }
        permissionController(pm)?.let {
            hard.putIfAbsent(it, "The permission controller — needed to grant and revoke permissions")
        }

        // The default SMS handler suspends fine — verified on-device. Blocking
        // it is the user's call, so it is freely selectable and merely carries a
        // caution about 2FA codes.
        val advisories = LinkedHashMap<String, String>()
        defaultSms(context)?.let { pkg ->
            if (pkg !in hard) {
                advisories[pkg] = "Heads up: this is your SMS app, so blocking it also silences two-factor codes."
            }
        }

        return Protections(hard, requiresConfirmation = emptyMap(), advisories = advisories)
    }

    /** Every installed app that has a launcher entry, as the picker lists them. */
    fun launchableApps(pm: PackageManager): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                pkg to label
            }
            .distinctBy { it.first }
    }

    private fun activeAdminPackages(context: Context): List<String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return dpm?.activeAdmins.orEmpty().map { it.packageName }
    }

    private fun homeLauncher(pm: PackageManager): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    private fun activeInputMethod(context: Context): String? {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ) ?: return null
        return ComponentName.unflattenFromString(flat)?.packageName
    }

    /**
     * `ACTION_INSTALL_PACKAGE` is deprecated as a way to *start* an install, but
     * it is still the reliable way to identify which package owns installing.
     * We only resolve it; we never fire it.
     */
    @Suppress("DEPRECATION")
    private fun packageInstaller(pm: PackageManager): String? {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        return pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    private fun defaultDialer(context: Context): String? {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return runCatching { tm?.defaultDialerPackage }.getOrNull()
    }

    /**
     * `PackageManager.getPermissionControllerPackageName` is `@SystemApi` and not
     * reachable from a normal app, so resolve the permissions UI instead — it
     * lives in the same package.
     */
    private fun permissionController(pm: PackageManager): String? =
        runCatching {
            pm.resolveActivity(Intent(ACTION_MANAGE_PERMISSIONS), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()

    private const val ACTION_MANAGE_PERMISSIONS = "android.intent.action.MANAGE_PERMISSIONS"

    private fun defaultSms(context: Context): String? =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
}
