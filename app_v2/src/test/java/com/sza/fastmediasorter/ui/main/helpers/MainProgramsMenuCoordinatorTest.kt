package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import com.sza.fastmediasorter.core.panel.SubProgramSurface
import com.sza.fastmediasorter.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1736 phase 04: the programs menu's composition, order, identity and remove action, tested without
 * an Activity or a PopupMenu - every one of them is a pure decision on the companion object, and the
 * drawing call around them adds nothing this suite could assert.
 *
 * Research artifact 04 measured that this coordinator carried the identity of every sub-program with
 * no test at all, which is why the registry move brings its own net (ADR-4).
 */
class MainProgramsMenuCoordinatorTest {

    private fun gate(
        broadcast: Boolean = true,
        isAvailable: (String) -> Boolean = { true },
    ) = MainProgramsMenuCoordinator.ProgramsMenuGate(
        streams = true,
        vrCinema = true,
        broadcast = broadcast,
        isSubProgramAvailable = isAvailable,
    )

    @Test
    fun `populate emits the registry's programs-menu entries in registry order`() {
        val expected = SubProgramCatalog
            .forSurface(SubProgramSurface.PROGRAMS_MENU)
            .map { it.routeKey }

        val actual = MainProgramsMenuCoordinator.visibleSubPrograms(gate()).map { it.routeKey }

        assertEquals("every programs-menu entry is drawn, in the registry's own order", expected, actual)
        assertEquals(
            "the menu order the items are registered under rises with the registry order",
            actual,
            MainProgramsMenuCoordinator.visibleSubPrograms(gate())
                .sortedBy { MainProgramsMenuOrder.menuOrderFor(it) }
                .map { it.routeKey },
        )
    }

    @Test
    fun `an entry the availability chain refuses is absent`() {
        val refused = InternalRouteCatalog.KEY_CALCULATOR

        val visible = MainProgramsMenuCoordinator
            .visibleSubPrograms(gate(isAvailable = { it != refused }))
            .map { it.routeKey }

        assertFalse("an unavailable program is not drawn", refused in visible)
        assertTrue("its neighbours are unaffected", InternalRouteCatalog.KEY_STOPWATCH in visible)
    }

    @Test
    fun `every drawn item round-trips between its route key and its menu item id`() {
        MainProgramsMenuCoordinator.visibleSubPrograms(gate()).forEach { entry ->
            val itemId = MainProgramsMenuCoordinator.menuItemIdFor(entry.routeKey)
            assertNotNull("'${entry.routeKey}' is drawn without a menu item id", itemId)
            assertEquals(
                "menu item id $itemId resolves back to another program",
                entry.routeKey,
                MainProgramsMenuCoordinator.routeKeyForItemId(itemId!!),
            )
        }
    }

    @Test
    fun `remove acts on the item's own off-switch and its own title`() {
        MainProgramsMenuCoordinator.visibleSubPrograms(gate()).forEach { entry ->
            val itemId = MainProgramsMenuCoordinator.menuItemIdFor(entry.routeKey)!!
            val target = MainProgramsMenuCoordinator.removeTargetFor(itemId)
            val disable = entry.disable
            if (disable == null) {
                assertNull("'${entry.routeKey}' has no off-switch, so it is not removable", target)
            } else {
                assertEquals(
                    "'${entry.routeKey}' removes some other program's setting",
                    disable(AppSettings()),
                    target!!.disable(AppSettings()),
                )
                assertEquals(
                    "'${entry.routeKey}' is confirmed under another program's title",
                    InternalRouteCatalog.byKey(entry.routeKey)!!.labelRes,
                    target.titleRes,
                )
            }
        }

        // The cross-wiring this suite exists to catch, stated on one concrete pair rather than only as
        // a loop invariant: a stale id table sent the tap to the neighbouring branch (S1733, S2881).
        val bothOn = AppSettings(enableCalculator = true, enableStopwatch = true)
        val calculatorRemoved = MainProgramsMenuCoordinator
            .removeTargetFor(MainProgramsMenuCoordinator.menuItemIdFor(InternalRouteCatalog.KEY_CALCULATOR)!!)!!
            .disable(bothOn)
        assertFalse("removing the calculator turns the calculator off", calculatorRemoved.enableCalculator)
        assertTrue("removing the calculator leaves the stopwatch on", calculatorRemoved.enableStopwatch)
    }
}
