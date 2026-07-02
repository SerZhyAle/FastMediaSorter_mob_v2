package com.sza.fastmediasorter.ui.main.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0807: unit tests for [MainProgramsPanelManager.resolveVisibility] - the pure decision behind the
 * expanded(item row) <-> collapsed(strip) toggle of the programs panel. Mirrors the resource-tabs
 * collapse resolver (MainResourceTabsCollapseManagerTest) so the "root owns availability" precedence
 * and the "never both shown" invariant are testable without Android view dependencies.
 */
class MainProgramsPanelManagerTest {

    @Test
    fun `available and expanded - item row shown, strip hidden`() {
        val (contentVisible, stripVisible) =
            MainProgramsPanelManager.resolveVisibility(available = true, collapsed = false)
        assertTrue("expanded: item row visible", contentVisible)
        assertFalse("expanded: strip hidden", stripVisible)
    }

    @Test
    fun `available and collapsed - strip shown, item row hidden`() {
        val (contentVisible, stripVisible) =
            MainProgramsPanelManager.resolveVisibility(available = true, collapsed = true)
        assertFalse("collapsed: item row hidden", contentVisible)
        assertTrue("collapsed: strip visible", stripVisible)
    }

    @Test
    fun `unavailable - both hidden regardless of collapsed`() {
        val (expandedContent, expandedStrip) =
            MainProgramsPanelManager.resolveVisibility(available = false, collapsed = false)
        assertFalse("hidden + expanded: item row hidden", expandedContent)
        assertFalse("hidden + expanded: strip hidden", expandedStrip)

        val (collapsedContent, collapsedStrip) =
            MainProgramsPanelManager.resolveVisibility(available = false, collapsed = true)
        assertFalse("hidden + collapsed: item row hidden", collapsedContent)
        assertFalse("hidden + collapsed: strip hidden", collapsedStrip)
    }

    @Test
    fun `item row and strip are never both shown`() {
        for (available in listOf(true, false)) {
            for (collapsed in listOf(true, false)) {
                val (contentVisible, stripVisible) =
                    MainProgramsPanelManager.resolveVisibility(available, collapsed)
                assertFalse(
                    "available=$available collapsed=$collapsed must not show both",
                    contentVisible && stripVisible
                )
            }
        }
    }
}
