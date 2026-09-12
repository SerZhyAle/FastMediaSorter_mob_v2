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
 * S2830: the watch voice-note upgrade path walked in one run, from the oldest exported schema to the
 * version the app declares, over a row that existed before any of it.
 *
 * This file walks the chain to the declared version; the chain-test dimension of
 * assert-migration-schema-conformance.ps1 keeps CURRENT_SCHEMA in step with the database, so a chain
 * frozen at an older target cannot keep passing while it stops covering the newest hop.
 */
@RunWith(AndroidJUnit4::class)
class WearVoiceNoteDatabaseMigrationChainTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WearVoiceNoteDatabase::class.java
    )

    @Test
    fun everyMigrationFromTheOldestExportedSchemaRunsAndKeepsTheNoteItStartedWith() {
        helper.createDatabase(TEST_DB, OLDEST_EXPORTED_SCHEMA).use { old ->
            old.execSQL(
                "INSERT INTO voice_notes " +
                    "(id, fileName, absolutePath, createdAtMillis, durationMillis, sizeBytes, deliveryState) " +
                    "VALUES ($NOTE_ID, '$FILE_NAME', '$PATH', 1000, 5000, 1024, 'LOCAL_ONLY')"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            CURRENT_SCHEMA,
            true,
            WearVoiceNoteMigrations.MIGRATION_1_2
        )

        db.query(
            "SELECT fileName, absolutePath, publishedAddress FROM voice_notes WHERE id = ?",
            arrayOf<Any>(NOTE_ID)
        ).use { cursor ->
            assertTrue("a note recorded before the chain must survive all of it", cursor.moveToFirst())
            assertEquals(FILE_NAME, cursor.getString(0))
            assertEquals(PATH, cursor.getString(1))
            assertNull("a column added by the chain must be null on a row that predates it", cursor.getString(2))
        }
    }

    private companion object {
        const val TEST_DB = "wear-voice-notes-migration-chain"

        // The oldest schema this module exports. A watch older than this cannot be validated against
        // anything, so the chain starts where the evidence starts.
        const val OLDEST_EXPORTED_SCHEMA = 1

        // The version WearVoiceNoteDatabase declares.
        const val CURRENT_SCHEMA = 2

        const val NOTE_ID = 1L
        const val FILE_NAME = "audio_260902_120000.m4a"
        const val PATH = "/data/user/0/com.sza.fastmediasorter/files/voice_notes/audio_260902_120000.m4a"
    }
}
