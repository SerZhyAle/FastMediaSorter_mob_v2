package com.sza.fastmediasorter.wear.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * S2808: the watch-side heart-rate history store, version 1.
 *
 * Separate from [WearVoiceNoteDatabase] (ADR-1): a different subsystem with different
 * recovery semantics - there are no files on disk to rebuild from, so the recovery path
 * is simpler. The schema is exported so future migrations have something to be diffed against.
 */
@Database(entities = [HeartRateHistoryEntity::class], version = 1, exportSchema = true)
abstract class WearHeartRateDatabase : RoomDatabase() {

    abstract fun heartRateHistoryDao(): HeartRateHistoryDao

    companion object {
        const val DATABASE_NAME = "wear_heart_rate_history.db"
    }
}
