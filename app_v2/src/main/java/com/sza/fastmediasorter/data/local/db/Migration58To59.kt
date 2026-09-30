package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_FROM = 58
private const val SCHEMA_VERSION_TO = 59

/**
 * S3836: turns the launcher journal from an event log into one row per command.
 *
 * Every duplicate collapses onto its newest launch before the unique index is created, since the index
 * would otherwise refuse to build over the history an upgraded device already carries. A tie on the
 * launch time keeps the highest id, which is the later insert. The launch counters live in their own
 * never-trimmed table and are deliberately left alone.
 */
val MIGRATION_58_59 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM `launcher_journal` WHERE `id` NOT IN (" +
                "SELECT (SELECT j2.`id` FROM `launcher_journal` j2 WHERE j2.`target` = j1.`target` " +
                "ORDER BY j2.`launchedAt` DESC, j2.`id` DESC LIMIT 1) " +
                "FROM `launcher_journal` j1 GROUP BY j1.`target`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_launcher_journal_target` " +
                "ON `launcher_journal` (`target`)"
        )
    }
}
