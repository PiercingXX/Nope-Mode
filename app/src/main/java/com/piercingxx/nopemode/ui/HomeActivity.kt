package com.piercingxx.nopemode.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.piercingxx.nopemode.admin.DeviceOwnerManager
import com.piercingxx.nopemode.core.NopeController
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.databinding.ActivityHomeBinding
import com.piercingxx.nopemode.schedule.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * WS7 home screen (design §11).
 *
 * Reports the derived state — not a guess. Design §7.1 lists "app foregrounded"
 * as a reconcile trigger, so `onResume` reconciles before reading state back;
 * that keeps the screen honest if an alarm was missed while the app was away.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var deviceOwner: DeviceOwnerManager

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
    }

    private fun open(target: Class<out AppCompatActivity>) {
        startActivity(Intent(this, target))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
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
                Triple(active, override, blockedCount)
            }
            binding.stateText.text = HomeStateText.stateText(state.first, state.second)
            binding.blockedCountText.text = HomeStateText.blockedCountText(state.third)
        }
    }
}
