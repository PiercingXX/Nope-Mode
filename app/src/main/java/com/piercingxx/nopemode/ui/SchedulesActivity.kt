package com.piercingxx.nopemode.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.nopemode.R
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.Schedule
import com.piercingxx.nopemode.data.SeedSchedule
import com.piercingxx.nopemode.databinding.ActivitySchedulesBinding
import com.piercingxx.nopemode.databinding.ItemScheduleBinding
import com.piercingxx.nopemode.schedule.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WS7 — Schedules (design §11, §7): list, add, edit, delete.
 *
 * This screen is how the active window is changed, which makes it the thing
 * that was missing when a schedule left no way out. Every edit reconciles
 * immediately (design §7.1), so shrinking or disabling a window takes effect at
 * once rather than at the next boundary — including when that is the window
 * currently holding apps suspended.
 *
 * The two ambiguities design §11 calls out are stated rather than left to the
 * reader: a wrapping window says "tonight → tomorrow", and a wrapping
 * schedule's day chips say they mean the night the window starts.
 *
 * Text formatting lives in [ScheduleText], which is pure and tested.
 */
class SchedulesActivity : BrandActivity() {

    private lateinit var binding: ActivitySchedulesBinding
    private val adapter = ScheduleAdapter(
        onToggleDay = ::onToggleDay,
        onPickStart = { pickTime(it, start = true) },
        onPickEnd = { pickTime(it, start = false) },
        onToggleEnabled = ::onToggleEnabled,
        onDelete = ::onDelete,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySchedulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()

        binding.scheduleList.layoutManager = LinearLayoutManager(this)
        binding.scheduleList.adapter = adapter
        binding.addButton.setOnClickListener { onAdd() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val schedules = withContext(Dispatchers.IO) {
                NopeDatabase.get(applicationContext).scheduleDao().observeAll().first()
            }
            adapter.submit(schedules)
            binding.emptyText.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Persist an edited schedule and reconcile so it applies now (§7.1). */
    private fun save(schedule: Schedule) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                NopeDatabase.get(applicationContext).scheduleDao().upsert(schedule)
                runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }
            }
            load()
        }
    }

    private fun onToggleDay(schedule: Schedule, dayIndex: Int) {
        save(schedule.copy(daysMask = ScheduleText.toggleDay(schedule.daysMask, dayIndex)))
    }

    private fun onToggleEnabled(schedule: Schedule, enabled: Boolean) {
        save(schedule.copy(enabled = enabled))
    }

    private fun onDelete(schedule: Schedule) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                NopeDatabase.get(applicationContext).scheduleDao().delete(schedule)
                runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }
            }
            load()
        }
    }

    private fun onAdd() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = NopeDatabase.get(applicationContext).scheduleDao()
                val existing = dao.observeAll().first()
                // Room's @PrimaryKey is not autoGenerate here, so pick the next
                // free id rather than colliding with an existing row.
                val nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1L
                dao.upsert(
                    Schedule(
                        id = nextId,
                        startMinuteOfDay = SeedSchedule.DEFAULT_START_MINUTE,
                        endMinuteOfDay = SeedSchedule.DEFAULT_END_MINUTE,
                        daysMask = SeedSchedule.ALL_DAYS_MASK,
                        enabled = true,
                    )
                )
                runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }
            }
            load()
        }
    }

    private fun pickTime(schedule: Schedule, start: Boolean) {
        val current = if (start) schedule.startMinuteOfDay else schedule.endMinuteOfDay
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val value = hour * 60 + minute
                save(
                    if (start) schedule.copy(startMinuteOfDay = value)
                    else schedule.copy(endMinuteOfDay = value)
                )
            },
            current / 60,
            current % 60,
            true,
        ).show()
    }

    private class ScheduleAdapter(
        private val onToggleDay: (Schedule, Int) -> Unit,
        private val onPickStart: (Schedule) -> Unit,
        private val onPickEnd: (Schedule) -> Unit,
        private val onToggleEnabled: (Schedule, Boolean) -> Unit,
        private val onDelete: (Schedule) -> Unit,
    ) : RecyclerView.Adapter<ScheduleAdapter.VH>() {

        private var schedules: List<Schedule> = emptyList()

        fun submit(next: List<Schedule>) {
            schedules = next
            notifyDataSetChanged()
        }

        override fun getItemCount() = schedules.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val schedule = schedules[position]
            val context = holder.itemView.context
            with(holder.binding) {
                windowText.text = ScheduleText.window(schedule)
                daysText.text = ScheduleText.days(schedule)

                val meaning = ScheduleText.dayMeaning(schedule)
                dayMeaningText.visibility = if (meaning == null) View.GONE else View.VISIBLE
                dayMeaningText.text = meaning

                startButton.text = context.getString(
                    R.string.start_at, ScheduleText.time(schedule.startMinuteOfDay)
                )
                endButton.text = context.getString(
                    R.string.end_at, ScheduleText.time(schedule.endMinuteOfDay)
                )
                startButton.setOnClickListener { onPickStart(schedule) }
                endButton.setOnClickListener { onPickEnd(schedule) }
                deleteButton.setOnClickListener { onDelete(schedule) }

                enabledSwitch.setOnCheckedChangeListener(null)
                enabledSwitch.isChecked = schedule.enabled
                enabledSwitch.setOnCheckedChangeListener { _, checked ->
                    onToggleEnabled(schedule, checked)
                }

                // Rebuild the chips each bind: the row is recycled, so leftover
                // chips from a previous schedule would show the wrong days.
                dayChips.removeAllViews()
                ScheduleText.DAY_INITIALS.forEachIndexed { index, initial ->
                    dayChips.addView(dayChip(context, initial, schedule, index))
                }

                val alpha = if (schedule.enabled) 1f else 0.45f
                windowText.alpha = alpha
                daysText.alpha = alpha
                dayChips.alpha = alpha
            }
        }

        private fun dayChip(
            context: android.content.Context,
            initial: String,
            schedule: Schedule,
            index: Int,
        ): TextView {
            val on = ScheduleText.isDaySet(schedule.daysMask, index)
            return TextView(context).apply {
                text = initial
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(
                    context.getColor(if (on) R.color.pxx_ink else R.color.pxx_white_50)
                )
                setBackgroundResource(
                    if (on) R.drawable.bg_day_chip_on else R.drawable.bg_day_chip_off
                )
                val size = (34 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (6 * resources.displayMetrics.density).toInt()
                }
                setOnClickListener { onToggleDay(schedule, index) }
            }
        }

        class VH(val binding: ItemScheduleBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
