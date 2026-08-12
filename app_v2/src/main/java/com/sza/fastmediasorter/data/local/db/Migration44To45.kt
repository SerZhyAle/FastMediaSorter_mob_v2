package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 44 to 45.
 * S1378: adds an additive nullable `storage_volume_id` column to `resources` binding a resource to
 * the `StorageVolumeInfo.id` it lives on. Existing rows keep NULL, which means "not bound to any
 * volume" and preserves today's behaviour exactly - no resource has to be re-granted access.
 * Nullable-TEXT precedent: MIGRATION_42_43 (the `stream_sources` track-memory columns).
 */
private const val SCHEMA_VERSION_FROM = 44
private const val SCHEMA_VERSION_TO = 45

val MIGRATION_44_45 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE resources ADD COLUMN storage_volume_id TEXT DEFAULT NULL")
    }
}
