package com.sza.fastmediasorter.wear.ui.home

import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1940: favourites has to stay at the end whatever else the catalog decides to emit.
 *
 * S1974: the catalog no longer emits the last-used shortcut at all. That absence is asserted rather
 * than assumed, because a conditional first member is exactly what used to shift every predefined
 * section by one cell whenever a resource was opened or deleted.
 */
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
    fun `favourites is offered before anything is marked`() {
        val sections = HomeSectionCatalog.sectionsFor(visibility())

        val favourites = sections.first { it.id == HomeSectionId.FAVOURITES }
        assertEquals(WearRoutes.FAVOURITES, favourites.route)
    }

    @Test
    fun `a conditional section cannot displace favourites from the end`() {
        val sections = HomeSectionCatalog.sectionsFor(visibility(streamsEnabled = true))

        assertEquals(HomeSectionId.FAVOURITES, sections.last().id)
    }

    private fun visibility(streamsEnabled: Boolean = false) = HomeSectionVisibility(
        streamsEnabled = streamsEnabled
    )
}
