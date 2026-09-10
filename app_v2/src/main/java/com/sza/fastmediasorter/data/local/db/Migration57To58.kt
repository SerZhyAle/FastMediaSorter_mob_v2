package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_FROM = 57
private const val SCHEMA_VERSION_TO = 58

/**
 * S2813: adds the stream catalog's device-identity column.
 *
 * Purely additive and nullable, with no DEFAULT clause and no `@ColumnInfo(defaultValue = ..)` on the
 * entity, so what `runMigrationsAndValidate` compares and what this migration produces agree - a
 * default declared on one side only is the shape that fails validation while looking correct.
 */
val MIGRATION_57_58 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stream_sources` ADD COLUMN `sourceDeviceId` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stream_sources_sourceDeviceId` " +
                "ON `stream_sources` (`sourceDeviceId`)"
        )
    }
}
