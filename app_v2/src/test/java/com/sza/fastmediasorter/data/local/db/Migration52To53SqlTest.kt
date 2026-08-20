package com.sza.fastmediasorter.data.local.db

import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1832: proves the SQL of [MIGRATION_52_53] on the JVM.
 *
 * [AppDatabaseMigration52To53Test] is the real migration test - it validates the resulting schema, which
 * only Room's instrumented helper can do - but nothing in this repository runs the instrumented set
 * today (S1844), so on its own it would leave an irreversible statement unexecuted until a user's device
 * ran it. This test executes exactly the same statements against real SQLite and reads back what they
 * did, which is the half that can be proven without a device.
 *
 * The database is opened at the current schema and the retired table re-created by hand. That is a
 * faithful stand-in because this hop changes neither `launcher_cells` nor `stream_sources`: the two
 * tables the rewrite reads and writes have identical shapes at 52 and 53, and the only other object the
 * migration touches is the table created below.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration52To53SqlTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val db get() = dbRule.db.openHelper.writableDatabase

    @Test
    fun `resolvable stream targets are re-addressed and nothing else is touched`() {
        db.execSQL(CREATE_RETIRED_TABLE)
        db.execSQL(insertSource(LIVE_ID, LIVE_URL))
        db.execSQL(insertCell(CELL_LIVE, STREAM_PREFIX + LIVE_ID))
        db.execSQL(insertCell(CELL_GONE, STREAM_PREFIX + GONE_ID))
        db.execSQL(insertCell(CELL_APP, APP_TARGET))

        MIGRATION_52_53.migrate(db)

        assertEquals(
            "a live channel's cell must now carry the identity",
            STREAM_PREFIX + StreamChannelIdentity.of(LIVE_URL),
            targetOf(CELL_LIVE)
        )
        assertEquals(
            "an unresolvable target has nothing to derive from and must survive verbatim",
            STREAM_PREFIX + GONE_ID,
            targetOf(CELL_GONE)
        )
        assertEquals("a non-stream cell is not a stream cell", APP_TARGET, targetOf(CELL_APP))
        assertEquals("the retired table must be gone", 0, retiredTableCount())
    }

    @Test
    fun `a row whose identity is somehow empty is left alone rather than blanked`() {
        db.execSQL(CREATE_RETIRED_TABLE)
        // The backfill in MIGRATION_51_52 gives every row an identity, so this state should not exist.
        // It is seeded anyway because the cost of being wrong about that is a desktop cell rewritten to
        // the bare prefix `stream:`, which decodes to null and renders as a dead tile.
        db.execSQL(insertSource(LIVE_ID, LIVE_URL, identity = ""))
        db.execSQL(insertCell(CELL_LIVE, STREAM_PREFIX + LIVE_ID))

        MIGRATION_52_53.migrate(db)

        assertEquals(STREAM_PREFIX + LIVE_ID, targetOf(CELL_LIVE))
    }

    private fun targetOf(cellId: Long): String =
        db.query("SELECT target FROM launcher_cells WHERE id = $cellId").use { cursor ->
            assertTrue("seeded cell $cellId must survive", cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun retiredTableCount(): Int =
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'stream_play_outcome'"
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun insertSource(id: String, url: String, identity: String? = null): String =
        "INSERT INTO stream_sources " +
            "(id, url, title, mediaKind, sourceOrigin, sortIndex, pinned, addedAt, identityKey) " +
            "VALUES ('$id', '$url', 'title', 'AUDIO', 'CATALOG', 0, 0, 1, " +
            "'${identity ?: StreamChannelIdentity.of(url)}')"

    private fun insertCell(id: Long, target: String): String =
        "INSERT INTO launcher_cells " +
            "(id, orientation, rowIndex, colIndex, spanW, spanH, kind, target, labelOverride, addedAt) " +
            "VALUES ($id, 'PORTRAIT', 0, 0, 1, 1, 'SHORTCUT', '$target', NULL, 1)"

    private companion object {
        const val CREATE_RETIRED_TABLE =
            "CREATE TABLE IF NOT EXISTS `stream_play_outcome` (`streamId` TEXT NOT NULL, " +
                "`outcome` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL, PRIMARY KEY(`streamId`))"

        const val STREAM_PREFIX = "stream:"
        const val APP_TARGET = "app:com.example.other"

        const val LIVE_ID = "live-row-id"
        const val GONE_ID = "already-pruned-row-id"
        const val LIVE_URL = "https://example.org/live.mp3"

        const val CELL_LIVE = 1L
        const val CELL_GONE = 2L
        const val CELL_APP = 3L
    }
}
