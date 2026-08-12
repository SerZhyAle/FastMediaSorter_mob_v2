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
 * S1401: instrumented migration test for the two new cache tables (schema 45 -> 46). Follows the
 * going-forward convention noted in [AppDatabaseSchemaExportTest]: each version bump adds an
 * (N-1)->N migration test. Validates that MIGRATION_45_46 produces a schema matching the exported
 * 46.json, and - the point of the test - that a launcher row written at v45 is untouched, since this
 * migration only creates tables and must not disturb anything that already exists.
 * Instrumented (device-gated); the JVM schema-export guard covers version/export consistency in CI.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration45To46Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate45To46_addsEmptyCacheTablesAndKeepsExistingRows() {
        helper.createDatabase(TEST_DB, 45).use { old ->
            old.execSQL(
                "INSERT INTO launcher_pins (position, target) VALUES (0, 'app:com.example.sample')"
            )
        }

        // runMigrationsAndValidate validates the migrated schema against the exported 46.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 46, true, MIGRATION_45_46)

        db.query("SELECT target FROM launcher_pins").use { cursor ->
            assertEquals("the pre-migration pin must survive", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("app:com.example.sample", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM installed_app_cache").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("the app cache starts empty and is filled in the background", 0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM launcher_launch_stats").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("launch statistics start empty", 0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-45-to-46"
    }
}
