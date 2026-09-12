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
 * S2355: the watch database at its shipped version, opened the way an update opens it.
 *
 * The watch has no migration yet and this test deliberately writes none. What it proves is the
 * chain that a future migration test depends on and that nothing else exercises: the instrumented
 * set compiles, the exported schemas reached the device as androidTest assets, the runner found
 * this package, and the watch returned a verdict. A connected run with nothing to execute reports
 * success having observed nothing - the exact failure mode this ticket exists to remove (ADR-5).
 *
 * [MigrationTestHelper.runMigrationsAndValidate] is what makes it a schema test rather than a
 * smoke test: it compares the open database against the exported 1.json, which is the same
 * comparison Room performs on a user's watch during an upgrade.
 */
@RunWith(AndroidJUnit4::class)
class WearVoiceNoteDatabaseSchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WearVoiceNoteDatabase::class.java
    )

    @Test
    fun version1_openedFromExportedSchema_keepsItsRows() {
        helper.createDatabase(TEST_DB, DATABASE_VERSION).use { db ->
            db.execSQL(
                "INSERT INTO voice_notes " +
                    "(fileName, absolutePath, createdAtMillis, durationMillis, sizeBytes, deliveryState) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    SAMPLE_FILE_NAME,
                    SAMPLE_PATH,
                    SAMPLE_CREATED_AT,
                    SAMPLE_DURATION,
                    SAMPLE_SIZE,
                    SAMPLE_DELIVERY_STATE
                )
            )
        }

        // No migration is supplied: at the shipped version this validates the open database
        // against the exported 1.json, which is what proves the schema assets are mounted.
        val reopened = helper.runMigrationsAndValidate(TEST_DB, DATABASE_VERSION, true)

        var rowFound = false
        reopened.query("SELECT fileName, deliveryState FROM voice_notes").use { cursor ->
            while (cursor.moveToNext()) {
                rowFound = true
                assertEquals(SAMPLE_FILE_NAME, cursor.getString(0))
                assertEquals(SAMPLE_DELIVERY_STATE, cursor.getString(1))
            }
        }
        reopened.close()

        assertTrue("the row written at version $DATABASE_VERSION must survive reopening", rowFound)
    }

    private companion object {
        const val TEST_DB = "wear-voice-notes-schema-test"
        const val DATABASE_VERSION = 1
        const val SAMPLE_FILE_NAME = "note-0001.m4a"
        const val SAMPLE_PATH = "/data/voice/note-0001.m4a"
        const val SAMPLE_CREATED_AT = 1_700_000_000_000L
        const val SAMPLE_DURATION = 4_200L
        const val SAMPLE_SIZE = 65_536L
        const val SAMPLE_DELIVERY_STATE = "PENDING"
    }
}
