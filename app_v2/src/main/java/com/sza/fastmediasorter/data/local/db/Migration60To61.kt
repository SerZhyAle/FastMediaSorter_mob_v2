package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_FROM = 60
private const val SCHEMA_VERSION_TO = 61

/**
 * S4156: a resource attached from the exchange directory remembers its account entry's identity and its
 * `resourceId`. Both columns are nullable with default NULL, matching the entity: every existing resource
 * stays unbound and behaves exactly as before.
 */
val MIGRATION_60_61 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `resources` ADD COLUMN `exchange_account` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `resources` ADD COLUMN `exchange_resource_id` TEXT DEFAULT NULL")
    }
}
