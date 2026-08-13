package com.piercingxx.nopemode.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * T2 — NopeDatabase (design §10, WS2). The Room database for the whole app.
 *
 * Version 1, all five entities. On a fresh install the default schedule
 * (20:00 -> 08:00, all days, enabled) is seeded via a
 * [RoomDatabase.Callback.onCreate] so the app is already enforcing the shipped
 * default before the user configures anything.
 *
 * Android-dependent — its *behavior* is deferred to the operator's on-device
 * check (fresh install has the 20:00 -> 08:00 seed row). The pure parts of the
 * data layer (the mappers, [SeedSchedule]) are JVM-provable.
 */
@Database(
    entities = [
        BlockedApp::class,
        Schedule::class,
        AppState::class,
        SuspendRecord::class,
        BreakLog::class
    ],
    version = 1,
    exportSchema = true
)
abstract class NopeDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun appStateDao(): AppStateDao
    abstract fun suspendRecordDao(): SuspendRecordDao
    abstract fun breakLogDao(): BreakLogDao

    companion object {
        private const val NAME = "nope-mode.db"

        @Volatile
        private var instance: NopeDatabase? = null

        /** Process-wide singleton. */
        fun get(context: Context): NopeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NopeDatabase::class.java,
                    NAME
                )
                    .addCallback(SEED_CALLBACK)
                    .build()
                    .also { instance = it }
            }

        /**
         * Seed the default schedule on a fresh install. Runs inside the first
         * transaction after the schema is created, so a fresh install always
         * starts with the shipped 20:00 -> 08:00 window (design §10).
         */
        private val SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val s = SeedSchedule.defaultSchedule()
                db.execSQL(
                    "INSERT INTO schedule " +
                        "(id, startMinuteOfDay, endMinuteOfDay, daysMask, enabled) " +
                        "VALUES (?, ?, ?, ?, ?)",
                    arrayOf(s.id, s.startMinuteOfDay, s.endMinuteOfDay, s.daysMask, if (s.enabled) 1 else 0)
                )
            }
        }
    }
}