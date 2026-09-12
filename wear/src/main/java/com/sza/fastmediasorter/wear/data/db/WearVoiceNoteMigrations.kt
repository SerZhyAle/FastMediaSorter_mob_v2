package com.sza.fastmediasorter.wear.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * S2161: migrations for the watch voice-note database.
 */
object WearVoiceNoteMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE voice_notes ADD COLUMN publishedAddress TEXT DEFAULT NULL")
        }
    }
}
