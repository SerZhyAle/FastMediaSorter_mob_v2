package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_FROM = 55
private const val SCHEMA_VERSION_TO = 56

/** Preserves existing desktop cells as user-managed while adding automatic-install provenance. */
val MIGRATION_55_56 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE launcher_cells ADD COLUMN origin TEXT NOT NULL DEFAULT 'USER'")
    }
}
