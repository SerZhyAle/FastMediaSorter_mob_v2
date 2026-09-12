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
class AppDatabaseMigration57To58Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate57To58_addsSourceDeviceColumnAndKeepsUserAuthoredRows() {
        helper.createDatabase(TEST_DB, 57).use { old ->
            old.execSQL(insertUserStream())
            old.execSQL(insertUserState())
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 58, true, MIGRATION_57_58)

        db.query(
            "SELECT url, sourceDeviceId FROM stream_sources WHERE url = ?",
            arrayOf<Any>(USER_URL)
        ).use { cursor ->
            assertTrue("a user-authored stream must survive the migration", cursor.moveToFirst())
            assertEquals(USER_URL, cursor.getString(0))
            assertTrue("a row that named no device must read null, not empty", cursor.isNull(1))
        }
        db.query("SELECT pinned FROM stream_user_state WHERE identityKey = ?", arrayOf<Any>(IDENTITY))
            .use { cursor ->
                assertTrue("a user pin must survive the migration", cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
    }

    @Test
    fun migrate57To58_letsTwoRowsCarryNoDeviceAtOnce() {
        helper.createDatabase(TEST_DB_NULLS, 57).close()

        val db = helper.runMigrationsAndValidate(TEST_DB_NULLS, 58, true, MIGRATION_57_58)

        db.execSQL(insertUserStream())
        db.execSQL(insertUserStream(id = SECOND_STREAM_ID, url = SECOND_URL, identity = SECOND_IDENTITY))

        db.query("SELECT COUNT(*) FROM stream_sources WHERE sourceDeviceId IS NULL").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("the device index must not be unique over nulls", 2, cursor.getInt(0))
        }
    }

    private fun insertUserStream(
        id: String = STREAM_ID,
        url: String = USER_URL,
        identity: String = IDENTITY
    ): String =
        "INSERT INTO stream_sources " +
            "(id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt, identityKey) " +
            "VALUES ('$id', '$url', 'Mine', 'AUDIO', 'MANUAL', 0, 1, 1, '$identity')"

    private fun insertUserState(): String =
        "INSERT INTO stream_user_state " +
            "(identityKey, pinned, sortIndex, playOutcome, outcomeAt, updatedAt) " +
            "VALUES ('$IDENTITY', 1, 0, NULL, NULL, 1)"

    private companion object {
        const val TEST_DB = "migration-test-57-to-58"
        const val TEST_DB_NULLS = "migration-test-57-to-58-nulls"
        const val STREAM_ID = "22222222-2222-2222-2222-222222222222"
        const val SECOND_STREAM_ID = "33333333-3333-3333-3333-333333333333"
        const val USER_URL = "https://example.test/mine.m3u8"
        const val SECOND_URL = "https://example.test/other.m3u8"
        const val IDENTITY = "example.test/mine"
        const val SECOND_IDENTITY = "example.test/other"
    }
}
