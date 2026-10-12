package com.sza.fastmediasorter.data.local.db

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
 * S4160: the broadcast endpoint column of `stream_sources` (schema 61 -> 62). A channel saved at 61 must keep
 * its address and carry no endpoint list, so it plays exactly as it did before the migration.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration61To62Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate61To62_keepsChannelAndLeavesEndpointsNull() {
        helper.createDatabase(TEST_DB, 61).use { old ->
            old.execSQL(
                """
                INSERT INTO stream_sources (
                    id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt, identityKey, sourceDeviceId
                ) VALUES (
                    'row-1', '$CHANNEL_URL', 'Phone', 'AUDIO', 'MANUAL', 0, 0, 1000, 'key-1', 'device-1'
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 62, true, MIGRATION_61_62)

        db.query("SELECT url, sourceDeviceId, broadcastEndpoints FROM stream_sources").use { cursor ->
            assertEquals("the pre-migration channel must survive", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(CHANNEL_URL, cursor.getString(0))
            assertEquals("device-1", cursor.getString(1))
            assertNull("an existing channel must carry no endpoint list", cursor.getString(2))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-61-to-62"
        const val CHANNEL_URL = "http://192.0.2.10:8768/live-audio.aac"
    }
}
