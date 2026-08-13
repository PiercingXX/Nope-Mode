package com.piercingxx.nopemode.service

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy

/**
 * T8 — WS11 Quiet Ringer: the Android wiring of the zen rule (design §18).
 *
 * App suspension cannot silence the phone — the dialer is one of the packages
 * the platform refuses to suspend (§4.1) — so calls need a second, independent
 * mechanism. This owns a single [AutomaticZenRule] that restricts the **ringer**
 * only while Nope-Mode is active:
 *
 * - Created once via [NotificationManager.addAutomaticZenRule]; its id is
 *   persisted so the same rule is reused, never duplicated.
 * - Rule state is driven by the **same derived `isActive`** as everything else
 *   (§6); there is no second scheduler. Callers flip it inside `reconcile()`.
 * - The rule is removed on teardown so an uninstall cannot strand the phone
 *   permanently quiet (§18.4).
 * - Nope-Mode owns *its* rule only and never writes global DND state (§18.4).
 *
 * The pure, JVM-provable decision slice is [ZenPolicyBuilder]; this class only
 * maps that config onto the framework. Android-dependent — behavior is deferred
 * to the operator's on-device check (design §16).
 */
class RingerPolicy(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Whether the user has granted Do Not Disturb access. Without it the rule
     * cannot be created and the ringer stays unrestricted — Home must say so
     * loudly rather than ever showing "active" (§18.4, R8).
     */
    fun isGranted(): Boolean =
        notificationManager.isNotificationPolicyAccessGranted

    /**
     * Create the rule once and persist its id. Idempotent: a second call with
     * an already-created rule is a no-op, so it is safe to run on every
     * foreground and boot.
     *
     * @return the persisted rule id, or null if DND access is not granted.
     */
    fun ensureRule(quietRingerEnabled: Boolean, allowRepeatCallers: Boolean): String? {
        if (!isGranted()) return null

        val existing = ruleId()
        if (existing != null) {
            // Rule already exists; (re)apply its policy in case the settings
            // changed while it was inactive.
            updateRule(existing, quietRingerEnabled, allowRepeatCallers)
            return existing
        }

        val config = ZenPolicyBuilder.build(quietRingerEnabled, allowRepeatCallers)
        val rule = AutomaticZenRule(
            RULE_NAME,
            ComponentName(context, RingerPolicy::class.java),
            CONDITION_ID,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            rule.zenPolicy = toZenPolicy(config)
        }
        val id = notificationManager.addAutomaticZenRule(rule)
        persistRuleId(id)
        return id
    }

    /** Re-apply the policy to an existing rule (e.g. after a settings change). */
    private fun updateRule(id: String, quietRingerEnabled: Boolean, allowRepeatCallers: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val rule = notificationManager.getAutomaticZenRule(id) ?: return
        rule.zenPolicy = toZenPolicy(
            ZenPolicyBuilder.build(quietRingerEnabled, allowRepeatCallers),
        )
        notificationManager.updateAutomaticZenRule(id, rule)
    }

    /**
     * Flip the rule's active state to match the derived `isActive`. A false
     * [active] deactivates the rule via its condition, leaving the phone able
     * to ring normally.
     */
    fun setActive(active: Boolean) {
        val id = ruleId() ?: return
        notificationManager.setAutomaticZenRuleState(
            id,
            Condition(
                CONDITION_ID,
                RULE_NAME,
                if (active) Condition.STATE_TRUE else Condition.STATE_FALSE,
            ),
        )
    }

    /** Remove the rule on teardown so an uninstall cannot strand the phone. */
    fun tearDown() {
        val id = ruleId() ?: return
        notificationManager.removeAutomaticZenRule(id)
        clearRuleId()
    }

    /** Map the pure config onto the framework [ZenPolicy]. */
    private fun toZenPolicy(config: ZenPolicyBuilder.Config): ZenPolicy {
        val builder = ZenPolicy.Builder()
            .apply {
                when (config.callAccess) {
                    ZenPolicyBuilder.CallAccess.STARRED_ONLY ->
                        allowCalls(ZenPolicy.PEOPLE_TYPE_STARRED)
                    ZenPolicyBuilder.CallAccess.ALL ->
                        allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
                }
                allowRepeatCallers(config.repeatCallersAllowed)
            }
        // §18.1: no notification-category field is ever touched — the builder
        // is left at its default for messages, reminders, alarms, events, etc.
        return builder.build()
    }

    // ---- persisted rule id (simple SharedPreferences-backed store) ----

    private fun prefs() =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ruleId(): String? =
        prefs().getString(KEY_RULE_ID, null)

    private fun persistRuleId(id: String) {
        prefs().edit().putString(KEY_RULE_ID, id).apply()
    }

    private fun clearRuleId() {
        prefs().edit().remove(KEY_RULE_ID).apply()
    }

    companion object {
        private const val RULE_NAME = "Nope-Mode Quiet Ringer"
        private const val PREFS_NAME = "nopemode_ringer"
        private const val KEY_RULE_ID = "zen_rule_id"

        /**
         * The rule's condition id. The rule's active state is driven directly
         * via [NotificationManager.setAutomaticZenRuleState], so the condition
         * itself is inert — this Uri just needs to be a stable, non-null id.
         */
        private val CONDITION_ID: Uri = Uri.parse("nopemode://quiet-ringer")
    }
}