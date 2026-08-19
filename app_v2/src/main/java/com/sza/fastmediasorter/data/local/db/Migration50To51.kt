package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 50 to 51.
 * S1649: adds `orphaned_since` to `network_credentials` - the moment a credential was first seen with
 * no resource referencing it.
 *
 * The deletion deferral used to be measured from `createdDate`, which protected the wrong scenario:
 * a credential stored a year ago whose resource vanished a minute ago became deletable immediately.
 * The owner ruled on 2026-08-16 that the clock must start at orphaning, and this column is where that
 * moment lives.
 *
 * Purely additive - one `ALTER TABLE ADD COLUMN`, no table copied, no row rewritten, so nothing can be
 * lost here. Existing rows get NULL, which `UnusedCredentialPolicy` treats as "never eligible"; the
 * background worker stamps them on its first run after the update, so no backfill statement is needed
 * and none is written: a value derived here from `createdDate` would reintroduce the very defect this
 * migration exists to remove.
 *
 * The statement mirrors what Room generates for the nullable `Long?` field character for character -
 * `runMigrationsAndValidate` compares the migrated schema against the exported 51.json and fails on any
 * divergence, which is why it is written out by hand rather than trusted to match.
 */
private const val SCHEMA_VERSION_FROM = 50
private const val SCHEMA_VERSION_TO = 51

private const val ADD_ORPHANED_SINCE_COLUMN =
    "ALTER TABLE `network_credentials` ADD COLUMN `orphaned_since` INTEGER DEFAULT NULL"

val MIGRATION_50_51 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(ADD_ORPHANED_SINCE_COLUMN)
    }
}
