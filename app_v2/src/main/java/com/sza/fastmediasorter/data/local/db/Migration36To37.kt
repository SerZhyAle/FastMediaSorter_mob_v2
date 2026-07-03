package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 36 to 37.
 * S0761: adds the nullable country column to stream_sources, mirroring language (Migration33To34).
 * Holds the curated catalog's ISO 3166-1 alpha-2 code; manual/imported rows keep it null.
 */
@Suppress("MagicNumber")
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE stream_sources ADD COLUMN country TEXT")
    }
}
