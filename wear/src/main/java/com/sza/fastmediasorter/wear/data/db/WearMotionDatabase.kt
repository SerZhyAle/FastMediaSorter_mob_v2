package com.sza.fastmediasorter.wear.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * S3014: The watch-side physical activity and motion measurement history store, version 1.
 */
@Database(entities = [MotionHistoryEntity::class], version = 1, exportSchema = true)
abstract class WearMotionDatabase : RoomDatabase() {

    abstract fun motionHistoryDao(): MotionHistoryDao

    companion object {
        const val DATABASE_NAME = "wear_motion_history.db"
    }
}
