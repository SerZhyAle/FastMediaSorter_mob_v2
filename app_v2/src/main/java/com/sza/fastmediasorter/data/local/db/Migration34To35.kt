package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 34 to 35.
 * S0593: adds nullable lastPlayOutcome/lastPlayOutcomeAt columns to stream_sources for the local
 * play-status bullet. Forward-only ALTERs on a v34 table - existing rows keep these null (unknown).
 */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE stream_sources ADD COLUMN lastPlayOutcome TEXT")
        db.execSQL("ALTER TABLE stream_sources ADD COLUMN lastPlayOutcomeAt INTEGER")
    }
}
