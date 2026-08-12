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
 * S1378: instrumented migration test for the additive nullable `storage_volume_id` column
 * (schema 44 -> 45). Follows the going-forward convention noted in [AppDatabaseSchemaExportTest]:
 * each version bump adds an (N-1)->N migration test. Validates that MIGRATION_44_45 produces a
 * schema matching the exported 45.json, and - the point of the test - that a resource row written
 * at v44 survives the migration with its data intact and the new column NULL.
 * Instrumented (device-gated); the JVM schema-export guard covers version/export consistency in CI.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration44To45Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate44To45_keepsExistingRowAndLeavesVolumeBindingNull() {
        // Every NOT NULL column without a schema-level DEFAULT has to be supplied explicitly: Room
        // puts Kotlin property defaults in the entity, not in the table, so a short INSERT would
        // fail on the constraint rather than on the migration under test.
        helper.createDatabase(TEST_DB, 44).use { old ->
            old.execSQL(
                """
                INSERT INTO resources (
                    name, path, type, supportedMediaTypesFlags, sortMode, displayMode,
                    lastScrollPosition, fileCount, subfolderCount, lastAccessedDate, slideshowInterval,
                    isDestination, destinationOrder, destinationColor, isWritable, isReadOnly,
                    isAvailable, createdDate, scanSubdirectories, disableThumbnails, allFiles,
                    showHiddenFiles, showSubfoldersAsItems, displayOrder, rememberFileList
                ) VALUES (
                    'Pictures', '/storage/emulated/0/Pictures', 'LOCAL', 15, 'NAME_ASC', 'LIST',
                    0, 0, 0, 1000, 10,
                    0, -1, 0, 0, 0,
                    1, 1000, 0, 0, 0,
                    0, 0, 0, 0
                )
                """.trimIndent()
            )
        }

        // runMigrationsAndValidate validates the migrated schema against the exported 45.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 45, true, MIGRATION_44_45)

        db.query("SELECT name, path, storage_volume_id FROM resources").use { cursor ->
            assertEquals("the pre-migration row must survive", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("Pictures", cursor.getString(0))
            assertEquals("/storage/emulated/0/Pictures", cursor.getString(1))
            assertNull("an existing row must stay unbound to any volume", cursor.getString(2))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-44-to-45"
    }
}
