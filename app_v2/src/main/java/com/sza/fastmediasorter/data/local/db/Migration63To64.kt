package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_FROM = 63
private const val SCHEMA_VERSION_TO = 64

/**
 * S4607, DEVICE-EXCHANGE item BJ: a live channel chosen from an account entry's directory remembers that entry and
 * the broadcast id it listed, so only that entry's directory ends it.
 *
 * Nullable with no DEFAULT on either side: every existing row reads as unbound and keeps the account-wide end rule
 * it had before.
 */
val MIGRATION_63_64 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `exchangeEntryId` TEXT")
        db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `exchangeBroadcastId` TEXT")
    }
}
