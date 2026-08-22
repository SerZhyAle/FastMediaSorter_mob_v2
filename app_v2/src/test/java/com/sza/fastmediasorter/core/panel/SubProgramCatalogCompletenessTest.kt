package com.sza.fastmediasorter.core.panel

import android.content.Context
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.widget.registry.HomeWidgetCatalog
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1736 ADR-4: the registry is only worth having while a surface that stops resolving one of its
 * entries fails here rather than on a user's device.
 *
 * Every assertion names the offending `routeKey`. The failure this suite exists to catch is "a
 * program is present on one surface and silently absent from another", and a bare boolean would
 * report that a program went missing without saying which one.
 */
class SubProgramCatalogCompletenessTest {

    /**
     * Permissive on purpose: these cases ask whether a branch CLAIMS a route, never what that branch
     * answers, so a capability left false would still exercise the same branch.
     */
    private val resolver = ResolvePanelRouteAvailabilityUseCase(
        context = mockk<Context>(relaxed = true),
        capability = CapabilityAvailability(emptySet()),
        settingsRepository = mockk<SettingsRepository>(relaxed = true),
        mediaCapabilities = mockk<MediaCapabilities>(relaxed = true),
        networkMonitorContract = mockk<NetworkMonitorContract>(relaxed = true),
        screenVideoRecordingControllers = emptySet(),
    )

    /**
     * `entries()` is the ungated static table, deliberately not `availableEntries()`: the pairing is a
     * property of the catalog, not of what this particular build or settings state happens to offer.
     */
    private val widgetCatalog = HomeWidgetCatalog(
        context = mockk<Context>(relaxed = true),
        settingsRepository = mockk<SettingsRepository>(relaxed = true),
    )

    @Test
    fun `every entry names a route the route catalog knows`() {
        SubProgramCatalog.all().forEach { entry ->
            assertNotNull(
                "sub-program '${entry.routeKey}' has no InternalRouteCatalog route",
                InternalRouteCatalog.byKey(entry.routeKey),
            )
        }
    }

    @Test
    fun `every entry is claimed by a branch of the availability chain`() {
        SubProgramCatalog.all().forEach { entry ->
            assertNotNull(
                "sub-program '${entry.routeKey}' is not claimed by the availability chain",
                resolver.resolveOrNull(entry.routeKey, AppSettings()),
            )
        }
    }

    @Test
    fun `every WIDGET entry pairs with a widget the widget catalog offers`() {
        val gadgetKeys = widgetCatalog.entries().map { it.gadgetKey }.toSet()
        SubProgramCatalog.forSurface(SubProgramSurface.WIDGET).forEach { entry ->
            val widgetKey = entry.widgetKey
            assertNotNull("sub-program '${entry.routeKey}' declares WIDGET without a widgetKey", widgetKey)
            assertTrue(
                "sub-program '${entry.routeKey}' pairs with unknown widget '$widgetKey'",
                widgetKey in gadgetKeys,
            )
        }
    }

    @Test
    fun `no entry pairs with a widget without declaring the WIDGET surface`() {
        SubProgramCatalog.all().filter { it.widgetKey != null }.forEach { entry ->
            // The pairing is what the widget picker reads, so a widgetKey the surface set does not
            // declare is a program the picker can offer and the completeness test never checks.
            assertTrue(
                "sub-program '${entry.routeKey}' sets widgetKey but omits SubProgramSurface.WIDGET",
                SubProgramSurface.WIDGET in entry.surfaces,
            )
        }
    }

    @Test
    fun `every PROGRAMS_MENU entry has a route that can be opened`() {
        assertEveryRouteIsOpenable(SubProgramSurface.PROGRAMS_MENU)
    }

    @Test
    fun `every QUICK_ACCESS_PANEL entry has a route that can be opened`() {
        assertEveryRouteIsOpenable(SubProgramSurface.QUICK_ACCESS_PANEL)
    }

    @Test
    fun `every LAUNCHER_SHORTCUT entry has a route that can be opened`() {
        assertEveryRouteIsOpenable(SubProgramSurface.LAUNCHER_SHORTCUT)
    }

    @Test
    fun `order values are unique and no entry declares an empty surface set`() {
        val orders = SubProgramCatalog.all().map { it.order }
        assertEquals("two sub-programs share one order value: $orders", orders.size, orders.toSet().size)
        SubProgramCatalog.all().forEach { entry ->
            assertTrue(
                "sub-program '${entry.routeKey}' is fit for no surface, so nothing can ever show it",
                entry.surfaces.isNotEmpty(),
            )
        }
    }

    private fun assertEveryRouteIsOpenable(surface: SubProgramSurface) {
        SubProgramCatalog.forSurface(surface).forEach { entry ->
            val route = InternalRouteCatalog.byKey(entry.routeKey)
            assertNotNull("sub-program '${entry.routeKey}' is fit for $surface but has no route", route)
            assertNotNull("route '${entry.routeKey}' has no intent builder, so $surface cannot open it", route?.intent)
        }
    }
}
