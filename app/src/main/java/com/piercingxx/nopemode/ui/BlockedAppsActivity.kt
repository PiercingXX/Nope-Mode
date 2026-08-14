package com.piercingxx.nopemode.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.piercingxx.nopemode.R
import com.piercingxx.nopemode.data.BlockedApp
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.databinding.ActivityBlockedAppsBinding
import com.piercingxx.nopemode.databinding.ItemBlockedAppBinding
import com.piercingxx.nopemode.enforce.ProtectedPackages
import com.piercingxx.nopemode.enforce.SuspendablePackages.Exclusion
import com.piercingxx.nopemode.schedule.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WS7 — the blocked-apps screen (design §11, §4.1).
 *
 * This is the one screen without which the product does not exist: it is the
 * only writer to `blocked_app`, and everything downstream — the reconcile loop,
 * the enforcer, the tile — operates on the set it produces.
 *
 * Every launchable app is listed. The ONLY rows that cannot be selected are the
 * ones the platform itself refuses — verified on-device: Android logs
 * 'Cannot suspend package "...": is the default dialer' and leaves it running.
 * Those are shown disabled with the reason inline rather than hidden, so the
 * user can never assume an app is blocked when it isn't (design §4.1, R8).
 *
 * Nothing else is gated. This is the user's device; if they want to block their
 * SMS app they can, and the row simply carries a caution about 2FA codes.
 *
 * Every edit reconciles immediately (design §7.1), so checking an app while
 * Nope-Mode is already active suspends it there and then.
 */
class BlockedAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedAppsBinding
    private val adapter = AppAdapter(::onRowClicked)

    private var installed: List<AppPicker.InstalledApp> = emptyList()
    private var protections = ProtectedPackages.Protections(emptyMap(), emptyMap(), emptyMap())
    private var blocked: Set<String> = emptySet()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                render()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val db = NopeDatabase.get(applicationContext)
            val loaded = withContext(Dispatchers.IO) {
                Triple(
                    ProtectedPackages.launchableApps(packageManager)
                        .map { AppPicker.InstalledApp(it.first, it.second) },
                    ProtectedPackages.discover(this@BlockedAppsActivity),
                    db.blockedAppDao().observeAll().first().map { it.packageName }.toSet(),
                )
            }
            installed = loaded.first
            protections = loaded.second
            blocked = loaded.third

            // An app can become protected after it was blocked — the user
            // switching to a keyboard they had already selected, say. Drop those
            // rows rather than leaving a block that can never be enforced (R8).
            val stale = AppPicker.staleBlocked(blocked, protections.hardBlocked)
            if (stale.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    stale.forEach { db.blockedAppDao().deleteByPackage(it) }
                }
                blocked = blocked - stale
            }
            render()
        }
    }

    private fun render() {
        val rows = AppPicker.rows(
            installed = installed,
            blocked = blocked,
            hardBlocked = protections.hardBlocked,
            requiresConfirmation = protections.requiresConfirmation,
            advisories = protections.advisories,
            query = query,
        )
        adapter.submit(rows)
        binding.emptyText.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onRowClicked(row: AppPicker.Row) {
        if (!row.selectable) return
        if (!row.checked && row.exclusion is Exclusion.RequiresConfirmation) {
            confirmThenToggle(row)
            return
        }
        toggle(row)
    }

    /** Never reachable by bulk-select; the user must say yes to this one. */
    private fun confirmThenToggle(row: AppPicker.Row) {
        AlertDialog.Builder(this)
            .setTitle(row.label)
            .setMessage(row.reason)
            .setPositiveButton(R.string.blocked_apps_confirm_block) { _, _ -> toggle(row) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggle(row: AppPicker.Row) {
        val nowBlocked = !row.checked
        blocked = if (nowBlocked) blocked + row.packageName else blocked - row.packageName
        render()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = NopeDatabase.get(applicationContext).blockedAppDao()
                if (nowBlocked) {
                    dao.insert(BlockedApp(packageName = row.packageName))
                } else {
                    dao.deleteByPackage(row.packageName)
                }
                // Design §7.1: a blocked-list edit is a reconcile trigger. Route
                // through the loop, never straight at the enforcer (D8).
                runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }
            }
        }
    }

    private class AppAdapter(
        private val onClick: (AppPicker.Row) -> Unit,
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        private var rows: List<AppPicker.Row> = emptyList()

        fun submit(next: List<AppPicker.Row>) {
            rows = next
            notifyDataSetChanged()
        }

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemBlockedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = rows[position]
            with(holder.binding) {
                appLabel.text = row.label
                appPackage.text = row.packageName
                appCheck.isChecked = row.checked
                appCheck.isEnabled = row.selectable

                val reason = row.reason
                appReason.visibility = if (reason == null) View.GONE else View.VISIBLE
                appReason.text = reason

                // A hard-excluded row stays visible but reads as unavailable.
                val alpha = if (row.selectable) 1f else 0.5f
                appLabel.alpha = alpha
                appPackage.alpha = alpha

                root.isEnabled = row.selectable
                root.setOnClickListener { onClick(row) }
            }
        }

        class VH(val binding: ItemBlockedAppBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
