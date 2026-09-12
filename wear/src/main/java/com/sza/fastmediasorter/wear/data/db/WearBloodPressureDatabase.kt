package com.sza.fastmediasorter.wear.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * S2809: the watch-side blood pressure history store, version 1.
 *
 * Separate from [WearHeartRateDatabase] (ADR-2): a different subsystem with different
 * recovery semantics - there are no files on disk to rebuild from, so the recovery path
 * is simpler (delete and recreate empty). The schema is exported so future migrations
 * have something to be diffed against.
 */
@Database(entities = [BloodPressureHistoryEntity::class], version = 1, exportSchema = true)
abstract class WearBloodPressureDatabase : RoomDatabase() {

    abstract fun bloodPressureHistoryDao(): BloodPressureHistoryDao

    companion object {
        const val DATABASE_NAME = "wear_blood_pressure_history.db"
    }
}
