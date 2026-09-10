package com.sza.fastmediasorter.widget.registry

import com.sza.fastmediasorter.core.panel.SubProgramAccentCatalog
import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2889: pins both directions of the widget join.
 *
 * Forwards, because a paired widget silently resolving to no colour is the divergence this ticket exists
 * to remove. Backwards, because a mechanical check that does not know the state-carrying set would demand
 * an accent for a pause button - and because an exclusion list nobody counts is a place to quietly hide a
 * widget rather than a decision.
 */
class HomeWidgetAccentTest {

    @Test
    fun `every paired widget that is not state-carrying resolves to a tone`() {
        val missing = SubProgramCatalog.all()
            .mapNotNull { it.widgetKey }
            .filterNot { it in HomeWidgetAccent.stateCarryingWidgets }
            .filter { HomeWidgetAccent.accentResFor(it) == null }

        assertEquals("paired widgets with no tone: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `a paired widget wears its own sub-program's tone`() {
        val mismatched = SubProgramCatalog.all()
            .filter { it.widgetKey != null }
            .filterNot { it.widgetKey in HomeWidgetAccent.stateCarryingWidgets }
            .filter { HomeWidgetAccent.accentResFor(it.widgetKey!!) != SubProgramAccentCatalog.accentFor(it.routeKey) }
            .map { it.widgetKey }

        assertEquals("widgets wearing another program's tone: $mismatched", emptyList<String?>(), mismatched)
    }

    @Test
    fun `a state-carrying widget is left alone`() {
        HomeWidgetAccent.stateCarryingWidgets.forEach {
            assertNull("$it must keep its own colour", HomeWidgetAccent.accentResFor(it))
        }
    }

    @Test
    fun `the excluded set is pinned so removing an exclusion is deliberate`() {
        assertEquals(
            setOf("stopwatch", "network_monitor", "quick_audio_recorder"),
            HomeWidgetAccent.stateCarryingWidgets,
        )
    }

    @Test
    fun `every excluded widget is actually paired, so the list cannot rot`() {
        val paired = SubProgramCatalog.all().mapNotNull { it.widgetKey }.toSet()
        val strays = HomeWidgetAccent.stateCarryingWidgets.filterNot { it in paired }

        assertEquals("excluded widget keys nothing pairs any more: $strays", emptyList<String>(), strays)
    }

    @Test
    fun `an unknown widget key has no tone`() {
        assertNull(HomeWidgetAccent.accentResFor("not_a_widget"))
    }
}
