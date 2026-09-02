package com.sza.fastmediasorter.ui.common

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S2046: before the catalog existed, five tables assigned a hue per media type and two of them had
 * already drifted apart on one member with nothing to notice it. The distinct-colour assertion below
 * is what makes that drift a build failure instead of a discovery.
 *
 * Legibility of the tones against a real theme stays on the device pass - a resource id cannot carry
 * contrast.
 */
class MediaTypeColorCatalogTest {

    private companion object {
        const val EXPECTED_CATEGORY_COUNT = 5
    }

    @Test
    fun `every category has a colour`() {
        MediaColorCategory.entries.forEach { category ->
            assertNotEquals(
                "no colour declared for $category",
                0,
                MediaTypeColorCatalog.colorFor(category)
            )
        }
    }

    @Test
    fun `no two categories share a colour`() {
        val colours = MediaColorCategory.entries.map { MediaTypeColorCatalog.colorFor(it) }
        assertEquals(
            "two categories resolve to the same colour resource: $colours",
            MediaColorCategory.entries.size,
            colours.toSet().size
        )
    }

    @Test
    fun `every media type maps to a category`() {
        MediaType.entries.forEach { type ->
            val category = MediaTypeColorCatalog.categoryOf(type)
            assertNotEquals(
                "no colour reachable for $type",
                0,
                MediaTypeColorCatalog.colorFor(category)
            )
        }
    }

    @Test
    fun `every stats type maps to a category and every category is reachable`() {
        val reached = StatsMediaType.entries.map { MediaTypeColorCatalog.categoryOf(it) }.toSet()
        assertEquals(
            "the statistics surface cannot reach every category: $reached",
            MediaColorCategory.entries.toSet(),
            reached
        )
    }

    @Test
    fun `category count is pinned so adding one is deliberate`() {
        assertEquals(EXPECTED_CATEGORY_COUNT, MediaColorCategory.entries.size)
    }
}
