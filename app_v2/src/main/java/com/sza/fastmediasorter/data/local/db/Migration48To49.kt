package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 48 to 49.
 * S1433: adds `network_measurement`, the Network Monitor's history of manual measurements.
 *
 * Purely additive - no existing table is touched, so there is no copy-and-rename here and no data can be
 * lost. The statements mirror what Room generates for `NetworkMeasurementEntity` character for character,
 * including the index name Room derives from the column: `runMigrationsAndValidate` compares the migrated
 * schema against the exported 49.json and fails on any divergence, which is the whole point of writing it
 * out by hand rather than trusting the entity to match.
 */
private const val SCHEMA_VERSION_FROM = 48
private const val SCHEMA_VERSION_TO = 49

private const val CREATE_MEASUREMENT_TABLE =
    "CREATE TABLE IF NOT EXISTS `network_measurement` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `takenAtMillis` INTEGER NOT NULL, " +
        "`kind` TEXT NOT NULL, `networkLabel` TEXT NOT NULL, `downMbps` REAL, `upMbps` REAL, " +
        "`latencyMs` INTEGER, `resultText` TEXT NOT NULL, `succeeded` INTEGER NOT NULL)"

private const val CREATE_TAKEN_AT_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_network_measurement_takenAtMillis` " +
        "ON `network_measurement` (`takenAtMillis`)"

val MIGRATION_48_49 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_MEASUREMENT_TABLE)
        db.execSQL(CREATE_TAKEN_AT_INDEX)
    }
}
