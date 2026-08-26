package com.piercingxx.nopemode.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.piercingxx.nopemode.R
import com.piercingxx.nopemode.admin.DeviceOwnerManager
import com.piercingxx.nopemode.admin.RelinquishAction
import com.piercingxx.nopemode.databinding.ActivitySetupBinding
import com.piercingxx.nopemode.service.FallbackGrants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Setup: provisioning command, fallback grants, Relinquish (design §3.1, §11).
 */
class SetupActivity : BrandActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var deviceOwner: DeviceOwnerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()
        deviceOwner = DeviceOwnerManager(this)

        binding.grantListenerButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.grantAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.grantDndButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        binding.grantExactAlarmButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    },
                )
            }
        }
        binding.relinquishButton.setOnClickListener { confirmRelinquish() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val isOwner = deviceOwner.tier() == DeviceOwnerManager.Tier.DEVICE_OWNER
        binding.setupText.text = HomeStateText.provisionText(
            isOwner,
            deviceOwner.provisioningCommand(),
        )
        binding.setupText.visibility =
            if (isOwner) View.GONE else View.VISIBLE

        binding.tierText.text = HomeStateText.tierText(isOwner)

        val listener = FallbackGrants.notificationListenerEnabled(this)
        val a11y = FallbackGrants.accessibilityEnabled(this)
        binding.listenerStatus.text = getString(
            if (listener) R.string.grant_listener_on else R.string.grant_listener_off,
        )
        binding.accessibilityStatus.text = getString(
            if (a11y) R.string.grant_accessibility_on else R.string.grant_accessibility_off,
        )

        val fallback = !isOwner
        binding.fallbackGrantsGroup.visibility = if (fallback) View.VISIBLE else View.GONE
        binding.grantListenerButton.visibility =
            if (fallback && !listener) View.VISIBLE else View.GONE
        binding.grantAccessibilityButton.visibility =
            if (fallback && !a11y) View.VISIBLE else View.GONE

        binding.grantDndButton.visibility = View.VISIBLE
        binding.grantExactAlarmButton.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) View.VISIBLE else View.GONE

        binding.relinquishButton.visibility = if (isOwner) View.VISIBLE else View.GONE
        binding.relinquishHint.visibility = if (isOwner) View.VISIBLE else View.GONE
    }

    private fun confirmRelinquish() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.relinquish_title)
            .setMessage(R.string.relinquish_warning)
            .setPositiveButton(R.string.relinquish_confirm) { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        RelinquishAction.run(this@SetupActivity)
                    }
                    if (!ok) {
                        MaterialAlertDialogBuilder(this@SetupActivity)
                            .setMessage(R.string.relinquish_failed)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    render()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
