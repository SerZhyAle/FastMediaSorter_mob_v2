package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 32 to 33.
 * Adds the stream_sources table for the S0565 "Трансляции" internet-stream catalog.
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stream_sources (
                id TEXT PRIMARY KEY NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                mediaKind TEXT NOT NULL,
                sourceOrigin TEXT NOT NULL,
                sortIndex INTEGER NOT NULL,
                pinned INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                lastPlayedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_stream_sources_url ON stream_sources(url)"
        )
    }
}
