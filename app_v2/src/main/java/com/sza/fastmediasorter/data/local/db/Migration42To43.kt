package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 42 to 43.
 * S1144: adds per-channel track-memory columns to `stream_sources` - the preferred audio-language and
 * subtitle-language (ADR-2: language-code, not raw index) plus a nullable subtitles on/off override
 * (null = follow the global default). Purely additive and nullable, so existing rows default to NULL
 * (follow global default) and channels without a stored preference behave exactly as before.
 *
 * Room maps `Boolean?` to a nullable INTEGER column, so `subtitlesEnabled` is declared INTEGER here.
 */
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `preferredAudioLang` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `preferredSubtitleLang` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `subtitlesEnabled` INTEGER DEFAULT NULL")
    }
}
