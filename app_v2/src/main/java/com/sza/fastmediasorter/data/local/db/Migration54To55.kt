package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

/**
 * S1905: Migration from schema version 54 to 55.
 * Adds the `launcher_cell_config` table for per-cell gadget configuration storage.
 */
private const val SCHEMA_VERSION_FROM = 54
private const val SCHEMA_VERSION_TO = 55

val MIGRATION_54_55 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `launcher_cell_config` (
                `cellId` INTEGER NOT NULL,
                `key` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                PRIMARY KEY(`cellId`, `key`),
                FOREIGN KEY(`cellId`) REFERENCES `launcher_cells`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        // No index on `cellId`, deliberately: LauncherCellConfigEntity declares none, and Room
        // validates the migrated table against the ENTITY, not against this SQL. Creating one here
        // made the two disagree, which Room reports as "Migration didn't properly handle" and the
        // recovery path answers by deleting the database - it did exactly that on the owner's phone
        // on 2026-09-05. If the foreign key ever needs an index, it is added to the entity first and
        // arrives on existing installs through a new migration, never by widening this one.
        Timber.i("Migrated database from schema 54 to 55 (added launcher_cell_config table)")
    }
}
