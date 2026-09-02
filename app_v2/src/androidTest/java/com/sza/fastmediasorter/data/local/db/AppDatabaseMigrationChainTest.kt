package com.sza.fastmediasorter.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S2306: the whole upgrade path in one run - schema 36 (the oldest exported one) to the current
 * version, over rows that existed before any of it.
 *
 * The per-hop tests beside this one each prove a single migration. Nothing proved the sequence, which
 * is the path a user who has not updated in a year actually takes: eighteen migrations against tables
 * that already hold data, where a statement SQLite refuses only on a non-empty table - `ADD COLUMN ..
 * NOT NULL` with no default - passes every empty-database check and fails on the phone. `MIGRATION_52_53`
 * had exactly that defect and it surfaced only because a unit test seeded a row.
 *
 * `runMigrationsAndValidate` ends the run with the same comparison Room performs on the device during
 * the first launch after an update - the comparison that failed on 2026-09-01 and deleted the owner's
 * database (S2251). Two seeded rows carry through the whole chain so the run answers both questions:
 * every migration executes, and the data is still there afterwards.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationChainTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun everyMigrationFromTheOldestExportedSchemaRunsAndKeepsTheRowsItStartedWith() {
        helper.createDatabase(TEST_DB, OLDEST_EXPORTED_SCHEMA).use { old ->
            old.execSQL(
                "INSERT INTO favorites (id, uri, resourceId, displayName, mediaType, size, " +
                    "lastKnownPath, dateModified, addedTimestamp) VALUES " +
                    "($FAVORITE_ID, '$FAVORITE_URI', 1, 'kept', 0, 128, '$FAVORITE_URI', 1, 1)"
            )
            old.execSQL(
                "INSERT INTO playback_positions (filePath, position, duration, lastPlayedAt, " +
                    "isCompleted) VALUES ('$PLAYBACK_PATH', $PLAYBACK_POSITION, 2000, 1, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            CURRENT_SCHEMA,
            true,
            MIGRATION_36_37,
            MIGRATION_37_38,
            MIGRATION_38_39,
            MIGRATION_39_40,
            MIGRATION_40_41,
            MIGRATION_41_42,
            MIGRATION_42_43,
            MIGRATION_43_44,
            MIGRATION_44_45,
            MIGRATION_45_46,
            MIGRATION_46_47,
            MIGRATION_47_48,
            MIGRATION_48_49,
            MIGRATION_49_50,
            MIGRATION_50_51,
            MIGRATION_51_52,
            MIGRATION_52_53,
            MIGRATION_53_54
        )

        db.query("SELECT uri FROM favorites WHERE id = ?", arrayOf<Any>(FAVORITE_ID)).use { cursor ->
            assertTrue("a favourite saved before the chain must survive all of it", cursor.moveToFirst())
            assertEquals(FAVORITE_URI, cursor.getString(0))
        }
        db.query(
            "SELECT position FROM playback_positions WHERE filePath = ?",
            arrayOf<Any>(PLAYBACK_PATH)
        ).use { cursor ->
            assertTrue("a saved playback position must survive the whole chain", cursor.moveToFirst())
            assertEquals(PLAYBACK_POSITION, cursor.getLong(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test-chain"

        // The oldest schema the repository exports. A device older than this cannot be validated
        // against anything, so the chain starts where the evidence starts.
        const val OLDEST_EXPORTED_SCHEMA = 36

        // The version AppDatabase declares. Kept in step with it by the chain-test dimension of
        // assert-migration-schema-conformance.ps1 - a chain test frozen at an older target stops
        // covering the newest hop while still passing, which is the failure this file exists to end.
        const val CURRENT_SCHEMA = 54

        const val FAVORITE_ID = 1L
        const val FAVORITE_URI = "content://chain/kept.mp4"
        const val PLAYBACK_PATH = "/chain/kept.mp4"
        const val PLAYBACK_POSITION = 4200L
    }
}
