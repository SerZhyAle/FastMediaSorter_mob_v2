package com.sza.fastmediasorter.wear.data.db

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
 * S2161: instrumented migration test for watch voice notes (schema 1 -> 2).
 *
 * Validates that adding publishedAddress matches the entity and exported schema,
 * and preserves existing recorded voice notes.
 */
@RunWith(AndroidJUnit4::class)
class WearVoiceNoteMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WearVoiceNoteDatabase::class.java
    )

    @Test
    fun migrate1To2_addsPublishedAddressAndKeepsExistingNotes() {
        helper.createDatabase(TEST_DB, 1).use { old ->
            old.execSQL(
                "INSERT INTO voice_notes " +
                    "(id, fileName, absolutePath, createdAtMillis, durationMillis, sizeBytes, deliveryState) " +
                    "VALUES ($NOTE_ID, '$FILE_NAME', '$PATH', 1000, 5000, 1024, 'LOCAL_ONLY')"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            WearVoiceNoteMigrations.MIGRATION_1_2
        )

        db.query(
            "SELECT id, fileName, absolutePath, publishedAddress FROM voice_notes WHERE id = ?",
            arrayOf<Any>(NOTE_ID)
        ).use { cursor ->
            assertTrue("a note recorded before the migration must survive it", cursor.moveToFirst())
            assertEquals(NOTE_ID, cursor.getLong(0))
            assertEquals(FILE_NAME, cursor.getString(1))
            assertEquals(PATH, cursor.getString(2))
            assertNull("new column must be null for pre-migration records", cursor.getString(3))
        }
    }

    private companion object {
        const val TEST_DB = "wear-voice-notes-migration-1-to-2"
        const val NOTE_ID = 1L
        const val FILE_NAME = "audio_260902_120000.m4a"
        const val PATH = "/data/user/0/com.sza.fastmediasorter/files/voice_notes/audio_260902_120000.m4a"
    }
}
