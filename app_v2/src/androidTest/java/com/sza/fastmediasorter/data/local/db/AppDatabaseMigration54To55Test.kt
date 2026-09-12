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
 * S1905: instrumented migration test for the per-cell gadget config hop (schema 54 -> 55).
 *
 * The `validate` half is the point, exactly as in the 53 -> 54 sibling. This migration originally ran
 * `CREATE INDEX index_launcher_cell_config_cellId` while [LauncherCellConfigEntity] declares no index,
 * so Room compared the migrated table against the entity, found one index where it expected none, and
 * the recovery path deleted the database on the first launch after the update - measured on a real
 * phone on 2026-09-05, the second wipe of this class in five days. `runMigrationsAndValidate` performs
 * that same comparison here, so the next disagreement fails in CI instead of on someone's phone.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration54To55Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate54To55_addsConfigTableMatchingTheEntityAndKeepsExistingCells() {
        helper.createDatabase(TEST_DB, 54).use { old ->
            old.execSQL(insertCell(CELL_PLACED, APP_TARGET))
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 55, true, MIGRATION_54_55)

        db.query(
            "SELECT target FROM launcher_cells WHERE id = ?",
            arrayOf<Any>(CELL_PLACED)
        ).use { cursor ->
            assertTrue("a cell placed before the migration must survive it", cursor.moveToFirst())
            assertEquals("the migration must not touch what the cell points at", APP_TARGET, cursor.getString(0))
        }

        // The new table has to be writable against the live cell, not merely present: the foreign key
        // is what a per-cell config is for, and a CASCADE that names a column the entity spells
        // differently would pass a "table exists" check and fail on the device.
        db.execSQL(
            "INSERT INTO launcher_cell_config (cellId, key, value) VALUES ($CELL_PLACED, '$CONFIG_KEY', '$CONFIG_VALUE')"
        )
        db.query(
            "SELECT value FROM launcher_cell_config WHERE cellId = ? AND key = ?",
            arrayOf<Any>(CELL_PLACED, CONFIG_KEY)
        ).use { cursor ->
            assertTrue("the config row written against a live cell must read back", cursor.moveToFirst())
            assertEquals(CONFIG_VALUE, cursor.getString(0))
        }
    }

    private fun insertCell(id: Long, target: String): String =
        "INSERT INTO launcher_cells " +
            "(id, orientation, rowIndex, colIndex, spanW, spanH, kind, target, labelOverride, addedAt, screenIndex) " +
            "VALUES ($id, 'PORTRAIT', 0, 0, 1, 1, 'SHORTCUT', '$target', NULL, 1, 0)"

    private companion object {
        const val TEST_DB = "migration-test-54-to-55"
        const val APP_TARGET = "app:com.example.other"
        const val CELL_PLACED = 1L
        const val CONFIG_KEY = "weather_place"
        const val CONFIG_VALUE = "Kyiv"
    }
}
