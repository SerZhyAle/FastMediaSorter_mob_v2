package com.sza.fastmediasorter.core.panel

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IconHueCatalogTest {

    @Test
    fun `every resource type has a hue`() {
        ResourceType.entries.forEach { assertNotEquals(it.name, 0, IconHueCatalog.forResourceType(it)) }
    }

    @Test
    fun `a program wears its own accent and an unknown route the product accent`() {
        assertEquals(
            SubProgramAccentCatalog.accentFor(InternalRouteCatalog.KEY_CALCULATOR),
            IconHueCatalog.forRoute(InternalRouteCatalog.KEY_CALCULATOR),
        )
        assertEquals(R.color.color_icon_accent, IconHueCatalog.forRoute("no_such_route"))
    }

    @Test
    fun `streams and the watch wear their program tone, not a source hue`() {
        assertEquals(
            IconHueCatalog.forRoute(InternalRouteCatalog.KEY_STREAMS),
            IconHueCatalog.forResourceType(ResourceType.HTTP_STREAM),
        )
        assertEquals(
            IconHueCatalog.forRoute(InternalRouteCatalog.KEY_WEAR_COMPANION),
            IconHueCatalog.forResourceType(ResourceType.WEAR_WATCH),
        )
    }
}
