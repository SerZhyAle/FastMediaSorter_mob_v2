package com.sza.fastmediasorter.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S4248: `scheduled_operations` is rebuilt with a nullable source and three watch-message columns
 * (schema 62 -> 63). A schedule saved at 62 must keep its id, its resources and its slot.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration62To63Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate62To63_keepsScheduleAndLeavesWatchFieldsNull() {
        helper.createDatabase(TEST_DB, 62).use { old ->
            // The resource row is not what this hop is about; without the pragma it would need every
            // NOT NULL column of `resources` spelled out here.
            old.execSQL("PRAGMA foreign_keys = OFF")
            old.execSQL(
                """
                INSERT INTO scheduled_operations (
                    id, is_enabled, source_resource_id, operation_type, target_resource_id, file_type_mask,
                    time_filter, start_time_hour, start_time_minute, interval_hours, interval_minutes,
                    overwrite, silent_mode, next_run_at
                ) VALUES (
                    $OPERATION_ID, 1, $RESOURCE_ID, 'DELETE', NULL, 1, 'ALL', 3, 0, 24, 0, 0, 0, $NEXT_RUN_AT
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 63, true, MIGRATION_62_63)

        db.query(
            "SELECT source_resource_id, next_run_at, watch_title FROM scheduled_operations WHERE id = $OPERATION_ID"
        ).use { cursor ->
            assertTrue("the pre-migration schedule must survive under its id", cursor.moveToFirst())
            assertEquals(RESOURCE_ID, cursor.getLong(0))
            assertEquals(NEXT_RUN_AT, cursor.getLong(1))
            assertNull("an existing schedule carries no watch message", cursor.getString(2))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-62-to-63"
        const val RESOURCE_ID = 7L
        const val OPERATION_ID = 11L
        const val NEXT_RUN_AT = 1_800_000_000_000L
    }
}
