package com.sza.fastmediasorter.wear.ui.common

import com.sza.fastmediasorter.wear.domain.model.WearContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2003: the distinct-tone assertion is the only part of strategic criterion 6 - "each type differs
 * from its neighbour by both colour and shape" - that a machine can settle. Legibility of those
 * tones on a small round display is reserved to the on-device pass by design.
 */
class ContentTypeCatalogTest {

    private companion object {
        const val EXPECTED_TYPE_COUNT = 7
    }

    @Test
    fun `every type has a glyph`() {
        WearContentType.entries.forEach { type ->
            assertNotEquals(
                "no glyph declared for $type",
                0,
                ContentTypeCatalog.iconFor(type)
            )
        }
    }

    @Test
    fun `every type has a tone`() {
        WearContentType.entries.forEach { type ->
            assertNotEquals(
                "no tone declared for $type",
                0,
                ContentTypeCatalog.tintFor(type)
            )
        }
    }

    @Test
    fun `no two types share a tone`() {
        val tones = WearContentType.entries.map(ContentTypeCatalog::tintFor)
        assertEquals(
            "two content types resolve to the same colour resource",
            tones.size,
            tones.distinct().size
        )
    }

    @Test
    fun `every glyph in the set is tintable`() {
        WearContentType.entries.forEach { type ->
            assertTrue(
                "$type carries a glyph the semantic tone cannot be applied to",
                ContentTypeCatalog.isMonochrome(type)
            )
        }
    }

    /** An eighth type must fail here, where the catalog is, rather than at some screen that forgot it. */
    @Test
    fun `the catalog covers exactly the declared types`() {
        assertEquals(EXPECTED_TYPE_COUNT, WearContentType.entries.size)
    }
}
