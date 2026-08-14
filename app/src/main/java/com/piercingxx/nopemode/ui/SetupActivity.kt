package com.piercingxx.nopemode.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.piercingxx.nopemode.admin.DeviceOwnerManager
import com.piercingxx.nopemode.databinding.ActivitySetupBinding

/**
 * WS7 — the setup screen. Shows the device-owner tier and, when not provisioned,
 * the exact ADB command to provision (design §2.2). Provisioning can only be
 * done on a device with zero accounts, so the command is surfaced verbatim.
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
    }

    override fun onResume() {
        super.onResume()
        val isDeviceOwner = deviceOwner.tier() == DeviceOwnerManager.Tier.DEVICE_OWNER
        binding.setupText.text = HomeStateText.provisionText(isDeviceOwner, deviceOwner.provisioningCommand())
    }
}