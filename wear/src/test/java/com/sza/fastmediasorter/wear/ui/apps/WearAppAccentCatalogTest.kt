package com.sza.fastmediasorter.wear.ui.apps

import com.sza.fastmediasorter.wear.domain.model.WearAppId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WearAppAccentCatalogTest {

    @Test
    fun `every program resolves to a colour resource`() {
        WearAppId.entries.forEach { id ->
            assertNotEquals("no accent for $id", 0, WearAppAccentCatalog.accentFor(id))
        }
    }

    /**
     * The whole list fits one watch screen, so a repeat here is not a harmless collision between two
     * distant rows - it is two of five rows looking identical, which is what colouring them was for.
     */
    @Test
    fun `no two programs share an accent`() {
        val accents = WearAppId.entries.map { it to WearAppAccentCatalog.accentFor(it) }
        val collisions = accents
            .groupBy { it.second }
            .filterValues { it.size > 1 }
            .map { (_, pairs) -> pairs.joinToString(" + ") { it.first.name } }

        assertEquals("programs sharing one accent: $collisions", emptyList<String>(), collisions)
    }
}
