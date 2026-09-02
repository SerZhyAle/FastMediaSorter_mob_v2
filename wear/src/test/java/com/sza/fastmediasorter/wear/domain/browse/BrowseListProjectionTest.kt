package com.sza.fastmediasorter.wear.domain.browse

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.contentTypeForMime
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2136: the projection is the only thing standing between a loaded list and what the wearer reads,
 * and strategic §11 criterion 8 makes its coverage a completion criterion. The watch content list had
 * no test in this dimension at all, so every branch below is new ground rather than a regression net.
 */
class BrowseListProjectionTest {

    private val anyUri = mockk<Uri>(relaxed = true)

    private fun file(name: String, mime: String, date: Long, size: Long) = WearMediaFile(
        id = date,
        name = name,
        uri = anyUri,
        mimeType = mime,
        size = size,
        dateModified = date
    )

    private val alpha = file("Alpha.mp3", "audio/mpeg", date = 300L, size = 30L)
    private val beta = file("beta.jpg", "image/jpeg", date = 100L, size = 10L)
    private val gamma = file("Gamma.mp4", "video/mp4", date = 200L, size = 20L)

    private val items = listOf(alpha, beta, gamma)

    private val keys = BrowseRefineKeys<WearMediaFile>(
        name = { it.name },
        contentType = { contentTypeForMime(it.mimeType) ?: WearContentType.OTHER },
        dateModified = { it.dateModified },
        sizeBytes = { it.size }
    )

    private fun refined(state: BrowseRefineState) = BrowseListProjection.refine(items, keys, state)

    @Test
    fun `blank query keeps every item`() {
        assertEquals(items, refined(BrowseRefineState(searchQuery = "   ")))
    }

    @Test
    fun `query matches a substring regardless of case`() {
        assertEquals(listOf(alpha), refined(BrowseRefineState(searchQuery = "ALPH")))
        assertEquals(listOf(beta), refined(BrowseRefineState(searchQuery = "ET")))
    }

    @Test
    fun `empty type set filters nothing`() {
        assertEquals(items, refined(BrowseRefineState(contentTypes = emptySet())))
    }

    @Test
    fun `type set keeps only its types`() {
        val state = BrowseRefineState(
            contentTypes = setOf(WearContentType.IMAGE, WearContentType.VIDEO)
        )
        assertEquals(listOf(beta, gamma), refined(state))
    }

    @Test
    fun `default order preserves the incoming order`() {
        assertEquals(items, refined(BrowseRefineState(sortOrder = BrowseSortOrder.DEFAULT)))
    }

    @Test
    fun `name orders ignore case in both directions`() {
        assertEquals(
            listOf(alpha, beta, gamma),
            refined(BrowseRefineState(sortOrder = BrowseSortOrder.NAME_ASC))
        )
        assertEquals(
            listOf(gamma, beta, alpha),
            refined(BrowseRefineState(sortOrder = BrowseSortOrder.NAME_DESC))
        )
    }

    @Test
    fun `date orders run both ways`() {
        assertEquals(
            listOf(beta, gamma, alpha),
            refined(BrowseRefineState(sortOrder = BrowseSortOrder.DATE_ASC))
        )
        assertEquals(
            listOf(alpha, gamma, beta),
            refined(BrowseRefineState(sortOrder = BrowseSortOrder.DATE_DESC))
        )
    }

    @Test
    fun `size orders run both ways`() {
        assertEquals(
            listOf(beta, gamma, alpha),
            refined(BrowseRefineState(sortOrder = BrowseSortOrder.SIZE_ASC))
        )
        assertEquals(
            listOf(alpha, gamma, beta),
            refined(BrowseRefineState(sortOrder = BrowseSortOrder.SIZE_DESC))
        )
    }

    @Test
    fun `an unknown size sorts last in both directions`() {
        val unmeasured = BrowseRefineKeys<WearMediaFile>(
            name = { it.name },
            contentType = { WearContentType.OTHER },
            sizeBytes = { if (it.name == beta.name) null else it.size }
        )
        assertEquals(
            listOf(gamma, alpha, beta),
            BrowseListProjection.refine(
                items,
                unmeasured,
                BrowseRefineState(sortOrder = BrowseSortOrder.SIZE_ASC)
            )
        )
        assertEquals(
            listOf(alpha, gamma, beta),
            BrowseListProjection.refine(
                items,
                unmeasured,
                BrowseRefineState(sortOrder = BrowseSortOrder.SIZE_DESC)
            )
        )
    }

    @Test
    fun `an order whose key is absent falls back to the incoming order`() {
        val nameOnly = BrowseRefineKeys<WearMediaFile>(
            name = { it.name },
            contentType = { WearContentType.OTHER }
        )
        assertEquals(
            items,
            BrowseListProjection.refine(
                items,
                nameOnly,
                BrowseRefineState(sortOrder = BrowseSortOrder.DATE_DESC)
            )
        )
    }

    @Test
    fun `query type filter and order all apply together`() {
        val state = BrowseRefineState(
            searchQuery = "a",
            contentTypes = setOf(WearContentType.MUSIC, WearContentType.VIDEO),
            sortOrder = BrowseSortOrder.NAME_DESC
        )
        assertEquals(listOf(gamma, alpha), refined(state))
    }

    @Test
    fun `available orders drop the pairs whose key is missing`() {
        val nameOnly = BrowseRefineKeys<WearMediaFile>(
            name = { it.name },
            contentType = { WearContentType.OTHER }
        )
        assertEquals(
            listOf(BrowseSortOrder.DEFAULT, BrowseSortOrder.NAME_ASC, BrowseSortOrder.NAME_DESC),
            nameOnly.availableSortOrders()
        )
        assertEquals(7, keys.availableSortOrders().size)
    }

    @Test
    fun `available orders drop only the date pair when just the date is missing`() {
        val sizeOnly = BrowseRefineKeys<WearMediaFile>(
            name = { it.name },
            contentType = { WearContentType.OTHER },
            sizeBytes = { it.size }
        )
        assertEquals(
            listOf(
                BrowseSortOrder.DEFAULT,
                BrowseSortOrder.NAME_ASC,
                BrowseSortOrder.NAME_DESC,
                BrowseSortOrder.SIZE_ASC,
                BrowseSortOrder.SIZE_DESC
            ),
            sizeOnly.availableSortOrders()
        )
    }

    @Test
    fun `present types report one entry for a homogeneous list`() {
        assertEquals(
            listOf(WearContentType.MUSIC),
            BrowseListProjection.presentTypes(listOf(alpha), keys)
        )
    }

    @Test
    fun `present types follow the enum declaration order`() {
        assertEquals(
            listOf(WearContentType.MUSIC, WearContentType.VIDEO, WearContentType.IMAGE),
            BrowseListProjection.presentTypes(items, keys)
        )
    }
}
