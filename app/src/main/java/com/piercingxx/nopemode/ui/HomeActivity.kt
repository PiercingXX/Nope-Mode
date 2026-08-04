package com.piercingxx.nopemode.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.piercingxx.nopemode.admin.DeviceOwnerManager
import com.piercingxx.nopemode.databinding.ActivityHomeBinding

/**
 * WS1/WS4 home screen. Deliberately thin: it reports which enforcement tier is
 * active and, when not provisioned, the command needed to fix that. The real
 * UI arrives in WS7 — this exists so provisioning can be verified on-device.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var deviceOwner: DeviceOwnerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        deviceOwner = DeviceOwnerManager(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        binding.stateText.text = getString(com.piercingxx.nopemode.R.string.app_name)

        when (deviceOwner.tier()) {
            DeviceOwnerManager.Tier.DEVICE_OWNER -> {
                binding.tierText.text =
                    "Enforcement: Device owner — apps can be fully suspended."
                binding.provisionText.text = ""
            }
            DeviceOwnerManager.Tier.FALLBACK -> {
                binding.tierText.text =
                    "Enforcement: Limited — not provisioned as device owner. " +
                        "Notifications can only be dismissed after they post, " +
                        "so a sound may play first."
                binding.provisionText.text = deviceOwner.provisioningCommand()
            }
        }
    }
}
