package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * giant IN/NOT IN clause fails the test where the runtime enforces the cap, while always asserting
 * correct add/update/prune semantics at scale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamSourceCatalogMergeTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.streamSourceDao()
    private val repo get() = StreamSourceRepository(
        dbRule.db,
        dao,
        dbRule.db.streamPlayOutcomeDao(),
        dbRule.db.streamQualityMemoryDao(),
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
        dao.upsert(
            StreamSourceEntity(
                id = "manual-1",
                url = sharedUrl,
                title = "My channel",
                mediaKind = "VIDEO",
                sourceOrigin = "MANUAL",
                sortIndex = 0,
                addedAt = 0L,
            ),
        )

        val result = repo.mergeCatalog(listOf(catalogEntry(0)))

        // The catalog insert is ignored on the unique-url collision; the manual row is left intact.
        assertEquals(0, result.added)
        assertEquals(0, result.removed)
        val row = dao.getByUrl(sharedUrl)
        assertNotNull(row)
        assertEquals("MANUAL", row!!.sourceOrigin)
        assertEquals("My channel", row.title)
    }

    private fun catalogEntry(index: Int) = StreamSourceEntity(
        id = "cat-$index",
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
    }
}
