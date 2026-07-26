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
 * S1009: instrumented migration test for the additive `is_hidden` column (schema 43 -> 44).
 * Going-forward convention noted in [AppDatabaseSchemaExportTest]: each version bump adds an
 * (N-1)->N migration test. Validates that MIGRATION_43_44 produces a schema matching the exported
 * 44.json and that `is_hidden` lands as NOT NULL DEFAULT 0 (existing rows stay visible).
 * Instrumented (device-gated); the JVM schema-export guard covers version/export consistency in CI.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration43To44Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate43To44_addsIsHiddenColumnDefaultingToZero() {
        helper.createDatabase(TEST_DB, 43).close()

        // runMigrationsAndValidate validates the migrated schema against the exported 44.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 44, true, MIGRATION_43_44)

        var isHiddenFound = false
        db.query("PRAGMA table_info(resources)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val dfltIndex = cursor.getColumnIndex("dflt_value")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "is_hidden") {
                    isHiddenFound = true
                    assertEquals("0", cursor.getString(dfltIndex))
                }
            }
        }
        db.close()

        assertTrue("is_hidden column must exist after the 43 -> 44 migration", isHiddenFound)
    }

    private companion object {
        const val TEST_DB = "migration-test-43-to-44"
    }
}
