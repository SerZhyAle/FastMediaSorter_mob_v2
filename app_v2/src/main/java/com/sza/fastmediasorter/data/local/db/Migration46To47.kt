package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 46 to 47.
 * S1179: adds `sensor_series_point`, the accumulated samples behind the launcher speed and altitude
 * charts, and touches no existing table. The series must survive an application update - a chart
 * whose history reset itself on upgrade would make its reset button meaningless - so the table is
 * migrated rather than rebuilt.
 */
private const val SCHEMA_VERSION_FROM = 46
private const val SCHEMA_VERSION_TO = 47

val MIGRATION_46_47 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sensor_series_point` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`seriesId` TEXT NOT NULL, " +
                "`takenAtMillis` INTEGER NOT NULL, " +
                "`primaryValue` REAL NOT NULL, " +
                "`secondaryValue` REAL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sensor_series_point_seriesId_takenAtMillis` " +
                "ON `sensor_series_point` (`seriesId`, `takenAtMillis`)"
        )
    }
}
