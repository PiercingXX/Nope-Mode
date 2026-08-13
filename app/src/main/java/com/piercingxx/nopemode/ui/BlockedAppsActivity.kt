package com.piercingxx.nopemode.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.piercingxx.nopemode.databinding.ActivityBlockedAppsBinding

/**
 * WS7 — the blocked-apps screen. Lists the user-selected apps that Nope-Mode
 * suspends while active. The add/remove wiring is on-device; this screen exists
 * so the blocked list can be viewed and managed.
 */
class BlockedAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedAppsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}