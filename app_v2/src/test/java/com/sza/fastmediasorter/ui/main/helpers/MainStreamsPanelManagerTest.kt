package com.sza.fastmediasorter.ui.main.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0808: unit tests for [MainStreamsPanelManager.resolveVisibility] - the pure decision behind the
 * expanded(content row) <-> collapsed(strip) toggle of the streams panel. Mirrors the programs-panel
 * collapse resolver (MainProgramsPanelManagerTest) so the "root owns availability" precedence and the
 * "never both shown" invariant are testable without Android view dependencies.
 *
 * [MainStreamsPanelManager] carries Media3's @UnstableApi, but that is a lint annotation (not
 * @RequiresOptIn), so referencing the pure companion resolver needs no opt-in here.
 */
class MainStreamsPanelManagerTest {

    @Test
    fun `available and expanded - content row shown, strip hidden`() {
        val (contentVisible, stripVisible) =
            MainStreamsPanelManager.resolveVisibility(available = true, collapsed = false)
        assertTrue("expanded: content row visible", contentVisible)
        assertFalse("expanded: strip hidden", stripVisible)
    }

    @Test
    fun `available and collapsed - strip shown, content row hidden`() {
        val (contentVisible, stripVisible) =
            MainStreamsPanelManager.resolveVisibility(available = true, collapsed = true)
        assertFalse("collapsed: content row hidden", contentVisible)
        assertTrue("collapsed: strip visible", stripVisible)
    }

    @Test
    fun `unavailable - both hidden regardless of collapsed`() {
        val (expandedContent, expandedStrip) =
            MainStreamsPanelManager.resolveVisibility(available = false, collapsed = false)
        assertFalse("hidden + expanded: content row hidden", expandedContent)
        assertFalse("hidden + expanded: strip hidden", expandedStrip)

        val (collapsedContent, collapsedStrip) =
            MainStreamsPanelManager.resolveVisibility(available = false, collapsed = true)
        assertFalse("hidden + collapsed: content row hidden", collapsedContent)
        assertFalse("hidden + collapsed: strip hidden", collapsedStrip)
    }

    @Test
    fun `content row and strip are never both shown`() {
        for (available in listOf(true, false)) {
            for (collapsed in listOf(true, false)) {
                val (contentVisible, stripVisible) =
                    MainStreamsPanelManager.resolveVisibility(available, collapsed)
                assertFalse(
                    "available=$available collapsed=$collapsed must not show both",
                    contentVisible && stripVisible
                )
            }
        }
    }
}
