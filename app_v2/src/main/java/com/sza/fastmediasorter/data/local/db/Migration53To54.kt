package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

/**
 * S2251: Migration from schema version 53 to 54.
 * Adds the `screenIndex` column to `launcher_cells` with default value 0.
 *
 * The column name must match [LauncherCellEntity.screenIndex] exactly - this migration shipped
 * as `screen_index`, Room refused the resulting schema on every upgrading install, and the destructive
 * fallback wiped the user's resources, credentials and desktop. The column name and its default are
 * both compared, so both are pinned here and in the entity.
 */
private const val SCHEMA_VERSION_FROM = 53
private const val SCHEMA_VERSION_TO = 54

val MIGRATION_53_54 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0")
        Timber.i("Migrated database from schema 53 to 54 (added screenIndex to launcher_cells)")
    }
}
