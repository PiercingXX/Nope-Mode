package com.piercingxx.nopemode.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.piercingxx.nopemode.R
import com.piercingxx.nopemode.admin.DeviceOwnerManager
import com.piercingxx.nopemode.core.FrictionSettings
import com.piercingxx.nopemode.core.NopeController
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.SettingsStore
import com.piercingxx.nopemode.databinding.ActivityHomeBinding
import com.piercingxx.nopemode.schedule.AlarmScheduler
import com.piercingxx.nopemode.schedule.BreakStarter
import com.piercingxx.nopemode.service.RingerPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime

/**
 * WS7 home screen (design §11).
 *
 * Reports the derived state — not a guess. Design §7.1 lists "app foregrounded"
 * as a reconcile trigger, so `onResume` reconciles before reading state back;
 * that keeps the screen honest if an alarm was missed while the app was away.
 */
class HomeActivity : AppCompatActivity() {

    /** Everything one refresh needs, so the UI reads named fields not a Triple. */
    private data class HomeState(
        val active: Boolean,
        val override: Override,
        val blockedCount: Int,
        val breakBlockedReason: String?,
    )

    private lateinit var binding: ActivityHomeBinding
    private lateinit var deviceOwner: DeviceOwnerManager
    private var onBreak = false
    private var suppressMasterCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        deviceOwner = DeviceOwnerManager(this)

        binding.blockedAppsButton.setOnClickListener { open(BlockedAppsActivity::class.java) }
        binding.schedulesButton.setOnClickListener { open(SchedulesActivity::class.java) }
        binding.settingsButton.setOnClickListener { open(SettingsActivity::class.java) }
        binding.backupButton.setOnClickListener { open(BackupActivity::class.java) }
        binding.setupButton.setOnClickListener { open(SetupActivity::class.java) }
        binding.breakButton.setOnClickListener { onBreakClicked() }
        binding.masterSwitch.setOnCheckedChangeListener { _, checked ->
            // Ignore the echo from refresh() setting the switch programmatically.
            // Guarding on isPressed instead looked equivalent but silently drops
            // real changes from accessibility services and synthetic input.
            if (suppressMasterCallback) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    SettingsStore(applicationContext).setEnabled(checked)
                    runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }
                }
                refresh()
            }
        }
    }

    private fun open(target: Class<out AppCompatActivity>) {
        startActivity(Intent(this, target))
    }

    /**
     * Focus Mode parity (design §1): a bounded break, offered as 5/15/30.
     *
     * While a break is running the same button ends it — changing your mind is
     * not a bypass, and the alternative is a dead control for the whole break.
     */
    private fun onBreakClicked() {
        if (onBreak) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { BreakStarter.end(applicationContext) }
                refresh()
            }
            return
        }

        val choices = FrictionSettings.BREAK_CHOICES
        AlertDialog.Builder(this)
            .setTitle(R.string.break_choose_title)
            .setItems(choices.map { getString(R.string.break_minutes, it) }.toTypedArray()) { _, which ->
                startBreak(choices[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startBreak(minutes: Int) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                BreakStarter.start(applicationContext, minutes)
            }
            // A refusal is shown, never swallowed: the frictions are the point,
            // so the user has to be told which one stopped them (§9, R8).
            if (result is BreakStarter.Result.Refused) {
                AlertDialog.Builder(this@HomeActivity)
                    .setMessage(result.reason)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            refresh()
        }
    }

    /**
     * Whether the Quiet Ringer can actually restrict the ringer.
     *
     * Checked by creating the rule, not by asking for the grant:
     * `isNotificationPolicyAccessGranted` was observed returning true on-device
     * while `addAutomaticZenRule` threw "Notification policy access denied", so
     * the grant flag alone would have us report a working ringer that isn't.
     */
    private fun quietRingerWorks(): Boolean =
        runCatching {
            RingerPolicy(applicationContext)
                .ensureRule(quietRingerEnabled = true, allowRepeatCallers = true) != null
        }.getOrDefault(false)

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        suppressMasterCallback = true
        binding.masterSwitch.isChecked = SettingsStore(this).isEnabled()
        suppressMasterCallback = false
        val isDeviceOwner = deviceOwner.tier() == DeviceOwnerManager.Tier.DEVICE_OWNER
        binding.tierText.text = HomeStateText.tierText(isDeviceOwner)
        binding.provisionText.text =
            HomeStateText.provisionText(isDeviceOwner, deviceOwner.provisioningCommand())

        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) {
                // Foregrounding is a reconcile trigger (design §7.1). Do it before
                // reading state so the headline reflects reality, not a stale row.
                runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }

                val db = NopeDatabase.get(applicationContext)
                val schedules = db.scheduleDao().observeAll().first()
                val override = OverrideMapper.toOverride(db.appStateDao().get())
                val blockedCount = db.blockedAppDao().observeAll().first().size
                val active = NopeController.derive(LocalDateTime.now(), schedules, override)
                val reason = BreakStarter.blockedReason(applicationContext)
                HomeState(active, override, blockedCount, reason)
            }
            onBreak = state.override is Override.Break &&
                Instant.now() < (state.override as Override.Break).until

            binding.stateText.text = HomeStateText.stateText(state.active, state.override)
            // BRAND-GUIDE §3.1, the rule of one accent: Signal green marks the
            // single thing the eye should land on. On this screen that is
            // whether Nope-Mode is actually on.
            binding.stateText.setTextColor(
                ContextCompat.getColor(
                    this@HomeActivity,
                    if (state.active) R.color.pxx_signal else R.color.pxx_paper,
                )
            )
            binding.blockedCountText.text = HomeStateText.blockedCountText(state.blockedCount)

            binding.breakButton.text =
                getString(if (onBreak) R.string.end_break else R.string.take_a_break)
            // Enabled while on a break so it can be ended; otherwise only when
            // the frictions allow a new one.
            binding.breakButton.isEnabled = onBreak || state.breakBlockedReason == null
            val showReason = !onBreak && state.breakBlockedReason != null
            binding.breakReasonText.visibility = if (showReason) View.VISIBLE else View.GONE
            binding.breakReasonText.text = state.breakBlockedReason.orEmpty()

            // Design §18.4 / R8: a Quiet Ringer that silently does nothing is
            // the worst outcome, so say it out loud rather than let the screen
            // imply calls are being filtered when they are not.
            val ringerWarning = HomeStateText.quietRingerWarning(quietRingerWorks())
            binding.warningText.text = ringerWarning.orEmpty()
            binding.warningText.visibility =
                if (ringerWarning == null) View.GONE else View.VISIBLE
        }
    }
}
