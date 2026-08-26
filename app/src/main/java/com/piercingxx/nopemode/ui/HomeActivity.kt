package com.piercingxx.nopemode.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.piercingxx.nopemode.R
import com.piercingxx.nopemode.admin.DeviceOwnerManager
import com.piercingxx.nopemode.core.FrictionSettings
import com.piercingxx.nopemode.core.NopeController
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.ReconcileStatus
import com.piercingxx.nopemode.data.SettingsStore
import com.piercingxx.nopemode.databinding.ActivityHomeBinding
import com.piercingxx.nopemode.schedule.AlarmScheduler
import com.piercingxx.nopemode.schedule.BreakStarter
import com.piercingxx.nopemode.service.FallbackGrants
import com.piercingxx.nopemode.service.RingerPolicy
import com.piercingxx.nopemode.service.TileState
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
class HomeActivity : BrandActivity() {

    /** Everything one refresh needs, so the UI reads named fields not a Triple. */
    private data class HomeState(
        val active: Boolean,
        val override: Override,
        val blockedCount: Int,
        val warning: String?,
        val onBreak: Boolean,
    )

    private lateinit var binding: ActivityHomeBinding
    private lateinit var deviceOwner: DeviceOwnerManager
    private var suppressMasterCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()
        deviceOwner = DeviceOwnerManager(this)

        binding.blockedAppsButton.setOnClickListener { open(BlockedAppsActivity::class.java) }
        binding.scheduleButton.setOnClickListener { open(SchedulesActivity::class.java) }
        binding.settingsButton.setOnClickListener { open(SettingsActivity::class.java) }
        binding.backupButton.setOnClickListener { open(BackupActivity::class.java) }
        binding.setupButton.setOnClickListener { open(SetupActivity::class.java) }
        binding.turnOnNowButton.setOnClickListener { onTurnOnNowClicked() }
        binding.takeABreakButton.setOnClickListener { onTakeABreakClicked() }
        binding.warningText.setOnClickListener { open(SetupActivity::class.java) }
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
     * Focus Mode's "Turn on now": force Nope-Mode active outside its schedule,
     * and clear that force when it is already on.
     *
     * Writing ForceOn is what the tile does too, so both go through the same
     * override and the same reconcile (D8) rather than two paths that can
     * disagree.
     */
    private fun onTurnOnNowClicked() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = NopeDatabase.get(applicationContext)
                val current = OverrideMapper.toOverride(db.appStateDao().get())
                // Same decision the tile makes, so the two controls cannot
                // disagree about what a tap means.
                val schedules = db.scheduleDao().observeAll().first()
                val now = LocalDateTime.now()
                val settings = SettingsStore(applicationContext)
                val activeBySchedule = NopeController.derive(now, schedules, Override.None)
                val isActive = settings.isEnabled() && NopeController.derive(now, schedules, current)
                val tap = TileState.onTap(isActive, current, activeBySchedule)
                settings.setEnabled(tap.enabled)
                db.appStateDao().upsert(OverrideMapper.toAppState(tap.override))
                runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }
            }
            refresh()
        }
    }

    private fun onTakeABreakClicked() {
        lifecycleScope.launch {
            val override = withContext(Dispatchers.IO) {
                OverrideMapper.toOverride(
                    NopeDatabase.get(applicationContext).appStateDao().get(),
                )
            }
            if (override is Override.Break && Instant.now() < override.until) {
                withContext(Dispatchers.IO) { BreakStarter.end(applicationContext) }
                refresh()
                return@launch
            }
            val refused = withContext(Dispatchers.IO) {
                BreakStarter.blockedReason(applicationContext)
            }
            if (refused != null) {
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setMessage(refused)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            requestBreakNotifications()
            val choices = FrictionSettings.BREAK_CHOICES
            val labels = choices.map { getString(R.string.break_minutes, it) }.toTypedArray()
            MaterialAlertDialogBuilder(this@HomeActivity)
                .setTitle(R.string.break_choose_title)
                .setItems(labels) { _, which ->
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            BreakStarter.start(applicationContext, choices[which])
                        }
                        if (result is BreakStarter.Result.Refused) {
                            MaterialAlertDialogBuilder(this@HomeActivity)
                                .setMessage(result.reason)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                        refresh()
                    }
                }
                .show()
        }
    }

    private fun requestBreakNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

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
                val settings = SettingsStore(applicationContext)
                val active = settings.isEnabled() &&
                    NopeController.derive(LocalDateTime.now(), schedules, override)
                val isDeviceOwnerNow =
                    deviceOwner.tier() == DeviceOwnerManager.Tier.DEVICE_OWNER
                val status = ReconcileStatus(applicationContext)
                val warning = HomeStateText.warnings(
                    quietRingerEnabled = settings.load().quietRingerEnabled,
                    quietRingerWorking = runCatching {
                        RingerPolicy(applicationContext).isUsable()
                    }.getOrDefault(false),
                    exactAlarmDegraded = status.isExactAlarmDegraded(),
                    failedPackages = status.failedPackages(),
                    fallback = !isDeviceOwnerNow,
                    listenerEnabled = FallbackGrants.notificationListenerEnabled(applicationContext),
                    accessibilityEnabled = FallbackGrants.accessibilityEnabled(applicationContext),
                    reconcileError = status.reconcileError(),
                )
                val onBreak = override is Override.Break && Instant.now() < override.until
                HomeState(active, override, blockedCount, warning, onBreak)
            }
            binding.stateText.text = HomeStateText.stateText(state.active, state.override)
            // BRAND-GUIDE §3.1, the rule of one accent: Signal white marks the
            // single thing the eye should land on. On this screen that is
            // whether Nope-Mode is actually on.
            binding.stateText.setTextColor(
                if (state.active) {
                    BackgroundTheme.accentTextColor(
                        preset,
                        ContextCompat.getColor(this@HomeActivity, R.color.pxx_signal),
                    )
                } else {
                    BackgroundTheme.bodyTextColor(preset)
                }
            )
            binding.blockedCountText.text = HomeStateText.blockedCountText(state.blockedCount)

            // Label follows the ACTIVE state, not the override. A stale ForceOn
            // left over from a tile tap had it reading "Turn off now" on a
            // screen that said Nope-Mode was off.
            binding.turnOnNowButton.text =
                getString(if (state.active) R.string.turn_off_now else R.string.turn_on_now)

            when {
                state.onBreak -> {
                    binding.takeABreakButton.visibility = View.VISIBLE
                    binding.takeABreakButton.text = getString(R.string.end_break)
                }
                state.active -> {
                    binding.takeABreakButton.visibility = View.VISIBLE
                    binding.takeABreakButton.text = getString(R.string.take_a_break)
                }
                else -> binding.takeABreakButton.visibility = View.GONE
            }

            binding.warningText.text = state.warning.orEmpty()
            binding.warningText.visibility =
                if (state.warning == null) View.GONE else View.VISIBLE
        }
    }
}
