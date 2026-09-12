package com.sza.fastmediasorter.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration56To57Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate56To57_addsCollectionTablesAndKeepsUserAuthoredRows() {
        helper.createDatabase(TEST_DB, 56).use { old ->
            old.execSQL(insertUserStream())
            old.execSQL(insertUserState())
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 57, true, MIGRATION_56_57)

        db.query("SELECT url, sourceOrigin FROM stream_sources WHERE url = ?", arrayOf<Any>(USER_URL))
            .use { cursor ->
                assertTrue("a user-authored stream must survive the migration", cursor.moveToFirst())
                assertEquals(USER_URL, cursor.getString(0))
            }
        db.query("SELECT pinned FROM stream_user_state WHERE identityKey = ?", arrayOf<Any>(IDENTITY))
            .use { cursor ->
                assertTrue("a user pin must survive the migration", cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        db.query("SELECT COUNT(*) FROM stream_collections").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("the new collection table starts empty", 0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM stream_collection_members").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("the new membership table starts empty", 0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate56To57_letsOneUrlBelongToTwoCollections() {
        helper.createDatabase(TEST_DB_MULTI, 56).close()

        val db = helper.runMigrationsAndValidate(TEST_DB_MULTI, 57, true, MIGRATION_56_57)

        db.execSQL("INSERT INTO stream_collections VALUES ('tv-ru', 10, '{\"en\":\"Russian TV\"}')")
        db.execSQL("INSERT INTO stream_collections VALUES ('news', 20, '{\"en\":\"News\"}')")
        db.execSQL("INSERT INTO stream_collection_members VALUES ('tv-ru', '$USER_URL', 1)")
        db.execSQL("INSERT INTO stream_collection_members VALUES ('news', '$USER_URL', 1)")

        db.query("SELECT COUNT(*) FROM stream_collection_members WHERE url = ?", arrayOf<Any>(USER_URL))
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("the composite key must admit one url twice", 2, cursor.getInt(0))
            }
        db.query("SELECT COUNT(*) FROM stream_collection_members WHERE collectionId = 'tv-ru'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertFalse("a collection must not swallow its own membership row", cursor.getInt(0) == 0)
            }
    }

    private fun insertUserStream(): String =
        "INSERT INTO stream_sources " +
            "(id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt, identityKey) " +
            "VALUES ('$STREAM_ID', '$USER_URL', 'Mine', 'AUDIO', 'MANUAL', 0, 1, 1, '$IDENTITY')"

    private fun insertUserState(): String =
        "INSERT INTO stream_user_state " +
            "(identityKey, pinned, sortIndex, playOutcome, outcomeAt, updatedAt) " +
            "VALUES ('$IDENTITY', 1, 0, NULL, NULL, 1)"

    private companion object {
        const val TEST_DB = "migration-test-56-to-57"
        const val TEST_DB_MULTI = "migration-test-56-to-57-multi"
        const val STREAM_ID = "11111111-1111-1111-1111-111111111111"
        const val USER_URL = "https://example.test/mine.m3u8"
        const val IDENTITY = "example.test/mine"
    }
}
