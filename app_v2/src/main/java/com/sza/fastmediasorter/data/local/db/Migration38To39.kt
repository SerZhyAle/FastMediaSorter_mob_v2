package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 38 to 39.
 * S1006: resources gain an optional ordered list of alternate SFTP access paths
 * ("host:port;host:port") so a companion resource can reach its server on the home
 * LAN or over the internet from a single import. Existing rows default to NULL
 * (single-path, unchanged behaviour).
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE resources ADD COLUMN alt_access_paths TEXT DEFAULT NULL")
    }
}
