package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_FROM = 61
private const val SCHEMA_VERSION_TO = 62

/**
 * S4160: a stream row imported from a broadcast descriptor keeps the descriptor's whole endpoint list, so a
 * broadcast that is reachable through an exchange server plays outside the broadcaster's LAN.
 *
 * Nullable with no DEFAULT on either side: every existing row reads as one endpoint, its `url`, exactly as
 * before.
 */
val MIGRATION_61_62 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `broadcastEndpoints` TEXT")
    }
}
