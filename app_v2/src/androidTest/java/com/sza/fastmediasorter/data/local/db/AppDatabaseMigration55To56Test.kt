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
class AppDatabaseMigration55To56Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate55To56_marksExistingLauncherCellsAsUserManaged() {
        helper.createDatabase(TEST_DB, 55).use { old ->
            old.execSQL(insertCell(CELL_ID, APP_TARGET))
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 56, true, MIGRATION_55_56)

        db.query(
            "SELECT target, origin FROM launcher_cells WHERE id = ?",
            arrayOf<Any>(CELL_ID),
        ).use { cursor ->
            assertTrue("a cell placed before the migration must survive it", cursor.moveToFirst())
            assertEquals(APP_TARGET, cursor.getString(0))
            assertEquals("USER", cursor.getString(1))
        }
    }

    private fun insertCell(id: Long, target: String): String =
        "INSERT INTO launcher_cells " +
            "(id, orientation, rowIndex, colIndex, spanW, spanH, kind, target, labelOverride, addedAt, screenIndex) " +
            "VALUES ($id, 'PORTRAIT', 0, 0, 1, 1, 'SHORTCUT', '$target', NULL, 1, 0)"

    private companion object {
        const val TEST_DB = "migration-test-55-to-56"
        const val APP_TARGET = "app:com.example.other"
        const val CELL_ID = 1L
    }
}
