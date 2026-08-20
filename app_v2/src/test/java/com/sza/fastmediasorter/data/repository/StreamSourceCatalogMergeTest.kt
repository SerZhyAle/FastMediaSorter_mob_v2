package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S0821 regression: [StreamSourceRepository.mergeCatalog] must scale past SQLite's per-statement
 * bind-variable limit. The original prune issued one `DELETE .. url NOT IN (:keepUrls)` binding the
 * entire new catalog, which aborted large-catalog imports on shipped API-29 devices with
 * "too many SQL variables". Each scenario here exceeds that 999-variable limit so a reintroduced
 * giant IN/NOT IN clause fails the test where the runtime enforces the cap.
 *
 * S1832: the three tests that used to assert S1826's orphan purge now assert what replaced it. The old
 * contract was "a pruned channel's play outcome is deleted with it"; the new one is the opposite - the
 * user's pin, its position and the outcome are filed under the channel's derived identity and must
 * SURVIVE the channel leaving the published bank and coming back as a different row. What still gets
 * deleted is state the user themselves discarded, and unpinned state old enough to be noise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamSourceCatalogMergeTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.streamSourceDao()
    private val userStateDao get() = dbRule.db.streamUserStateDao()
    private val repo get() = StreamSourceRepository(
        dbRule.db,
        dao,
        dbRule.db.streamQualityMemoryDao(),
        dbRule.db.streamUserStateDao(),
    )

    @Test
    fun mergeCatalog_largeCatalog_doesNotHitBindLimit() = runTest {
        val catalog = (0 until LARGE_CATALOG).map { catalogEntry(it) }

        val result = repo.mergeCatalog(catalog)

        assertEquals(LARGE_CATALOG, result.added)
        assertEquals(0, result.updated)
        assertEquals(0, result.removed)
        assertEquals(LARGE_CATALOG, dao.catalogSources().size)
    }

    @Test
    fun mergeCatalog_largePrune_removesEveryVanishedRow() = runTest {
        repo.mergeCatalog((0 until LARGE_CATALOG).map { catalogEntry(it) })

        // A fully disjoint second catalog forces a prune larger than the bind limit.
        val replacement = (DISJOINT_OFFSET until DISJOINT_OFFSET + SECOND_CATALOG).map { catalogEntry(it) }
        val replacementUrls = replacement.mapTo(HashSet()) { it.url }
        val result = repo.mergeCatalog(replacement)

        assertEquals(SECOND_CATALOG, result.added)
        assertEquals(0, result.updated)
        assertEquals(LARGE_CATALOG, result.removed)
        val remaining = dao.catalogSources()
        assertEquals(SECOND_CATALOG, remaining.size)
        assertTrue(remaining.all { it.url in replacementUrls })
    }

    @Test
    fun mergeCatalog_keepsManualRowWhenCatalogUrlCollides() = runTest {
        val sharedUrl = catalogEntry(0).url
        repo.add(manualEntry("manual-1", sharedUrl))

        val result = repo.mergeCatalog(listOf(catalogEntry(0)))

        // The catalog insert is ignored on the unique-url collision; the manual row is left intact.
        assertEquals(0, result.added)
        assertEquals(0, result.removed)
        val row = dao.getByUrl(sharedUrl)
        assertNotNull(row)
        assertEquals("MANUAL", row!!.sourceOrigin)
        assertEquals("My channel", row.title)
    }

    @Test
    fun mergeCatalog_prunedChannelReturns_keepsPinPositionAndOutcome() = runTest {
        repo.mergeCatalog(listOf(catalogEntry(0), catalogEntry(1)))
        repo.pinToTop("cat-1")
        repo.pinToTop("cat-0")
        repo.recordPlayOutcome("cat-0", "OK")
        val positionBefore = dao.getById("cat-0")!!.sortIndex

        // Channel 0 leaves the published bank entirely, then returns under a brand new row id.
        repo.mergeCatalog(listOf(catalogEntry(1)))
        assertNull("the row itself must be gone while the bank omits it", dao.getById("cat-0"))
        repo.mergeCatalog(listOf(catalogEntry(0, idSuffix = "-reissued"), catalogEntry(1)))

        val returned = dao.getByUrl(catalogEntry(0).url)
        assertNotNull(returned)
        assertNotEquals("the returning row is a different row", "cat-0", returned!!.id)
        assertTrue("the pin must come back", returned.pinned)
        assertEquals("and at the position the user gave it", positionBefore, returned.sortIndex)
        assertEquals("with its play history", "OK", repo.playOutcome(returned.id))
    }

    @Test
    fun mergeCatalog_channelRepublishedOverHttps_keepsPinAndLeavesOneRow() = runTest {
        val overHttp = catalogEntry(0).copy(url = "http://stream.example/0.m3u8")
        repo.mergeCatalog(listOf(overHttp))
        repo.pinToTop("cat-0")

        // The publisher moves the same channel to https. The url-keyed merge prunes the old row and
        // inserts a new one; identity is what carries the pin across.
        val overHttps = catalogEntry(0, idSuffix = "-tls").copy(url = "https://stream.example/0.m3u8")
        repo.mergeCatalog(listOf(overHttps))

        val rows = dao.catalogSources()
        assertEquals("the channel must not end up listed twice", 1, rows.size)
        assertTrue("the pin survives the scheme change", rows.single().pinned)
        assertEquals(
            "both addresses resolve to one identity",
            StreamChannelIdentity.of(overHttp.url),
            StreamChannelIdentity.of(overHttps.url)
        )
    }

    @Test
    fun remove_userDeletedChannel_doesNotComeBackPinned() = runTest {
        repo.mergeCatalog(listOf(catalogEntry(0)))
        repo.pinToTop("cat-0")
        repo.remove(dao.getById("cat-0")!!)

        // The bank still publishes it, so the next import brings the channel back - but not the pin the
        // user deliberately threw away. Absence from the bank is protected against; a user's delete is not.
        repo.mergeCatalog(listOf(catalogEntry(0, idSuffix = "-again")))

        val returned = dao.getByUrl(catalogEntry(0).url)
        assertNotNull(returned)
        assertFalse("an explicit delete must not be undone by an import", returned!!.pinned)
        assertNull(repo.playOutcome(returned.id))
    }

    @Test
    fun mergeCatalog_prune_dropsStaleUnpinnedStateAndKeepsPinnedOfTheSameAge() = runTest {
        val longAgo = System.currentTimeMillis() - STALE_AGE_MILLIS
        userStateDao.setPin(STALE_UNPINNED_KEY, pinned = false, sortIndex = 0, atMillis = longAgo)
        userStateDao.setPin(STALE_PINNED_KEY, pinned = true, sortIndex = 0, atMillis = longAgo)

        repo.mergeCatalog(listOf(catalogEntry(0)))

        assertNull("an unpinned bullet this old is noise", userStateDao.stateFor(STALE_UNPINNED_KEY))
        assertNotNull(
            "a pin is the user saying keep this, whatever its age",
            userStateDao.stateFor(STALE_PINNED_KEY)
        )
    }

    @Test
    fun deleteAllDownloaded_takesUserStateOfDownloadedRowsAndKeepsManualOnes() = runTest {
        repo.mergeCatalog(listOf(catalogEntry(0)))
        repo.add(manualEntry("manual-1", "https://manual.example/live.m3u8"))
        repo.recordPlayOutcome("cat-0", "OK")
        repo.recordPlayOutcome("manual-1", "FAIL")

        val removed = repo.deleteAllDownloaded()

        assertEquals(1, removed)
        assertNull(
            "wiping downloaded channels must not strand their state",
            userStateDao.stateFor(StreamChannelIdentity.of(catalogEntry(0).url))
        )
        assertEquals("FAIL", repo.playOutcome("manual-1"))
    }

    @Test
    fun unpin_keepsThePositionSoRepinningRestoresIt() = runTest {
        repo.mergeCatalog(listOf(catalogEntry(0), catalogEntry(1)))
        repo.pinToTop("cat-0")
        val position = dao.getById("cat-0")!!.sortIndex

        repo.unpin("cat-0")
        val identity = StreamChannelIdentity.of(catalogEntry(0).url)

        assertFalse(userStateDao.stateFor(identity)!!.pinned)
        assertEquals(
            "the ordering the user built is not thrown away",
            position,
            userStateDao.stateFor(identity)!!.sortIndex
        )
    }

    /**
     * S1832 §11 criterion 4: the identity column, its index and the pin projection must not make an
     * import of the whole published bank noticeably slower, and §3.2 forbids the merge degrading into a
     * per-row query.
     *
     * No wall-clock threshold is asserted - that would be a flaky test on shared CI hardware. The two
     * elapsed figures are printed for a human to compare against the pre-change number, and what is
     * asserted is the shape that makes the second merge cheap: every row is still there, and the pin
     * came back without the merge having had to walk rows one at a time to put it there.
     */
    @Test
    fun mergeCatalog_wholePublishedBank_staysWholeBatchOnTheSecondImport() = runTest {
        val bank = (0 until PUBLISHED_BANK).map { catalogEntry(it) }

        val coldMillis = measureMillis { repo.mergeCatalog(bank) }
        val pinnedIdentity = StreamChannelIdentity.of(bank[PIN_TARGET].url)
        repo.pinToTop(bank[PIN_TARGET].id)

        // Same addresses, fresh row ids - exactly what a re-import of an unchanged bank produces.
        val republished = (0 until PUBLISHED_BANK).map { catalogEntry(it, idSuffix = "-again") }
        val warmMillis = measureMillis { repo.mergeCatalog(republished) }

        println("S1832 merge of $PUBLISHED_BANK rows: cold ${coldMillis}ms, warm ${warmMillis}ms")

        assertEquals("no row may be lost by a re-import", PUBLISHED_BANK, dao.catalogSources().size)
        val restored = dao.pinnedSnapshot()
        assertEquals("exactly the one pinned channel comes back pinned", 1, restored.size)
        assertEquals(
            "and it is the same channel, found by identity rather than by the id that changed",
            pinnedIdentity,
            restored.single().identityKey
        )
    }

    private inline fun measureMillis(block: () -> Unit): Long {
        val startedAt = System.nanoTime()
        block()
        return (System.nanoTime() - startedAt) / NANOS_PER_MILLI
    }

    private fun manualEntry(id: String, url: String) = StreamSourceEntity(
        id = id,
        url = url,
        title = "My channel",
        mediaKind = "VIDEO",
        sourceOrigin = "MANUAL",
        sortIndex = 0,
        addedAt = 0L,
    )

    private fun catalogEntry(index: Int, idSuffix: String = "") = StreamSourceEntity(
        id = "cat-$index$idSuffix",
        url = "https://stream.example/$index.m3u8",
        title = "Channel $index",
        mediaKind = "VIDEO",
        sourceOrigin = "CATALOG",
        sortIndex = index,
        addedAt = 0L,
        category = "news",
    )

    private companion object {
        // Both sizes exceed SQLite's 999-variable cap so the old single NOT IN/IN would overflow.
        const val LARGE_CATALOG = 1500
        const val SECOND_CATALOG = 1100
        const val DISJOINT_OFFSET = 100_000

        const val STALE_UNPINNED_KEY = "web://gone.example/unpinned.m3u8"
        const val STALE_PINNED_KEY = "web://gone.example/pinned.m3u8"

        // Comfortably past the repository's 180-day retention window.
        const val STALE_AGE_MILLIS = 400L * 24L * 60L * 60L * 1000L

        // The size of the published bank this app actually ships, measured 2026-08-20. A round number
        // would make the timing figures incomparable with the ones taken on a device.
        const val PUBLISHED_BANK = 17_628
        const val PIN_TARGET = 42
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
