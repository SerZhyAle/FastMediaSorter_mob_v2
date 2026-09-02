package com.sza.fastmediasorter.wear.ui.settings

import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2130: the allowed-types screen now enumerates [BrowseCategoryCatalog.DISABLEABLE_TYPES] instead of
 * listing four rows by hand, so adding a type to the vocabulary adds a row without touching the screen.
 *
 * That is the point of the phase and also its new failure mode: [settingsLabelFor] falls back rather
 * than throwing, so a type added to the vocabulary with no label of its own would silently appear
 * wearing the documents wording. These cases are what makes that loud.
 */
class MediaTypesSettingsLabelTest {

    @Test
    fun `every disableable type has its own label`() {
        val labels = BrowseCategoryCatalog.DISABLEABLE_TYPES.map { settingsLabelFor(it) }

        assertEquals(
            "each disableable type must carry a distinct label, not share the fallback",
            BrowseCategoryCatalog.DISABLEABLE_TYPES.size,
            labels.distinct().size
        )
    }

    @Test
    fun `no label id is zero`() {
        BrowseCategoryCatalog.DISABLEABLE_TYPES.forEach { type ->
            assertTrue("$type resolved to no string resource", settingsLabelFor(type) != 0)
        }
    }

    /**
     * The navigational entries are not content types, so nothing may offer to switch them off - the
     * streams toggle moved out of this list for the same reason.
     */
    @Test
    fun `the disableable set holds only real content types`() {
        assertEquals(
            setOf(
                WearContentType.MUSIC,
                WearContentType.VIDEO,
                WearContentType.IMAGE,
                WearContentType.DOCUMENT
            ),
            BrowseCategoryCatalog.DISABLEABLE_TYPES
        )
    }
}
