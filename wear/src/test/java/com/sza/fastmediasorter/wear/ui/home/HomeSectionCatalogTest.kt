package com.sza.fastmediasorter.wear.ui.home

import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1836: the row named after a resource used to declare the same route as the Resources row, so two
 * neighbouring rows did the same thing and differed only by caption. These cases pin the destination.
 */
class HomeSectionCatalogTest {

    @Test
    fun `the last used row addresses the remembered source`() {
        val sections = HomeSectionCatalog.sectionsFor(visibility(LastUsedResource(SOURCE_ID, SOURCE_NAME)))

        val lastUsed = sections.first { it.id == HomeSectionId.LAST_USED_RESOURCE }
        assertEquals(WearRoutes.sourceMediaType(SOURCE_ID, SOURCE_NAME), lastUsed.route)
        assertEquals(SOURCE_NAME, lastUsed.dynamicLabel)
    }

    @Test
    fun `the last used row does not repeat the resources list`() {
        val sections = HomeSectionCatalog.sectionsFor(visibility(LastUsedResource(SOURCE_ID, SOURCE_NAME)))

        val lastUsed = sections.first { it.id == HomeSectionId.LAST_USED_RESOURCE }
        val resources = sections.first { it.id == HomeSectionId.RESOURCES }
        assertNotEquals(resources.route, lastUsed.route)
        assertEquals(WearRoutes.NETWORK_SOURCES, resources.route)
    }

    @Test
    fun `a gone source leaves no row behind`() {
        val sections = HomeSectionCatalog.sectionsFor(visibility(lastUsedResource = null))

        assertNull(sections.firstOrNull { it.id == HomeSectionId.LAST_USED_RESOURCE })
    }

    private fun visibility(lastUsedResource: LastUsedResource?) = HomeSectionVisibility(
        favouritesEnabled = false,
        lastUsedResource = lastUsedResource,
        streamsEnabled = false
    )

    private companion object {
        const val SOURCE_ID = "src-7"
        const val SOURCE_NAME = "MyNAS"
    }
}
