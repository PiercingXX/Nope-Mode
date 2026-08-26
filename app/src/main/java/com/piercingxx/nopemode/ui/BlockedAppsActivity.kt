package com.piercingxx.nopemode.ui

import android.graphics.drawable.Drawable
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
import com.piercingxx.nopemode.data.ReconcileStatus
import com.piercingxx.nopemode.data.SettingsStore
import com.piercingxx.nopemode.databinding.ActivityBlockedAppsBinding
import com.piercingxx.nopemode.databinding.ItemBlockedAppBinding
import com.piercingxx.nopemode.databinding.ItemSectionHeaderBinding
import com.piercingxx.nopemode.enforce.ProtectedPackages
import com.piercingxx.nopemode.schedule.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WS7 — the blocked-apps screen (design §11, §4.1), shaped like Focus Mode's:
 * the apps you have already chosen at the top under their own heading, the rest
 * below, every row carrying its icon.
 *
 * This is the only writer to `blocked_app`, and everything downstream — the
 * reconcile loop, the enforcer, the tile — operates on the set it produces.
 *
 * The only rows that cannot be selected are the ones the platform itself
 * refuses — verified on-device, Android logs
 * 'Cannot suspend package "...": is the default dialer' and leaves it running.
 * Those stay visible and disabled with the reason inline rather than vanishing,
 * so the user can never assume an app is blocked when it isn't (§4.1, R8).
 * Nothing else is gated: it is the user's device.
 *
 * Every edit reconciles immediately (design §7.1), so checking an app while
 * Nope-Mode is already active suspends it there and then.
 */
class BlockedAppsActivity : BrandActivity() {

    private lateinit var binding: ActivityBlockedAppsBinding
    private val adapter = AppAdapter(::onRowClicked)

    private var installed: List<AppPicker.InstalledApp> = emptyList()
    private var protections = ProtectedPackages.Protections(emptyMap(), emptyMap(), emptyMap())
    private var blocked: Set<String> = emptySet()
    private var icons: Map<String, Drawable> = emptyMap()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()

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

    private data class Loaded(
        val installed: List<AppPicker.InstalledApp>,
        val protections: ProtectedPackages.Protections,
        val blocked: Set<String>,
        val icons: Map<String, Drawable>,
    )

    private fun load() {
        lifecycleScope.launch {
            val db = NopeDatabase.get(applicationContext)
            val loaded = withContext(Dispatchers.IO) {
                val apps = ProtectedPackages.launchableApps(packageManager)
                Loaded(
                    installed = apps.map { AppPicker.InstalledApp(it.first, it.second) },
                    protections = ProtectedPackages.discover(this@BlockedAppsActivity),
                    blocked = db.blockedAppDao().observeAll().first().map { it.packageName }.toSet(),
                    // Icons are resolved once here rather than per bind: loading
                    // one hits the package manager, and doing that on every row
                    // recycle janks the scroll.
                    icons = apps.mapNotNull { (pkg, _) ->
                        runCatching { pkg to packageManager.getApplicationIcon(pkg) }.getOrNull()
                    }.toMap(),
                )
            }
            installed = loaded.installed
            protections = loaded.protections
            blocked = loaded.blocked
            icons = loaded.icons

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
        val failed = ReconcileStatus(this).failedPackages()
            .associateWith { getString(R.string.suspend_failed_reason) }
        val items = AppPicker.sections(
            installed = installed,
            blocked = blocked,
            hardBlocked = protections.hardBlocked,
            requiresConfirmation = protections.requiresConfirmation,
            advisories = protections.advisories + failed,
            query = query,
        )
        adapter.submit(items, icons)
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onRowClicked(row: AppPicker.Row) {
        if (!row.selectable) return
        toggle(row)
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
            render()
        }
    }

    private class AppAdapter(
        private val onClick: (AppPicker.Row) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<AppPicker.Item> = emptyList()
        private var icons: Map<String, Drawable> = emptyMap()

        fun submit(next: List<AppPicker.Item>, nextIcons: Map<String, Drawable>) {
            items = next
            icons = nextIcons
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int) =
            if (items[position] is AppPicker.Item.Header) TYPE_HEADER else TYPE_APP

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(ItemSectionHeaderBinding.inflate(inflater, parent, false))
            } else {
                AppVH(ItemBlockedAppBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is AppPicker.Item.Header -> (holder as HeaderVH).binding.headerText.apply {
                    text = item.title
                    setTextColor(
                        BackgroundTheme.accentTextColor(
                            SettingsStore(context).backgroundPreset(),
                            context.getColor(com.piercingxx.nopemode.R.color.pxx_signal),
                        )
                    )
                }
                is AppPicker.Item.App -> bindApp((holder as AppVH).binding, item.row)
            }
        }

        private fun bindApp(binding: ItemBlockedAppBinding, row: AppPicker.Row) = with(binding) {
            appLabel.text = row.label
            appCheck.isChecked = row.checked
            appCheck.isEnabled = row.selectable
            appIcon.setImageDrawable(icons[row.packageName])

            val reason = row.reason
            appReason.visibility = if (reason == null) View.GONE else View.VISIBLE
            appReason.text = reason

            // A hard-excluded row stays visible but reads as unavailable.
            val alpha = if (row.selectable) 1f else 0.5f
            appLabel.alpha = alpha
            appIcon.alpha = alpha

            root.isEnabled = row.selectable
            root.setOnClickListener { onClick(row) }
        }

        class AppVH(val binding: ItemBlockedAppBinding) : RecyclerView.ViewHolder(binding.root)
        class HeaderVH(val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)

        private companion object {
            const val TYPE_HEADER = 0
            const val TYPE_APP = 1
        }
    }
}
