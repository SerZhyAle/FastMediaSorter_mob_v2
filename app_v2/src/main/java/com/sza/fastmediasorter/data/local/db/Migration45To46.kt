package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 45 to 46.
 * S1401: adds two new tables and touches none of the existing ones. `installed_app_cache` is the
 * launchable-app cache the all-apps screen reads instead of waiting for the package manager;
 * `launcher_launch_stats` is the per-command launch counter the "most used" order needs, kept apart
 * from the trimmed `launcher_journal` so counting survives the trim.
 * Both hold derived data recoverable from the system, so a later format change rebuilds them rather
 * than migrating them.
 */
private const val SCHEMA_VERSION_FROM = 45
private const val SCHEMA_VERSION_TO = 46

val MIGRATION_45_46 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `installed_app_cache` (" +
                "`packageName` TEXT NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`labelSortKey` TEXT NOT NULL, " +
                "`firstInstallTime` INTEGER NOT NULL, " +
                "`lastUpdateTime` INTEGER NOT NULL, " +
                "`category` INTEGER NOT NULL, " +
                "`isSystemApp` INTEGER NOT NULL, " +
                "`iconFileName` TEXT, " +
                "`cacheFormatVersion` INTEGER NOT NULL, " +
                "`refreshedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`packageName`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `launcher_launch_stats` (" +
                "`target` TEXT NOT NULL, " +
                "`launchCount` INTEGER NOT NULL, " +
                "`lastLaunchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`target`))"
        )
    }
}
