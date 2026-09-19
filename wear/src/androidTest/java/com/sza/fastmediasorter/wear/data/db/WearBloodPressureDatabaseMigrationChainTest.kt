package com.sza.fastmediasorter.wear.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S3113: the watch blood-pressure upgrade path walked in one run, from the oldest exported schema to the
 * version the app declares, over a diary row that existed before any of it.
 *
 * The chain-test dimension of assert-migration-schema-conformance.ps1 keeps CURRENT_SCHEMA in step with
 * the database, so a chain frozen at an older target cannot keep passing while it stops covering the
 * newest hop.
 */
@RunWith(AndroidJUnit4::class)
class WearBloodPressureDatabaseMigrationChainTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WearBloodPressureDatabase::class.java
    )

    @Test
    fun everyMigrationFromTheOldestExportedSchemaRunsAndKeepsTheDiaryRowItStartedWith() {
        helper.createDatabase(TEST_DB, OLDEST_EXPORTED_SCHEMA).use { old ->
            old.execSQL(
                "INSERT INTO blood_pressure_history (id, systolic, diastolic, timestampMillis) " +
                    "VALUES ($ROW_ID, $SYSTOLIC, $DIASTOLIC, 1000)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            CURRENT_SCHEMA,
            true,
            WearBloodPressureMigrations.MIGRATION_1_2,
            WearBloodPressureMigrations.MIGRATION_2_3
        )

        db.query("SELECT systolic, diastolic FROM blood_pressure_history WHERE id = ?", arrayOf<Any>(ROW_ID))
            .use { cursor ->
                assertTrue("a diary row written before the chain must survive all of it", cursor.moveToFirst())
                assertEquals(SYSTOLIC, cursor.getInt(0))
                assertEquals(DIASTOLIC, cursor.getInt(1))
            }
    }

    private companion object {
        const val TEST_DB = "wear-blood-pressure-migration-chain"

        // The oldest schema this module exports. A watch older than this cannot be validated against
        // anything, so the chain starts where the evidence starts.
        const val OLDEST_EXPORTED_SCHEMA = 1

        // The version WearBloodPressureDatabase declares.
        const val CURRENT_SCHEMA = 3

        const val ROW_ID = 1L
        const val SYSTOLIC = 150
        const val DIASTOLIC = 98
    }
}
