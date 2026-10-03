package com.sza.fastmediasorter.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration59To60Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate59To60_keepsOperationAndLeavesConditionsUnset() {
        helper.createDatabase(TEST_DB, 59).use { old ->
            // The row is about the new columns, not about its resource, so the FK is not exercised.
            old.execSQL("PRAGMA foreign_keys = OFF")
            old.execSQL(
                "INSERT INTO scheduled_operations (id, is_enabled, source_resource_id, operation_type, " +
                    "target_resource_id, file_type_mask, time_filter, start_time_hour, start_time_minute, " +
                    "interval_hours, interval_minutes, overwrite, silent_mode, last_run_at, last_run_status) " +
                    "VALUES ($OP_ID, 1, $SOURCE_ID, 'COPY', NULL, 1, 'ALL', 3, 30, 24, 0, 0, 0, " +
                    "$LAST_RUN_AT, 'OK')"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 60, true, MIGRATION_59_60)

        db.query(
            "SELECT last_run_at, file_name_mask, min_age_hours, max_age_hours, min_size_bytes, " +
                "max_size_bytes, last_success_at FROM scheduled_operations WHERE id = ?",
            arrayOf<Any>(OP_ID)
        ).use { cursor ->
            assertTrue("an operation saved before the upgrade must survive it", cursor.moveToFirst())
            assertEquals("existing run state must be untouched", LAST_RUN_AT, cursor.getLong(0))
            for (column in 1 until cursor.columnCount) {
                assertTrue("new column ${cursor.getColumnName(column)} must start unset", cursor.isNull(column))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test-59-to-60"
        const val OP_ID = 7L
        const val SOURCE_ID = 3L
        const val LAST_RUN_AT = 1_700_000_000_000L
    }
}
