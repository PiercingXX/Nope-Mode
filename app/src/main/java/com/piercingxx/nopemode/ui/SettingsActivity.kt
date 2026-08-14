package com.piercingxx.nopemode.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.piercingxx.nopemode.R
import com.piercingxx.nopemode.core.BreakPolicy
import com.piercingxx.nopemode.core.FrictionSettings
import com.piercingxx.nopemode.core.NopeController
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.OverrideMapper
import com.piercingxx.nopemode.data.SettingsStore
import com.piercingxx.nopemode.databinding.ActivitySettingsBinding
import com.piercingxx.nopemode.schedule.AlarmScheduler
import com.piercingxx.nopemode.service.RingerPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * WS7 — Settings (design §9, §18.5).
 *
 * Friction settings are editable **only while Nope-Mode is inactive**, enforced
 * by [BreakPolicy.canEditFrictionSettings]. Without that, the 2am workaround is
 * simply to raise the break budget, and every other friction is theatre.
 *
 * The controls stay visible when locked rather than disappearing: a setting you
 * cannot find reads as a missing feature, one that is greyed with a reason
 * reads as a rule.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var editable = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FrictionSettings.BREAK_CHOICES.forEach { minutes ->
            binding.breakLengthGroup.addView(
                RadioButton(this).apply {
                    id = minutes
                    text = getString(R.string.break_minutes, minutes)
                    setTextColor(getColor(R.color.pxx_white_90))
                    textSize = 14f
                }
            )
        }

        binding.grantDndButton.setOnClickListener {
            // The grant is a user action in system Settings — there is no API to
            // award it to ourselves, device owner or not.
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onPause() {
        super.onPause()
        if (editable) persist()
    }

    private fun load() {
        val settings = SettingsStore(this).load()
        binding.quietRingerCheck.isChecked = settings.quietRingerEnabled
        binding.repeatCallersCheck.isChecked = settings.allowRepeatCallers
        binding.minIntervalInput.setText(settings.minIntervalMinutes.toString())
        binding.budgetInput.setText(settings.breakBudgetPerWindow.toString())
        binding.breakLengthGroup.check(settings.breakDurationMinutes)

        val granted = runCatching { RingerPolicy(applicationContext).isGranted() }.getOrDefault(false)
        binding.dndWarning.visibility = if (granted) View.GONE else View.VISIBLE
        binding.dndWarning.text = getString(R.string.dnd_missing)
        binding.grantDndButton.visibility = if (granted) View.GONE else View.VISIBLE

        lifecycleScope.launch {
            val active = withContext(Dispatchers.IO) {
                val db = NopeDatabase.get(applicationContext)
                NopeController.derive(
                    LocalDateTime.now(),
                    db.scheduleDao().observeAll().first(),
                    OverrideMapper.toOverride(db.appStateDao().get()),
                )
            }
            editable = BreakPolicy.canEditFrictionSettings(active)
            applyLock()
        }
    }

    private fun applyLock() {
        binding.lockedText.visibility = if (editable) View.GONE else View.VISIBLE
        listOf(
            binding.quietRingerCheck,
            binding.repeatCallersCheck,
            binding.minIntervalInput,
            binding.budgetInput,
        ).forEach {
            it.isEnabled = editable
            it.alpha = if (editable) 1f else 0.5f
        }
        for (i in 0 until binding.breakLengthGroup.childCount) {
            binding.breakLengthGroup.getChildAt(i).isEnabled = editable
        }
    }

    /**
     * Persist on leaving the screen, then reconcile so a changed Quiet Ringer
     * toggle takes effect immediately rather than at the next boundary.
     */
    private fun persist() {
        val current = SettingsStore(this).load()
        val settings = FrictionSettings(
            breakDurationMinutes = binding.breakLengthGroup.checkedRadioButtonId
                .takeIf { it in FrictionSettings.BREAK_CHOICES }
                ?: current.breakDurationMinutes,
            // A blank or nonsense box keeps the previous value rather than
            // silently becoming zero, which would disable the friction entirely.
            minIntervalMinutes = binding.minIntervalInput.text.toString().toIntOrNull()
                ?.coerceIn(0, 24 * 60) ?: current.minIntervalMinutes,
            breakBudgetPerWindow = binding.budgetInput.text.toString().toIntOrNull()
                ?.coerceIn(0, 99) ?: current.breakBudgetPerWindow,
            quietRingerEnabled = binding.quietRingerCheck.isChecked,
            allowRepeatCallers = binding.repeatCallersCheck.isChecked,
        )
        SettingsStore(this).save(settings)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }
            }
        }
    }
}
