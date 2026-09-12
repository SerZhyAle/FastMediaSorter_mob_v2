package com.sza.fastmediasorter.core.panel

import android.content.Context
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.launcher.LauncherScreenClass
import com.sza.fastmediasorter.core.launcher.LauncherStarterSets
import com.sza.fastmediasorter.core.launcher.LauncherStarterSets.StarterResources
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.ui.main.helpers.MainProgramsMenuCoordinator
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

    /**
     * S2673: the programs menu draws an entry from the registry plus a presentation row holding the
     * label, the icon and the menu item id the registry deliberately does not store (ADR-1). An entry
     * whose row is missing is silently skipped at run time, which is the "present on one surface,
     * absent from another" failure ADR-4 built this suite to catch.
     */
    @Test
    fun `every PROGRAMS_MENU entry can be drawn by the programs menu`() {
        SubProgramCatalog.forSurface(SubProgramSurface.PROGRAMS_MENU).forEach { entry ->
            assertTrue(
                "sub-program '${entry.routeKey}' is fit for PROGRAMS_MENU but the menu has no " +
                    "label/icon/id row for it",
                entry.routeKey in MainProgramsMenuCoordinator.PRESENTABLE_ROUTE_KEYS,
            )
        }
    }

    /**
     * S2675: the reverse of the assertion above, and the direction the suite lacked. A presentation
     * row added without a registry entry is a menu item no other surface can ever learn about, which
     * is the divergence the registry exists to remove (S1736 §2 goal 4).
     *
     * The four non-registry menu items - streams, VR Cinema, the quick-launch panel and broadcast -
     * are added by their own `popup.menu.add` calls around the registry loop and never enter
     * [MainProgramsMenuCoordinator.PRESENTATION], so they fall outside this assertion by
     * construction rather than by an exception list.
     */
    @Test
    fun `every programs-menu presentation row belongs to a registry entry`() {
        val menuEntries = SubProgramCatalog
            .forSurface(SubProgramSurface.PROGRAMS_MENU)
            .map { it.routeKey }
            .toSet()
        MainProgramsMenuCoordinator.PRESENTABLE_ROUTE_KEYS.forEach { routeKey ->
            assertTrue(
                "route '$routeKey' has a programs-menu label/icon/id row but no registry entry " +
                    "fit for PROGRAMS_MENU",
                routeKey in menuEntries,
            )
        }
    }

    /**
     * S2675: the same reverse direction for the starter desktop, which is where the gap was found.
     * `fn:streams` was seeded onto every desk with no registry entry behind it, and nothing failed
     * for the whole interval until S2664 derived the section from the registry and the cell silently
     * disappeared.
     *
     * Streams and favourites are the seeded feature cells outside the registry. Both are parts
     * of the main application rather than sub-programs (S1736 §2 Non-goals). Streams is seeded by
     * `LauncherStarterSets.commonFeatures` (it opens the streams screen), while favourites is
     * placed by hand in `LauncherStarterSets.commonTail()` - neither has a registry entry, so
     * both are named in `NON_REGISTRY_FEATURE_CELLS`.
     */
    @Test
    fun `every seeded feature cell belongs to a registry entry or a named exception`() {
        val shortcutEntries = SubProgramCatalog
            .forSurface(SubProgramSurface.LAUNCHER_SHORTCUT)
            .map { it.routeKey }
            .toSet()
        val everyRouteLaunchable = InternalRouteCatalog.all().associate { it.key to true }
        DeviceProfileType.entries.forEach { profile ->
            LauncherStarterSets
                .itemsFor(
                    profile = profile,
                    resources = StarterResources(),
                    routeLaunchable = everyRouteLaunchable,
                    installedPackages = emptySet(),
                    screenClass = MEDIUM_WIDE,
                )
                .mapNotNull { (LauncherCellCommand.decode(it.target) as? LauncherCellCommand.Feature)?.routeKey }
                .forEach { routeKey ->
                    assertTrue(
                        "$profile seeds feature cell '$routeKey', which is neither a registry entry " +
                            "fit for LAUNCHER_SHORTCUT nor a named non-registry exception",
                        routeKey in shortcutEntries || routeKey in NON_REGISTRY_FEATURE_CELLS,
                    )
                }
        }
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

    private companion object {

        /**
         * The screen class the desk assertions seed against - the pair the pre-S2309 hardcoded layout
         * was written for, so a shape axis this suite does not care about changes no membership.
         */
        private val MEDIUM_WIDE =
            LauncherScreenClass(LauncherScreenClass.Size.MEDIUM, LauncherScreenClass.Shape.WIDE)

        /**
         * S2675: the feature cells seeded by hand, each a part of the main app rather than a program.
         *
         * Streams joins favourites for the same reason: the desk seeds it through its own curated
         * section (`LauncherStarterSets.commonFeatures`), not through a registry entry - the streams
         * ROUTE opens the screen while a registry entry would have to mean one configured stream.
         */
        private val NON_REGISTRY_FEATURE_CELLS = setOf(
            InternalRouteCatalog.KEY_FAVORITES,
            InternalRouteCatalog.KEY_STREAMS,
        )
    }

    private fun assertEveryRouteIsOpenable(surface: SubProgramSurface) {
        SubProgramCatalog.forSurface(surface).forEach { entry ->
            val route = InternalRouteCatalog.byKey(entry.routeKey)
            assertNotNull("sub-program '${entry.routeKey}' is fit for $surface but has no route", route)
            assertNotNull("route '${entry.routeKey}' has no intent builder, so $surface cannot open it", route?.intent)
        }
    }
}
