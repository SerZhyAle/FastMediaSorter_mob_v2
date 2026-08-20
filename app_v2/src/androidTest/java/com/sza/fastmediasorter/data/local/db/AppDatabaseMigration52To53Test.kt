package com.sza.fastmediasorter.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1832: instrumented migration test for the launcher-cell hop (schema 52 -> 53).
 *
 * Two failures are worth more than the happy path here. A rewrite that blanked an unresolvable target
 * would delete a cell the user placed by hand - strategic §7 rates losing user data on a real device as
 * the risk that costs exactly what this ticket promises to keep. And a rewrite matching too broadly
 * would corrupt every non-stream cell on the desktop, which no other test would notice.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration52To53Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate52To53_reAddressesResolvableStreamCellsAndLeavesEverythingElseAlone() {
        helper.createDatabase(TEST_DB, 52).use { old ->
            old.execSQL(insertSource(LIVE_ID, LIVE_URL))
            old.execSQL(insertCell(CELL_LIVE, "${STREAM_PREFIX}$LIVE_ID"))
            old.execSQL(insertCell(CELL_GONE, "${STREAM_PREFIX}$GONE_ID"))
            old.execSQL(insertCell(CELL_APP, APP_TARGET))
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        assertEquals(
            "a cell whose channel is still in the catalog must now address it by identity",
            STREAM_PREFIX + StreamChannelIdentity.of(LIVE_URL),
            targetOf(db, CELL_LIVE)
        )
        assertEquals(
            "a cell whose channel is already gone has nothing to derive an identity from, so it must " +
                "be left byte-identical rather than blanked",
            "${STREAM_PREFIX}$GONE_ID",
            targetOf(db, CELL_GONE)
        )
        assertEquals(
            "a cell of any other kind must not be touched by a stream-only rewrite",
            APP_TARGET,
            targetOf(db, CELL_APP)
        )

        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'stream_play_outcome'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("the retired outcome table must be gone", 0, cursor.getInt(0))
        }
    }

    private fun targetOf(db: androidx.sqlite.db.SupportSQLiteDatabase, cellId: Long): String =
        db.query(
            "SELECT target FROM launcher_cells WHERE id = ?",
            arrayOf<Any>(cellId)
        ).use { cursor ->
            assertTrue("seeded cell $cellId must survive the migration", cursor.moveToFirst())
            cursor.getString(0)
        }

    // identityKey is written out rather than left to its column default: at version 52 every row on a
    // real device carries the value MIGRATION_51_52 backfilled, and a seed row holding '' would test a
    // state the app cannot be in.
    private fun insertSource(id: String, url: String): String =
        "INSERT INTO stream_sources " +
            "(id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt, identityKey) " +
            "VALUES ('$id', '$url', 'title-$id', 'AUDIO', 'CATALOG', 0, 0, 1, " +
            "'${StreamChannelIdentity.of(url)}')"

    private fun insertCell(id: Long, target: String): String =
        "INSERT INTO launcher_cells " +
            "(id, orientation, rowIndex, colIndex, spanW, spanH, kind, target, labelOverride, addedAt) " +
            "VALUES ($id, 'PORTRAIT', 0, 0, 1, 1, 'SHORTCUT', '$target', NULL, 1)"

    private companion object {
        const val TEST_DB = "migration-test-52-to-53"

        const val STREAM_PREFIX = "stream:"
        const val APP_TARGET = "app:com.example.other"

        const val LIVE_ID = "live-row-id"
        const val GONE_ID = "row-id-of-a-channel-already-pruned"
        const val LIVE_URL = "https://example.org/live.mp3"

        const val CELL_LIVE = 1L
        const val CELL_GONE = 2L
        const val CELL_APP = 3L
    }
}
