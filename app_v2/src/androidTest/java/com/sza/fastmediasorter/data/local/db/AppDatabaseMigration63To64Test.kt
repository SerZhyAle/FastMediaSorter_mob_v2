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
 * S4607: the exchange entry binding of `stream_sources` (schema 63 -> 64). A channel saved at 63 must keep its
 * address and endpoints and read as unbound, so its end is judged by the whole account as before.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration63To64Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate63To64_keepsChannelAndLeavesItUnbound() {
        helper.createDatabase(TEST_DB, 63).use { old ->
            old.execSQL(
                """
                INSERT INTO stream_sources (
                    id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt, identityKey, sourceDeviceId,
                    broadcastEndpoints
                ) VALUES (
                    'row-1', '$CHANNEL_URL', 'Phone', 'AUDIO', 'IMPORTED', 0, 0, 1000, 'key-1', 'device-1', '$ENDPOINTS'
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 64, true, MIGRATION_63_64)

        db.query(
            "SELECT url, broadcastEndpoints, exchangeEntryId, exchangeBroadcastId FROM stream_sources"
        ).use { cursor ->
            assertEquals("the pre-migration channel must survive", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(CHANNEL_URL, cursor.getString(0))
            assertEquals(ENDPOINTS, cursor.getString(1))
            assertNull("an existing channel must name no entry", cursor.getString(2))
            assertNull("an existing channel must name no listed broadcast", cursor.getString(3))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-63-to-64"
        const val CHANNEL_URL = "http://192.0.2.10:8768/live-audio.aac"
        const val ENDPOINTS = "[]"
    }
}
