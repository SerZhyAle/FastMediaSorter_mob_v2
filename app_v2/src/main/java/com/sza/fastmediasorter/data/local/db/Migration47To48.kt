package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 47 to 48.
 * S1502: moves `lastPlayOutcome` / `lastPlayOutcomeAt` off `stream_sources` into the dedicated
 * `stream_play_outcome` table. Room invalidates per table, so while the outcome lived on the catalog
 * row every finished reachability probe re-emitted the whole 20k-row catalog to the streams list.
 *
 * Recorded outcomes are copied out before the catalog table is rebuilt, and `stream_sources` is
 * recreated rather than altered because SQLite on minSdk 23 has no DROP COLUMN. The unique index on
 * `url` is re-created after the rename - `DROP TABLE` takes it with the old table, and losing it
 * would let the next catalog import insert duplicate channels.
 */
private const val SCHEMA_VERSION_FROM = 47
private const val SCHEMA_VERSION_TO = 48

private const val CREATE_PLAY_OUTCOME_TABLE =
    "CREATE TABLE IF NOT EXISTS `stream_play_outcome` (" +
        "`streamId` TEXT NOT NULL, `outcome` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL, " +
        "PRIMARY KEY(`streamId`))"

private const val COPY_RECORDED_OUTCOMES =
    "INSERT OR REPLACE INTO `stream_play_outcome` (`streamId`, `outcome`, `recordedAt`) " +
        "SELECT `id`, `lastPlayOutcome`, COALESCE(`lastPlayOutcomeAt`, 0) FROM `stream_sources` " +
        "WHERE `lastPlayOutcome` IS NOT NULL"

private const val CREATE_STREAM_SOURCES_V48 =
    "CREATE TABLE IF NOT EXISTS `stream_sources_new` (" +
        "`id` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, " +
        "`mediaKind` TEXT NOT NULL, `sourceOrigin` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, " +
        "`pinned` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `lastPlayedAt` INTEGER, " +
        "`category` TEXT, `topic` TEXT, `language` TEXT, `country` TEXT, `access` TEXT, " +
        "`preferredAudioLang` TEXT, `preferredSubtitleLang` TEXT, `subtitlesEnabled` INTEGER, " +
        "PRIMARY KEY(`id`))"

private const val COPY_STREAM_SOURCES =
    "INSERT INTO `stream_sources_new` (`id`, `url`, `title`, `mediaKind`, `sourceOrigin`, " +
        "`sortIndex`, `pinned`, `addedAt`, `lastPlayedAt`, `category`, `topic`, `language`, " +
        "`country`, `access`, `preferredAudioLang`, `preferredSubtitleLang`, `subtitlesEnabled`) " +
        "SELECT `id`, `url`, `title`, `mediaKind`, `sourceOrigin`, `sortIndex`, `pinned`, " +
        "`addedAt`, `lastPlayedAt`, `category`, `topic`, `language`, `country`, `access`, " +
        "`preferredAudioLang`, `preferredSubtitleLang`, `subtitlesEnabled` FROM `stream_sources`"

private const val RECREATE_URL_INDEX =
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_stream_sources_url` ON `stream_sources` (`url`)"

val MIGRATION_47_48 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_PLAY_OUTCOME_TABLE)
        db.execSQL(COPY_RECORDED_OUTCOMES)
        db.execSQL(CREATE_STREAM_SOURCES_V48)
        db.execSQL(COPY_STREAM_SOURCES)
        db.execSQL("DROP TABLE `stream_sources`")
        db.execSQL("ALTER TABLE `stream_sources_new` RENAME TO `stream_sources`")
        db.execSQL(RECREATE_URL_INDEX)
    }
}
