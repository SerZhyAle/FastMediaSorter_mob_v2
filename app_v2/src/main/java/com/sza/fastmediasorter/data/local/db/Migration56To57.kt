package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_FROM = 56
private const val SCHEMA_VERSION_TO = 57

/**
 * S2669: adds the two curated-collection tables. Purely additive - no existing table is altered, so
 * user-authored stream rows and pins survive the upgrade exactly as they survive a catalog import.
 */
val MIGRATION_56_57 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stream_collections` (" +
                "`collectionId` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`namesJson` TEXT NOT NULL, " +
                "PRIMARY KEY(`collectionId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stream_collection_members` (" +
                "`collectionId` TEXT NOT NULL, " +
                "`url` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`collectionId`, `url`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stream_collection_members_url` " +
                "ON `stream_collection_members` (`url`)"
        )
    }
}
