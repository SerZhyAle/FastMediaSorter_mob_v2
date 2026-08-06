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
 * S1179: instrumented migration test for `sensor_series_point` (schema 46 -> 47). Validates that
 * MIGRATION_46_47 produces a schema matching the exported 47.json, and - the point of the test -
 * that a launcher row written at v46 is untouched, since this migration only creates a table and
 * must not disturb anything that already exists.
 * Instrumented (device-gated); the JVM schema-export guard covers version/export consistency in CI.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration46To47Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate46To47_addsEmptySeriesTableAndKeepsExistingRows() {
        helper.createDatabase(TEST_DB, 46).use { old ->
            old.execSQL(
                "INSERT INTO launcher_pins (position, target) VALUES (0, 'app:com.example.sample')"
            )
        }

        // runMigrationsAndValidate validates the migrated schema against the exported 47.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 47, true, MIGRATION_46_47)

        db.query("SELECT target FROM launcher_pins").use { cursor ->
            assertEquals("the pre-migration pin must survive", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("app:com.example.sample", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM sensor_series_point").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("a migrated series starts empty", 0, cursor.getInt(0))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = " +
                "'index_sensor_series_point_seriesId_takenAtMillis'"
        ).use { cursor ->
            assertEquals("the series lookup index must exist after the migration", 1, cursor.count)
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-46-to-47"
    }
}
