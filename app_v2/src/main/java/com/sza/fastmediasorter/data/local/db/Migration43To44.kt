package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 43 to 44.
 * S1009: adds an additive `is_hidden` boolean column to `resources` marking a resource hidden from
 * visible surfaces (main list, scheduled-op pickers, browse home, watch-sync). Existing rows default
 * to 0 (visible). Additive + NOT NULL DEFAULT, so no table recreation is needed.
 * Boolean-NOT-NULL precedent: MIGRATION_28_29 (`needs_sign_in`).
 */
val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE resources ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0")
    }
}
