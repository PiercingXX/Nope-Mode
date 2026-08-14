package com.piercingxx.nopemode.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.piercingxx.nopemode.core.Override
import com.piercingxx.nopemode.databinding.ActivityBlockedBinding
import java.time.Instant

/**
 * WS7 — the blocked screen shown while Nope-Mode is active. When a break is
 * running it shows the remaining countdown; otherwise it states that Nope-Mode
 * is active. The countdown is derived from the break's end instant (design §9).
 */
class BlockedActivity : BrandActivity() {

    private lateinit var binding: ActivityBlockedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        val breakUntil = intent.getLongExtra(EXTRA_BREAK_UNTIL, -1L)
        val now = Instant.now()
        if (breakUntil > 0) {
            val until = Instant.ofEpochMilli(breakUntil)
            binding.blockedText.text = HomeStateText.stateText(true, Override.Break(until), now)
            binding.countdownText.text =
                "Resumes in ${HomeStateText.breakCountdownMinutes(until, now)} min"
        } else {
            binding.blockedText.text = HomeStateText.stateText(true, Override.None, now)
            binding.countdownText.text = ""
        }
    }

    companion object {
        const val EXTRA_BREAK_UNTIL = "break_until_millis"
    }
}