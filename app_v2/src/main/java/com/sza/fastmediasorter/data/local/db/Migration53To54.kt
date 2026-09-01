package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

/**
 * S2251: Migration from schema version 53 to 54.
 * Adds `screen_index` column to `launcher_cells` table with default value 0.
 */
private const val SCHEMA_VERSION_FROM = 53
private const val SCHEMA_VERSION_TO = 54

val MIGRATION_53_54 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `launcher_cells` ADD COLUMN `screen_index` INTEGER NOT NULL DEFAULT 0")
        Timber.i("S2251: Migrated database from schema 53 to 54 (added screen_index to launcher_cells)")
    }
}
