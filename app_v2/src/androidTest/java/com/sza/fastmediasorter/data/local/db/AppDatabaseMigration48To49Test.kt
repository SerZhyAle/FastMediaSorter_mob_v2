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
 * S1433: instrumented migration test for the measurement-history table (schema 48 -> 49).
 *
 * The hop is purely additive, which is the shape that looks too simple to test and is not: the migration
 * writes its `CREATE TABLE` by hand, so any divergence from what Room generates for
 * `NetworkMeasurementEntity` - a column order, an affinity, the index name - surfaces as a crash on the
 * first launch after upgrade rather than at build time. `runMigrationsAndValidate` compares the migrated
 * schema against the exported 49.json and is what catches that.
 *
 * The pre-existing row proves the second half of the promise: an additive migration must leave every other
 * table exactly as it found it.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration48To49Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate48To49_addsEmptyHistoryTableAndLeavesExistingRowsAlone() {
        helper.createDatabase(TEST_DB, 48).use { old ->
            old.execSQL(
                "INSERT INTO stream_play_outcome (streamId, outcome, recordedAt) VALUES ('kept', 'OK', 7)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 49, true, MIGRATION_48_49)

        db.query("SELECT COUNT(*) FROM network_measurement").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("the history table must arrive empty", 0, cursor.getInt(0))
        }
        db.query("SELECT streamId, outcome, recordedAt FROM stream_play_outcome").use { cursor ->
            assertEquals("an additive migration must not touch an existing table", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("kept", cursor.getString(0))
            assertEquals("OK", cursor.getString(1))
            assertEquals(7L, cursor.getLong(2))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_network_measurement_takenAtMillis'"
        ).use { cursor ->
            assertEquals("the migration must create the index Room expects, not only the table", 1, cursor.count)
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-48-to-49"
    }
}
