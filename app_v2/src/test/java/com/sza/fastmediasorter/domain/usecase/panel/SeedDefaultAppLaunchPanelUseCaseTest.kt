package com.sza.fastmediasorter.domain.usecase.panel

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import com.sza.fastmediasorter.core.panel.SubProgramSurface
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.model.panel.AppLaunchPanelRouteTarget
import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1736: the seed is the first surface to take its composition from the registry, and strategic
 * 11.3 requires already-saved tiles to keep resolving afterwards.
 *
 * The wire-format case asserts against literal `fn:` strings rather than a round trip through the
 * codec on purpose: a codec changed on both sides would still round-trip cleanly and prove nothing
 * about what is already stored on a user's device.
 */
class SeedDefaultAppLaunchPanelUseCaseTest {

    private val captured = mutableListOf<AppLaunchPanelTile>()

    private fun seedWith(unavailable: Set<String> = emptySet()): SeedDefaultAppLaunchPanelUseCase {
        val repository = mockk<AppLaunchPanelRepository>(relaxed = true) {
            coEvery { count() } returns 0
            coEvery { setTile(capture(captured)) } returns Unit
        }
        val availability = mockk<ResolvePanelRouteAvailabilityUseCase> {
            coEvery { this@mockk(any()) } answers {
                val key = firstArg<String>()
                ResolvePanelRouteAvailabilityUseCase.Availability(
                    availableInBuild = key !in unavailable,
                    enabledAtRuntime = true,
                )
            }
        }
        return SeedDefaultAppLaunchPanelUseCase(
            repository = repository,
            resourceRepository = mockk<ResourceRepository> { coEvery { getAllResourcesSync() } returns emptyList() },
            resolveRouteAvailability = availability,
        )
    }

    /** The feature route keys the run actually wrote, in the slot order it wrote them. */
    private fun seededFeatureKeys(): List<String> = captured
        .filter { it.type == AppLaunchPanelTileType.INTERNAL_ROUTE }
        .sortedBy { it.slotIndex }
        .mapNotNull { AppLaunchPanelRouteTarget.decode(it.targetId) as? AppLaunchPanelRouteTarget.Feature }
        .map { it.routeKey }

    private fun panelRegistryKeys(): List<String> =
        SubProgramCatalog.forSurface(SubProgramSurface.QUICK_ACCESS_PANEL).map { it.routeKey }

    @Test
    fun `seed emits registry entries in registry order`() = runBlocking {
        seedWith().invoke()

        val seeded = seededFeatureKeys()
        assertTrue("the seed wrote no feature tile at all", seeded.isNotEmpty())
        assertEquals(
            "seeded features are not the registry's order",
            panelRegistryKeys().take(seeded.size),
            seeded,
        )
    }

    @Test
    fun `an unavailable entry is skipped and the next one shifts up`() = runBlocking {
        val skipped = panelRegistryKeys().first()
        val full = seedWith().invoke().let { seededFeatureKeys() }
        captured.clear()
        seedWith(unavailable = setOf(skipped)).invoke()
        val reduced = seededFeatureKeys()

        assertFalse("the unavailable route was seeded anyway", skipped in reduced)
        assertEquals("skipping left a hole instead of shifting up", full.size, reduced.size)
        assertEquals(
            "the routes after the skipped one did not shift up",
            panelRegistryKeys().filterNot { it == skipped }.take(reduced.size),
            reduced,
        )
        assertEquals("slots are not contiguous from zero", captured.indices.toList(), captured.map { it.slotIndex })
    }

    @Test
    fun `a target stored before this ticket still decodes to the same route`() = runBlocking {
        assertEquals(
            AppLaunchPanelRouteTarget.Feature(InternalRouteCatalog.KEY_CALCULATOR),
            AppLaunchPanelRouteTarget.decode("fn:calculator"),
        )
        assertEquals(
            AppLaunchPanelRouteTarget.Feature(InternalRouteCatalog.KEY_QUICK_VOICE),
            AppLaunchPanelRouteTarget.decode("fn:quick_voice"),
        )
        assertEquals(
            AppLaunchPanelRouteTarget.Feature(InternalRouteCatalog.KEY_SCREEN_RECORDING),
            AppLaunchPanelRouteTarget.decode("fn:screen_recording"),
        )

        seedWith().invoke()
        val written = captured.mapNotNull { it.targetId }.filter { it.startsWith("fn:") }
        assertTrue("the seed stopped writing the fn: vocabulary", written.isNotEmpty())
        assertTrue(
            "the seed wrote a target the pre-ticket decoder cannot read",
            written.all { AppLaunchPanelRouteTarget.decode(it) is AppLaunchPanelRouteTarget.Feature },
        )
    }
}
