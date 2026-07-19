package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 40 to 41.
 * S0404: creates the launcher-mode desktop tables - placed cells, the app's own launch journal,
 * taskbar pins, and desktop-wide state. Purely additive: users who never enable launcher mode keep
 * empty tables.
 *
 * Each statement is copied verbatim from `41.json`'s `createSql` (with `${'$'}{TABLE_NAME}` resolved), so
 * the tables this creates cannot drift from the ones Room validates when opening the database.
 */
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `launcher_cells` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`orientation` TEXT NOT NULL, " +
                "`rowIndex` INTEGER NOT NULL, " +
                "`colIndex` INTEGER NOT NULL, " +
                "`spanW` INTEGER NOT NULL, " +
                "`spanH` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`target` TEXT NOT NULL, " +
                "`labelOverride` TEXT, " +
                "`addedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `launcher_journal` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`target` TEXT NOT NULL, " +
                "`launchedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `launcher_pins` (" +
                "`position` INTEGER NOT NULL, " +
                "`target` TEXT NOT NULL, " +
                "PRIMARY KEY(`position`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `launcher_state` (" +
                "`id` INTEGER NOT NULL, " +
                "`seededPortrait` INTEGER NOT NULL, " +
                "`seededLandscape` INTEGER NOT NULL, " +
                "`columnsPortrait` INTEGER NOT NULL, " +
                "`columnsLandscape` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}
