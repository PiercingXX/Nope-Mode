package com.piercingxx.nopemode.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.piercingxx.nopemode.R
import com.piercingxx.nopemode.data.BackupJson
import com.piercingxx.nopemode.data.BackupRestorer
import com.piercingxx.nopemode.data.BackupValidator
import com.piercingxx.nopemode.data.NopeDatabase
import com.piercingxx.nopemode.data.SettingsStore
import com.piercingxx.nopemode.databinding.ActivityBackupBinding
import com.piercingxx.nopemode.schedule.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Export / restore blocked apps, schedules, and friction settings as JSON.
 * Restore commits only after [BackupValidator] accepts the payload.
 */
class BackupActivity : BrandActivity() {

    private lateinit var binding: ActivityBackupBinding

    private val createDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) exportTo(uri)
    }

    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importFrom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()
        binding.exportButton.setOnClickListener {
            createDoc.launch("nopemode-backup.json")
        }
        binding.restoreButton.setOnClickListener {
            openDoc.launch(arrayOf("application/json", "text/*", "*/*"))
        }
    }

    private fun exportTo(uri: Uri) {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                val db = NopeDatabase.get(applicationContext)
                val settings = SettingsStore(applicationContext).load()
                BackupJson.export(
                    db.blockedAppDao().observeAll().first(),
                    db.scheduleDao().observeAll().first(),
                    BackupJson.Settings(
                        breakDurationMinutes = settings.breakDurationMinutes,
                        minIntervalMinutes = settings.minIntervalMinutes,
                        breakBudgetPerWindow = settings.breakBudgetPerWindow,
                    ),
                )
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("could not open output")
                }.isSuccess
            }
            Toast.makeText(
                this@BackupActivity,
                if (ok) R.string.backup_exported else R.string.backup_export_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) {
                val json = runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull() ?: return@withContext getString(R.string.backup_import_failed)
                val data = BackupJson.import(json)
                    ?: return@withContext getString(R.string.backup_malformed)
                val result = BackupValidator.validate(data)
                if (!result.valid) {
                    return@withContext result.errors.joinToString("\n")
                }
                BackupRestorer.restore(
                    NopeDatabase.get(applicationContext),
                    SettingsStore(applicationContext),
                    data,
                )
                runCatching { AlarmScheduler.from(applicationContext).reconcileAndApply() }
                getString(R.string.backup_restored)
            }
            Toast.makeText(this@BackupActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}
