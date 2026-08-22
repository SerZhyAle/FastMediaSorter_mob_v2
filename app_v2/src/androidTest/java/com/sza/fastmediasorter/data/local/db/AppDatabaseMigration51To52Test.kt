package com.sza.fastmediasorter.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1832: instrumented migration test for the channel-identity hop (schema 51 -> 52).
 *
 * The existing migration tests prove that a row survives untouched, which is the wrong promise here: a
 * migration that dropped every pin on the floor would still pass that. What this hop must prove is that
 * user-authored state ARRIVES in the new table carrying its original values, because §11 criterion 5
 * requires the upgrade to be safe on a device that already has pins on it.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration51To52Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate51To52_derivesIdentityAndCarriesPinsAndOutcomesAcross() {
        helper.createDatabase(TEST_DB, 51).use { old ->
            old.execSQL(insertSource(PINNED_ID, PINNED_URL, pinned = 1, sortIndex = PINNED_SORT_INDEX))
            old.execSQL(insertSource(PLAYED_ID, PLAYED_URL, pinned = 0, sortIndex = 0))
            old.execSQL(insertSource(PLAIN_ID, PLAIN_URL, pinned = 0, sortIndex = 0))
            old.execSQL(insertSource(HTTP_ID, HTTP_URL, pinned = 0, sortIndex = 0))
            old.execSQL(insertSource(HTTPS_ID, HTTPS_URL, pinned = 0, sortIndex = 0))
            old.execSQL(
                "INSERT INTO stream_play_outcome (streamId, outcome, recordedAt) " +
                    "VALUES ('$PLAYED_ID', '$OUTCOME', $OUTCOME_AT)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 52, true, MIGRATION_51_52)

        db.query("SELECT id, url, identityKey FROM stream_sources").use { cursor ->
            assertEquals("every seeded row must survive", EXPECTED_ROWS, cursor.count)
            while (cursor.moveToNext()) {
                assertEquals(
                    "identity must be derived from the row's own url",
                    StreamChannelIdentity.of(cursor.getString(1)),
                    cursor.getString(2)
                )
            }
        }

        // The http and https rows are the same channel published twice. They must fold onto one identity
        // and yet both remain in the catalog - a unique index here would have deleted one of them.
        assertEquals(identityOf(db, HTTP_ID), identityOf(db, HTTPS_ID))
        assertNotEquals(identityOf(db, HTTP_ID), identityOf(db, PLAIN_ID))

        db.query(
            "SELECT pinned, sortIndex FROM stream_user_state WHERE identityKey = ?",
            arrayOf(StreamChannelIdentity.of(PINNED_URL))
        ).use { cursor ->
            assertTrue("the pin must arrive in the new table", cursor.moveToFirst())
            assertEquals("pinned flag preserved", 1, cursor.getInt(0))
            assertEquals("pin position preserved", PINNED_SORT_INDEX, cursor.getInt(1))
        }

        db.query(
            "SELECT playOutcome, outcomeAt, pinned FROM stream_user_state WHERE identityKey = ?",
            arrayOf(StreamChannelIdentity.of(PLAYED_URL))
        ).use { cursor ->
            assertTrue("the play outcome must arrive in the new table", cursor.moveToFirst())
            assertEquals(OUTCOME, cursor.getString(0))
            assertEquals("the moment of the outcome is preserved", OUTCOME_AT, cursor.getLong(1))
            assertEquals("an outcome must not invent a pin", 0, cursor.getInt(2))
        }

        db.query(
            "SELECT COUNT(*) FROM stream_user_state WHERE identityKey = ?",
            arrayOf(StreamChannelIdentity.of(PLAIN_URL))
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("a channel the user never touched gets no state row", 0, cursor.getInt(0))
        }
    }

    private fun identityOf(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String): String =
        db.query("SELECT identityKey FROM stream_sources WHERE id = ?", arrayOf(id)).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    private fun insertSource(id: String, url: String, pinned: Int, sortIndex: Int): String =
        "INSERT INTO stream_sources " +
            "(id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt) " +
            "VALUES ('$id', '$url', 'title-$id', 'AUDIO', 'CATALOG', $sortIndex, $pinned, 1)"

    private companion object {
        const val TEST_DB = "migration-test-51-to-52"

        const val PINNED_ID = "pinned-id"
        const val PLAYED_ID = "played-id"
        const val PLAIN_ID = "plain-id"
        const val HTTP_ID = "http-id"
        const val HTTPS_ID = "https-id"

        const val PINNED_URL = "https://example.org/pinned.mp3"
        const val PLAYED_URL = "https://example.org/played.mp3"
        const val PLAIN_URL = "https://example.org/plain.mp3"
        const val HTTP_URL = "http://example.org/shared.mp3"
        const val HTTPS_URL = "https://example.org/shared.mp3"

        const val PINNED_SORT_INDEX = -7
        const val OUTCOME = "OK"
        const val OUTCOME_AT = 1_700_000_000_000L
        const val EXPECTED_ROWS = 5
    }
}
