package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 41 to 42.
 * S1117: adds the `access` column to `stream_sources` - the curated catalog's region-restriction flag
 * ("geo" = region-locked, may still play in-region; null/blank = open). Purely additive and nullable,
 * so existing rows default to NULL (open) and the streams list simply shows no geo badge for them.
 *
 * The statement is copied verbatim from `42.json`'s `createSql` intent (ADD COLUMN), so the column this
 * creates cannot drift from the one Room validates when opening the database.
 */
val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `access` TEXT DEFAULT NULL")
    }
}
