package com.sza.fastmediasorter.wear.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * S3113: migrations for the watch blood-pressure database.
 *
 * Version 2 keeps every diary row - they become `MANUAL`, which is what they were - and adds the table of
 * calibration pairs the estimate is fitted on.
 */
object WearBloodPressureMigrations {

    private const val SCHEMA_2 = 2
    private const val SCHEMA_3 = 3

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE blood_pressure_history ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS blood_pressure_calibration (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "systolic INTEGER NOT NULL, " +
                    "diastolic INTEGER NOT NULL, " +
                    "timestampMillis INTEGER NOT NULL, " +
                    "windowFile TEXT, " +
                    "heartRateBpm REAL NOT NULL, " +
                    "upstrokeSeconds REAL NOT NULL, " +
                    "width50Seconds REAL NOT NULL, " +
                    "perfusionIndex REAL NOT NULL, " +
                    "acceptedBeats INTEGER NOT NULL)"
            )
        }
    }

    /**
     * Version 3 adds the pulse measured with each reading, the third number a cuff shows. Nullable with no
     * default: a row written before the watch measured pulse has none, and a zero would read as a value.
     */
    val MIGRATION_2_3 = object : Migration(SCHEMA_2, SCHEMA_3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE blood_pressure_history ADD COLUMN pulse INTEGER")
        }
    }
}
