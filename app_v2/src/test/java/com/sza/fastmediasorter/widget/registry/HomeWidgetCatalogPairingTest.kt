package com.sza.fastmediasorter.widget.registry

import android.content.Context
import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1736: the registry pairs a sub-program with the widget that launches it, and that pairing is what
 * the picker reads. Before this suite it was held by a comment, which is exactly how a pairing drifts.
 *
 * The catalog is read through `entries()`, the ungated static table, never `availableEntries()`: a
 * pairing is a property of the catalog itself, not of what this build or this settings state offers.
 */
class HomeWidgetCatalogPairingTest {

    private val catalog = HomeWidgetCatalog(
        context = mockk<Context>(relaxed = true),
        settingsRepository = mockk<SettingsRepository>(relaxed = true),
    )

    private fun gadgetKeys(): Set<String> = catalog.entries().map { it.gadgetKey }.toSet()

    @Test
    fun `every paired widget key resolves to a widget the catalog declares`() {
        val known = gadgetKeys()
        SubProgramCatalog.all().forEach { entry ->
            val widgetKey = entry.widgetKey ?: return@forEach
            assertTrue(
                "sub-program '${entry.routeKey}' pairs with widget '$widgetKey', which no catalog entry declares",
                widgetKey in known,
            )
        }
    }

    @Test
    fun `no two sub-programs claim the same widget`() {
        val claimed = SubProgramCatalog.all().mapNotNull { it.widgetKey }
        val duplicated = claimed.groupBy { it }.filterValues { it.size > 1 }.keys
        assertEquals(
            "one widget is claimed by several sub-programs, so the picker cannot say which it launches: $duplicated",
            emptySet<String>(),
            duplicated,
        )
    }

    @Test
    fun `stream launch is claimed by no sub-program`() {
        val claimant = SubProgramCatalog.all().firstOrNull { it.widgetKey == STREAM_LAUNCH_KEY }
        assertEquals(
            "ADR-6: '$STREAM_LAUNCH_KEY' opens one configured stream while route 'streams' opens the list, " +
                "so pairing them by the spelling of their keys would silently change what an already-placed " +
                "widget opens. The exclusion is deliberate - claimed here by '${claimant?.routeKey}'.",
            null,
            claimant,
        )
    }

    private companion object {
        const val STREAM_LAUNCH_KEY = "stream_launch"
    }
}
