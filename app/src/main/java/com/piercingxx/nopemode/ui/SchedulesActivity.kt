package com.piercingxx.nopemode.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.piercingxx.nopemode.databinding.ActivitySchedulesBinding

/**
 * WS7 — the schedules screen. Manages the active-window schedules (start, end,
 * days). The add/edit wiring is on-device; this screen exists so schedules can
 * be viewed and managed.
 */
class SchedulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySchedulesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySchedulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}