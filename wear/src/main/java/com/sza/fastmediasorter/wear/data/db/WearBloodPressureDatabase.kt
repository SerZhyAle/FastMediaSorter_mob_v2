package com.sza.fastmediasorter.wear.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * S2809: the watch-side blood pressure history store.
 *
 * Separate from [WearHeartRateDatabase] (ADR-2): a different subsystem with different
 * recovery semantics - there are no files on disk to rebuild from, so the recovery path
 * is simpler (delete and recreate empty). The schema is exported so future migrations
 * have something to be diffed against.
 *
 * S3113: version 2 adds the source of each history row and the calibration pairs
 * ([WearBloodPressureMigrations.MIGRATION_1_2]); version 3 adds the pulse of each history row
 * ([WearBloodPressureMigrations.MIGRATION_2_3]).
 */
@Database(
    entities = [BloodPressureHistoryEntity::class, BloodPressureCalibrationEntity::class],
    version = 3,
    exportSchema = true
)
abstract class WearBloodPressureDatabase : RoomDatabase() {

    abstract fun bloodPressureHistoryDao(): BloodPressureHistoryDao

    abstract fun bloodPressureCalibrationDao(): BloodPressureCalibrationDao

    companion object {
        const val DATABASE_NAME = "wear_blood_pressure_history.db"
    }
}
