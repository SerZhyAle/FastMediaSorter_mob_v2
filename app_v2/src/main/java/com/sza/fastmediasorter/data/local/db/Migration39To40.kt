package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 39 to 40.
 * S1014: resources gain an optional access-note carrying the companion's connectivity guidance
 * ("accessNote"), shown to the user when a connection fails. Existing rows default to NULL.
 */
val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE resources ADD COLUMN access_note TEXT DEFAULT NULL")
    }
}
