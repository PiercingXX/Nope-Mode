package com.piercingxx.nopemode.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.piercingxx.nopemode.databinding.ActivitySettingsBinding

/**
 * WS7 — the settings screen. Friction settings (minimum break interval, break
 * budget) are editable only while Nope-Mode is inactive (design §9). The wiring
 * is on-device; this screen exists so settings can be viewed and managed.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}