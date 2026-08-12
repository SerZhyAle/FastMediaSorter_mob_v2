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
 * S1502: instrumented migration test for the play-outcome split (schema 47 -> 48). MIGRATION_47_48
 * recreates `stream_sources` without its two outcome columns, which is the destructive shape - a
 * mistake in it does not fail loudly, it silently drops user rows or the url uniqueness that keeps
 * a re-import from duplicating the catalog. So this asserts all four: every channel survives with
 * its remaining fields, a recorded outcome lands in the new table, a channel that was never tried
 * gets no row there, and the unique index is back after the rename.
 * Instrumented (device-gated); the JVM schema-export guard covers version/export consistency in CI.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration47To48Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate47To48_movesRecordedOutcomesAndKeepsEveryChannel() {
        helper.createDatabase(TEST_DB, 47).use { old ->
            old.execSQL(
                "INSERT INTO stream_sources (id, url, title, mediaKind, sourceOrigin, sortIndex, " +
                    "pinned, addedAt, lastPlayOutcome, lastPlayOutcomeAt) VALUES " +
                    "('played', 'http://example.invalid/a', 'Played', 'VIDEO', 'CATALOG', 3, 1, 10, 'OK', 4242)"
            )
            old.execSQL(
                "INSERT INTO stream_sources (id, url, title, mediaKind, sourceOrigin, sortIndex, " +
                    "pinned, addedAt, lastPlayOutcome, lastPlayOutcomeAt) VALUES " +
                    "('nulltime', 'http://example.invalid/b', 'NoTimestamp', 'AUDIO', 'MANUAL', 1, 0, 20, 'FAIL', NULL)"
            )
            old.execSQL(
                "INSERT INTO stream_sources (id, url, title, mediaKind, sourceOrigin, sortIndex, " +
                    "pinned, addedAt) VALUES " +
                    "('untried', 'http://example.invalid/c', 'NeverTried', 'VIDEO', 'MANUAL', 2, 0, 30)"
            )
        }

        // runMigrationsAndValidate validates the migrated schema against the exported 48.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 48, true, MIGRATION_47_48)

        db.query("SELECT id, title, sortIndex, pinned FROM stream_sources ORDER BY id").use { cursor ->
            assertEquals("every channel must survive the table recreate", 3, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("nulltime", cursor.getString(0))
            assertEquals("NoTimestamp", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals("played", cursor.getString(0))
            assertEquals("the remaining columns must copy across untouched", 3, cursor.getInt(2))
            assertEquals("a pinned channel must stay pinned", 1, cursor.getInt(3))
        }
        db.query("SELECT streamId, outcome, recordedAt FROM stream_play_outcome ORDER BY streamId").use { cursor ->
            assertEquals("only the two channels with a recorded outcome move over", 2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("nulltime", cursor.getString(0))
            assertEquals("FAIL", cursor.getString(1))
            assertEquals("a null timestamp becomes 0, since the new column is NOT NULL", 0L, cursor.getLong(2))
            assertTrue(cursor.moveToNext())
            assertEquals("played", cursor.getString(0))
            assertEquals("OK", cursor.getString(1))
            assertEquals(4242L, cursor.getLong(2))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_stream_sources_url'"
        ).use { cursor ->
            assertEquals("DROP TABLE takes the url index with it - the migration must put it back", 1, cursor.count)
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-47-to-48"
    }
}
