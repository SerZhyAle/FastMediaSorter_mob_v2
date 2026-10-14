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
 * S4156: the exchange binding columns of `resources` (schema 60 -> 61). A resource written at 60 must survive
 * with its data and stay unbound, so nothing that predates the exchange directory looks attached through it.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration60To61Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate60To61_keepsResourceAndLeavesExchangeBindingNull() {
        // Every NOT NULL column without a schema-level DEFAULT is supplied: Room keeps Kotlin defaults in the
        // entity, not in the table.
        helper.createDatabase(TEST_DB, 60).use { old ->
            old.execSQL(
                """
                INSERT INTO resources (
                    name, path, type, supportedMediaTypesFlags, sortMode, displayMode,
                    lastScrollPosition, fileCount, subfolderCount, lastAccessedDate, slideshowInterval,
                    isDestination, destinationOrder, destinationColor, isWritable, isReadOnly,
                    isAvailable, createdDate, scanSubdirectories, disableThumbnails, allFiles,
                    showHiddenFiles, showSubfoldersAsItems, displayOrder, rememberFileList
                ) VALUES (
                    'Share', 'sftp://192.0.2.10:2222/', 'SFTP', 15, 'NAME_ASC', 'LIST',
                    0, 0, 0, 1000, 10,
                    0, -1, 0, 0, 0,
                    1, 1000, 0, 0, 0,
                    0, 0, 0, 0
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 61, true, MIGRATION_60_61)

        db.query("SELECT name, path, exchange_account, exchange_resource_id FROM resources").use { cursor ->
            assertEquals("the pre-migration row must survive", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("Share", cursor.getString(0))
            assertEquals("sftp://192.0.2.10:2222/", cursor.getString(1))
            assertNull("an existing resource must stay unbound to any entry", cursor.getString(2))
            assertNull("an existing resource must carry no exchange resourceId", cursor.getString(3))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-60-to-61"
    }
}
