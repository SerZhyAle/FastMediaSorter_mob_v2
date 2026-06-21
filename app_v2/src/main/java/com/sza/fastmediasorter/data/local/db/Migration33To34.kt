package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 33 to 34.
 * S0570: adds nullable category/topic/language columns to stream_sources for the curated catalog
 * import. Forward-only ALTERs on a v33 table - manual/imported rows simply keep these null.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE stream_sources ADD COLUMN category TEXT")
        db.execSQL("ALTER TABLE stream_sources ADD COLUMN topic TEXT")
        db.execSQL("ALTER TABLE stream_sources ADD COLUMN language TEXT")
    }
}
