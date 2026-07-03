package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 37 to 38.
 * S0783: the shared favorites table gains a kind discriminator so it can also hold live channels.
 * Existing rows default to 'FILE'; STREAM rows carry the channel media kind in streamMediaKind.
 */
@Suppress("MagicNumber")
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorites ADD COLUMN kind TEXT NOT NULL DEFAULT 'FILE'")
        db.execSQL("ALTER TABLE favorites ADD COLUMN streamMediaKind TEXT")
    }
}
