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
 * S2251: instrumented migration test for the multi-screen hop (schema 53 -> 54).
 *
 * The point of this test is the `validate` half, not the data half. The migration originally added the
 * column as `screen_index` while the entity declares `screenIndex`, so Room rejected the resulting
 * schema on every upgrading install and the destructive fallback wiped the user's resources, network
 * credentials and desktop - measured on a real phone on 2026-09-01. `runMigrationsAndValidate` compares
 * the migrated table against the exported schema, which is exactly the comparison that failed on the
 * device, so a name or default that drifts again fails here instead of on someone's phone.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration53To54Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate53To54_addsScreenIndexMatchingTheEntityAndKeepsExistingCells() {
        helper.createDatabase(TEST_DB, 53).use { old ->
            old.execSQL(insertCell(CELL_PLACED, APP_TARGET))
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 54, true, MIGRATION_53_54)

        db.query(
            "SELECT screenIndex, target FROM launcher_cells WHERE id = ?",
            arrayOf<Any>(CELL_PLACED)
        ).use { cursor ->
            assertTrue("a cell placed before the migration must survive it", cursor.moveToFirst())
            assertEquals(
                "an existing cell belongs to the first desktop screen, which is what the default says",
                0,
                cursor.getInt(0)
            )
            assertEquals("the migration must not touch what the cell points at", APP_TARGET, cursor.getString(1))
        }
    }

    private fun insertCell(id: Long, target: String): String =
        "INSERT INTO launcher_cells " +
            "(id, orientation, rowIndex, colIndex, spanW, spanH, kind, target, labelOverride, addedAt) " +
            "VALUES ($id, 'PORTRAIT', 0, 0, 1, 1, 'SHORTCUT', '$target', NULL, 1)"

    private companion object {
        const val TEST_DB = "migration-test-53-to-54"
        const val APP_TARGET = "app:com.example.other"
        const val CELL_PLACED = 1L
    }
}
