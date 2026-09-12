package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2673 is the incident this join has already failed once, silently: the offset was applied when the item
 * was registered and not when it was read back, so exactly one item out of sixteen matched and wore another
 * program's colour while nothing failed. S2889 added two more readers of the same join, which is why the
 * round trip is pinned here rather than left to whoever notices a menu of grey icons.
 */
class MainProgramsMenuOrderTest {

    @Test
    fun `every entry survives the round trip`() {
        val lost = SubProgramCatalog.all()
            .filter { MainProgramsMenuOrder.entryForMenuOrder(MainProgramsMenuOrder.menuOrderFor(it)) != it }
            .map { it.routeKey }

        assertEquals("entries the menu order does not resolve back to: $lost", emptyList<String>(), lost)
    }

    @Test
    fun `the round trip resolves the whole catalog and nothing else`() {
        val resolved = SubProgramCatalog.all()
            .mapNotNull { MainProgramsMenuOrder.entryForMenuOrder(MainProgramsMenuOrder.menuOrderFor(it)) }

        assertEquals(SubProgramCatalog.all().size, resolved.distinct().size)
    }

    @Test
    fun `an order below the registry band is not a sub-program`() {
        assertNull(MainProgramsMenuOrder.entryForMenuOrder(MainProgramsMenuOrder.MENU_ORDER_REGISTRY_BASE - 1))
    }

    @Test
    fun `an order above the registry band is not a sub-program`() {
        val beyond = SubProgramCatalog.all().maxOf { MainProgramsMenuOrder.menuOrderFor(it) } + 1

        assertNull(MainProgramsMenuOrder.entryForMenuOrder(beyond))
    }
}
