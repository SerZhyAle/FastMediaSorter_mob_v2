package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 49 to 50.
 * S1511: adds `stream_quality_memory`, what a stream channel learned about its video-quality rung.
 *
 * Purely additive - no existing table is touched, so there is no copy-and-rename here and no data can be
 * lost. The statement mirrors what Room generates for `StreamQualityMemoryEntity` character for character,
 * composite primary key included: `runMigrationsAndValidate` compares the migrated schema against the
 * exported 50.json and fails on any divergence, which is why it is written out by hand rather than trusted
 * to match.
 */
private const val SCHEMA_VERSION_FROM = 49
private const val SCHEMA_VERSION_TO = 50

private const val CREATE_QUALITY_MEMORY_TABLE =
    "CREATE TABLE IF NOT EXISTS `stream_quality_memory` (" +
        "`normalizedUrl` TEXT NOT NULL, `rungBitrateBps` INTEGER NOT NULL, " +
        "`ceilingWidthPx` INTEGER NOT NULL, `ceilingHeightPx` INTEGER NOT NULL, " +
        "`probeFailureCount` INTEGER NOT NULL, `recordedAt` INTEGER NOT NULL, " +
        "PRIMARY KEY(`normalizedUrl`, `rungBitrateBps`))"

val MIGRATION_49_50 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_QUALITY_MEMORY_TABLE)
    }
}
