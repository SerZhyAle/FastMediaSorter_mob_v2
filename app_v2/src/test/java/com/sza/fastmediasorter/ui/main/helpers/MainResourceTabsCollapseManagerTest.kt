package com.sza.fastmediasorter.ui.main.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MainResourceTabsCollapseManager.resolveVisibility] - the pure decision behind the
 * expanded(tabs) <-> collapsed(strip) toggle. Extracted to the companion so the vanish-rule
 * precedence and the "never both shown" invariant are testable without Android view dependencies
 * (same pure-function approach as DestinationButtonsManagerTest).
 */
class MainResourceTabsCollapseManagerTest {

    @Test
    fun `available and expanded - tabs shown, strip hidden`() {
        val (tabsVisible, stripVisible) =
            MainResourceTabsCollapseManager.resolveVisibility(available = true, collapsed = false)
        assertTrue("expanded: tabs visible", tabsVisible)
        assertFalse("expanded: strip hidden", stripVisible)
    }

    @Test
    fun `available and collapsed - strip shown, tabs hidden`() {
        val (tabsVisible, stripVisible) =
            MainResourceTabsCollapseManager.resolveVisibility(available = true, collapsed = true)
        assertFalse("collapsed: tabs hidden", tabsVisible)
        assertTrue("collapsed: strip visible", stripVisible)
    }

    @Test
    fun `unavailable - both hidden regardless of collapsed (vanish rule wins)`() {
        val (expandedTabs, expandedStrip) =
            MainResourceTabsCollapseManager.resolveVisibility(available = false, collapsed = false)
        assertFalse("vanish + expanded: tabs hidden", expandedTabs)
        assertFalse("vanish + expanded: strip hidden", expandedStrip)

        val (collapsedTabs, collapsedStrip) =
            MainResourceTabsCollapseManager.resolveVisibility(available = false, collapsed = true)
        assertFalse("vanish + collapsed: tabs hidden", collapsedTabs)
        assertFalse("vanish + collapsed: strip hidden", collapsedStrip)
    }

    @Test
    fun `tabs and strip are never both shown`() {
        for (available in listOf(true, false)) {
            for (collapsed in listOf(true, false)) {
                val (tabsVisible, stripVisible) =
                    MainResourceTabsCollapseManager.resolveVisibility(available, collapsed)
                assertFalse(
                    "available=$available collapsed=$collapsed must not show both",
                    tabsVisible && stripVisible
                )
            }
        }
    }
}
