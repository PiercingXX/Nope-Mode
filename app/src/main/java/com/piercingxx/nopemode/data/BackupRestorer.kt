package com.piercingxx.nopemode.data

import androidx.room.withTransaction

/**
 * Commit a validated backup in one transaction, then the caller reconciles.
 * Never call this without [BackupValidator.validate] returning valid.
 */
object BackupRestorer {

    suspend fun restore(db: NopeDatabase, store: SettingsStore, data: BackupJson.BackupData) {
        db.withTransaction {
            db.blockedAppDao().clear()
            if (data.blockedApps.isNotEmpty()) {
                db.blockedAppDao().insertAll(data.blockedApps)
            }
            db.scheduleDao().clear()
            if (data.schedules.isNotEmpty()) {
                db.scheduleDao().insertAll(data.schedules)
            }
        }
        val current = store.load()
        store.save(
            current.copy(
                breakDurationMinutes = data.settings.breakDurationMinutes,
                minIntervalMinutes = data.settings.minIntervalMinutes,
                breakBudgetPerWindow = data.settings.breakBudgetPerWindow,
            ),
        )
    }
}
