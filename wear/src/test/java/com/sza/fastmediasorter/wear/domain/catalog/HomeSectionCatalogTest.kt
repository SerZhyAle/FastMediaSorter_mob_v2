package com.sza.fastmediasorter.wear.domain.catalog

import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.destinationFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1940: favourites has to stay at the end whatever else the catalog decides to emit.
 *
 * S1974: the catalog no longer emits the last-used shortcut at all. That absence is asserted rather
 * than assumed, because a conditional first member is exactly what used to shift every predefined
 * section by one cell whenever a resource was opened or deleted.
 */
// S2511: MAX_BUTTONS is 7 and the last cell leads to the screen listing the rest, so six sections fit.
private const val TILE_CELLS_BEFORE_OVERFLOW = 6

class HomeSectionCatalogTest {

    @Test
    fun `the catalog emits no shortcut row of its own`() {
        val sections = HomeSectionCatalog.sectionsFor(visibility(streamsEnabled = true))

        assertNull(sections.firstOrNull { it.id == HomeSectionId.LAST_USED_RESOURCE })
    }

    @Test
    fun `resources is the first section whatever else is enabled`() {
        assertEquals(HomeSectionId.RESOURCES, HomeSectionCatalog.sectionsFor(visibility()).first().id)
        assertEquals(
            HomeSectionId.RESOURCES,
            HomeSectionCatalog.sectionsFor(visibility(streamsEnabled = true)).first().id
        )
    }

    @Test
    fun `favourites is the last section`() {
        val sections = HomeSectionCatalog.sectionsFor(visibility())

        assertEquals(HomeSectionId.FAVOURITES, sections.last().id)
    }

    @Test
    fun `a catalogued section is addressed by its id and carries no route`() {
        // S2751: a route on a record the domain hands out is what put the address table below the
        // screens. Every catalogued section must therefore answer null here and resolve through its id.
        HomeSectionCatalog.sectionsFor(visibility(streamsEnabled = true)).forEach { section ->
            assertNull("${'$'}{section.id} must not carry an address", section.route)
            assertNotNull("${'$'}{section.id} must resolve to a destination", destinationFor(section.id))
        }
    }

    @Test
    fun `a conditional section cannot displace favourites from the end`() {
        val sections = HomeSectionCatalog.sectionsFor(visibility(streamsEnabled = true))

        assertEquals(HomeSectionId.FAVOURITES, sections.last().id)
    }

    /**
     * S2551: the two directions are separate rows, and the order says which is which.
     *
     * Asserted as a relation rather than as a cell number, because STREAMS above them is conditional
     * and the count of drawn rows therefore varies with the owner's settings.
     */
    @Test
    fun `the phone camera row sits directly after the broadcast row and before favourites`() {
        val ids = HomeSectionCatalog.sectionsFor(visibility(streamsEnabled = true)).map { it.id }

        val broadcast = ids.indexOf(HomeSectionId.BROADCAST)
        val phoneCamera = ids.indexOf(HomeSectionId.PHONE_CAMERA)

        assertEquals("the phone camera row is missing", broadcast + 1, phoneCamera)
        assertEquals(HomeSectionId.FAVOURITES, ids.last())
    }

    /**
     * S2511: the grid holds seven cells and spends one of them on the way out, so six sections reach the
     * tile. Favourites is one of the six the owner named in the request, and screen order put it eighth -
     * which is how the tile came to drop it without saying so.
     */
    @Test
    fun `the tile order keeps every section the request named within reach of the grid`() {
        val onTile = HomeSectionCatalog.tileSectionsFor(visibility(streamsEnabled = true))
            .take(TILE_CELLS_BEFORE_OVERFLOW)
            .map { it.id }

        listOf(
            HomeSectionId.RESOURCES,
            HomeSectionId.PHONE,
            HomeSectionId.LOCAL,
            HomeSectionId.STREAMS,
            HomeSectionId.APPS,
            HomeSectionId.FAVOURITES
        ).forEach { id ->
            assertTrue("$id is not on the tile", onTile.contains(id))
        }
    }

    @Test
    fun `the tile shows the same sections as the screen, only in its own order`() {
        val visibility = visibility(streamsEnabled = true)

        assertEquals(
            HomeSectionCatalog.sectionsFor(visibility).map { it.id }.toSet(),
            HomeSectionCatalog.tileSectionsFor(visibility).map { it.id }.toSet()
        )
    }

    /** A section switched off is absent from the tile for the same reason it is absent from the screen. */
    @Test
    fun `the tile drops the streams section when it is switched off`() {
        val ids = HomeSectionCatalog.tileSectionsFor(visibility(streamsEnabled = false)).map { it.id }

        assertFalse(ids.contains(HomeSectionId.STREAMS))
    }

    /** A row added by a later ticket sorts behind the named six rather than pushing one of them off. */
    @Test
    fun `the rows added after the request sort last on the tile`() {
        val ids = HomeSectionCatalog.tileSectionsFor(visibility(streamsEnabled = true)).map { it.id }

        assertTrue(ids.indexOf(HomeSectionId.FAVOURITES) < ids.indexOf(HomeSectionId.BROADCAST))
        assertTrue(ids.indexOf(HomeSectionId.FAVOURITES) < ids.indexOf(HomeSectionId.PHONE_CAMERA))
    }

    private fun visibility(streamsEnabled: Boolean = false) = HomeSectionVisibility(
        streamsEnabled = streamsEnabled
    )
}
