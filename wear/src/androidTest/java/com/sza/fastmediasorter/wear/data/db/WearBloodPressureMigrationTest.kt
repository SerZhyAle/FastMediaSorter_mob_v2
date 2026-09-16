package com.sza.fastmediasorter.wear.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearBloodPressureMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WearBloodPressureDatabase::class.java
    )

    @Test
    fun migrate1To2_keepsDiaryRowsAsManualAndAddsCalibrationTable() {
        helper.createDatabase(TEST_DB, 1).use { old ->
            old.execSQL(
                "INSERT INTO blood_pressure_history (id, systolic, diastolic, timestampMillis) " +
                    "VALUES ($ROW_ID, $SYSTOLIC, $DIASTOLIC, 1000)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, WearBloodPressureMigrations.MIGRATION_1_2)

        db.query("SELECT systolic, diastolic, source FROM blood_pressure_history WHERE id = ?", arrayOf<Any>(ROW_ID))
            .use { cursor ->
                assertTrue("a diary row written before the migration must survive it", cursor.moveToFirst())
                assertEquals(SYSTOLIC, cursor.getInt(0))
                assertEquals(DIASTOLIC, cursor.getInt(1))
                assertEquals("MANUAL", cursor.getString(2))
            }
        db.query("SELECT COUNT(*) FROM blood_pressure_calibration").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate2To3_addsAnEmptyPulseToEveryRow() {
        helper.createDatabase(TEST_DB_2_3, 2).use { old ->
            old.execSQL(
                "INSERT INTO blood_pressure_history (id, systolic, diastolic, timestampMillis, source) " +
                    "VALUES ($ROW_ID, $SYSTOLIC, $DIASTOLIC, 1000, 'CALIBRATION')"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_2_3, 3, true, WearBloodPressureMigrations.MIGRATION_2_3)

        db.query("SELECT source, pulse FROM blood_pressure_history WHERE id = ?", arrayOf<Any>(ROW_ID)).use { cursor ->
            assertTrue("a row written by version 2 must survive version 3", cursor.moveToFirst())
            assertEquals("CALIBRATION", cursor.getString(0))
            assertTrue("a row that predates pulse has none", cursor.isNull(1))
        }
    }

    private companion object {
        const val TEST_DB_2_3 = "wear-blood-pressure-migration-2-to-3"
        const val TEST_DB = "wear-blood-pressure-migration-1-to-2"
        const val ROW_ID = 1L
        const val SYSTOLIC = 150
        const val DIASTOLIC = 98
    }
}
