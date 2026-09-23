package com.sza.fastmediasorter.wear.tile

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.appIdFor
import com.sza.fastmediasorter.wear.ui.apps.WearAppAccentCatalog
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.home.HomeSectionIconCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3434: the decorated look of a tile cell - which hue its plate takes and which colour its glyph takes on it.
 *
 * The plate hue must be the one the same entity wears on the watch's own screens, or a tile cell and the
 * row it opens read as two things; the glyph colour follows ICON-RENDER 0.10 item D's 3:1 rule.
 */
class WearTilePlateTest {

    @Test
    fun `a program cell takes its Apps-list accent`() {
        WearDestinationId.entries.forEach { destination ->
            val program: WearAppId = appIdFor(destination) ?: return@forEach
            assertEquals(destination.name, WearAppAccentCatalog.accentFor(program), tilePlateHueFor(destination))
        }
    }

    @Test
    fun `a section cell takes its home-row tone`() {
        assertEquals(
            ContentTypeCatalog.tintFor(WearContentType.STREAM),
            tilePlateHueFor(WearDestinationId.STREAMS)
        )
        assertEquals(
            ContentTypeCatalog.tintFor(checkNotNull(HomeSectionIconCatalog.contentTypeFor(HomeSectionId.RESOURCES))),
            tilePlateHueFor(WearDestinationId.RESOURCES)
        )
    }

    @Test
    fun `favourites takes the amber of its own star`() {
        assertEquals(R.color.color_program_accent_amber, tilePlateHueFor(WearDestinationId.FAVOURITES))
    }

    @Test
    fun `a dark plate carries a white glyph`() {
        assertEquals(WHITE, onPlateColorFor(0xFF7B1FA2.toInt()))
        assertEquals(WHITE, onPlateColorFor(0xFF5C6BC0.toInt()))
    }

    @Test
    fun `a plate too light for white carries the dark glyph`() {
        assertEquals(DARK, onPlateColorFor(0xFFFFC400.toInt()))
        assertEquals(DARK, onPlateColorFor(0xFF90A4AE.toInt()))
    }

    @Test
    fun `the glyph spans six tenths of the plate`() {
        assertEquals(0.6f, TILE_PLATE_GLYPH_RATIO, 0.0f)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val DARK = 0xFF1F1F1F.toInt()
    }
}
