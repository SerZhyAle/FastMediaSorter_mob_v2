package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 31 to 32.
 * Adds device_profile table for S0327 device profile feature.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create device_profile table with single-row schema
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS device_profile (
                id INTEGER PRIMARY KEY NOT NULL,
                type TEXT NOT NULL,
                source TEXT NOT NULL,
                confidence TEXT NOT NULL,
                presetVersion INTEGER NOT NULL,
                appliedAtInstallTime INTEGER NOT NULL,
                lastModified INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
