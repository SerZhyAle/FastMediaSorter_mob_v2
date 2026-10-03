package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_FROM = 59
private const val SCHEMA_VERSION_TO = 60

/**
 * S4075: optional per-file conditions of a scheduled operation, plus the moment of its last successful run.
 *
 * Every column is nullable with no default, matching the entity exactly: NULL is "no condition" and "never
 * succeeded", so an upgraded operation keeps selecting the same files it selected before.
 */
val MIGRATION_59_60 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `scheduled_operations` ADD COLUMN `file_name_mask` TEXT")
        db.execSQL("ALTER TABLE `scheduled_operations` ADD COLUMN `min_age_hours` INTEGER")
        db.execSQL("ALTER TABLE `scheduled_operations` ADD COLUMN `max_age_hours` INTEGER")
        db.execSQL("ALTER TABLE `scheduled_operations` ADD COLUMN `min_size_bytes` INTEGER")
        db.execSQL("ALTER TABLE `scheduled_operations` ADD COLUMN `max_size_bytes` INTEGER")
        db.execSQL("ALTER TABLE `scheduled_operations` ADD COLUMN `last_success_at` INTEGER")
    }
}
