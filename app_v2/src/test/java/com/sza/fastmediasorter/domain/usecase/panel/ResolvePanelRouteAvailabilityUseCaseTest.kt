package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1856: the availability chain is the single source of truth every launch surface reads, so a route
 * that declares itself always-enabled silently defeats its own settings toggle. These tests pin the
 * calculator's two halves - it reads its toggle, and it names the setting a disabled route opens.
 */
class ResolvePanelRouteAvailabilityUseCaseTest {

    private fun useCaseFor(
        settings: AppSettings,
        wearBridge: Boolean = false,
    ): ResolvePanelRouteAvailabilityUseCase =
        ResolvePanelRouteAvailabilityUseCase(
            context = mockk<Context>(relaxed = true),
            capability = CapabilityAvailability(emptySet()),
            settingsRepository = mockk<SettingsRepository> {
                every { getSettings() } returns flowOf(settings)
            },
            mediaCapabilities = mockk<MediaCapabilities>(relaxed = true) {
                every { supportsWearCompanion } returns wearBridge
            },
            networkMonitorContract = mockk<NetworkMonitorContract>(relaxed = true),
            screenVideoRecordingControllers = emptySet(),
        )

    @Test
    fun `calculator is launchable only while its own toggle is on`() = runBlocking {
        val enabled = useCaseFor(AppSettings(enableCalculator = true))
            .invoke(InternalRouteCatalog.KEY_CALCULATOR)
        assertTrue("an enabled calculator launches", enabled.isLaunchable)

        val disabled = useCaseFor(AppSettings(enableCalculator = false))
            .invoke(InternalRouteCatalog.KEY_CALCULATOR)
        assertFalse("a disabled calculator must not launch", disabled.isLaunchable)
    }

    @Test
    fun `a disabled calculator stays compiled in rather than vanishing`() = runBlocking {
        val disabled = useCaseFor(AppSettings(enableCalculator = false))
            .invoke(InternalRouteCatalog.KEY_CALCULATOR)
        // The picker hides what is absent from the build and only flags what the user switched off,
        // so collapsing "off" into "not built" would remove the route from the picker entirely.
        assertTrue("the calculator ships in every flavor", disabled.availableInBuild)
    }

    @Test
    fun `the wear companion needs both the watch bridge and its own switch`() = runBlocking {
        val both = useCaseFor(AppSettings(enableWearCompanion = true), wearBridge = true)
            .invoke(InternalRouteCatalog.KEY_WEAR_COMPANION)
        assertTrue("bridge plus switch launches", both.isLaunchable)

        val switchOff = useCaseFor(AppSettings(enableWearCompanion = false), wearBridge = true)
            .invoke(InternalRouteCatalog.KEY_WEAR_COMPANION)
        assertFalse("a switched-off companion must not launch", switchOff.isLaunchable)
        assertTrue("the build still carries it, so the picker may offer it", switchOff.availableInBuild)
    }

    @Test
    fun `a build without the watch bridge does not carry the companion at all`() = runBlocking {
        val noBridge = useCaseFor(AppSettings(enableWearCompanion = true), wearBridge = false)
            .invoke(InternalRouteCatalog.KEY_WEAR_COMPANION)
        // A stored switch survives a move to lite, photos or vr, so reading it alone would offer a tile
        // that nothing in the build can open.
        assertFalse("no bridge means the route is absent from the build", noBridge.availableInBuild)
        assertFalse("and it must not launch whatever the stored switch says", noBridge.isLaunchable)
    }

    @Test
    fun `the wear companion route names the setting a disabled tile opens`() {
        val route = InternalRouteCatalog.byKey(InternalRouteCatalog.KEY_WEAR_COMPANION)
        assertNotNull("the wear companion route is missing from the catalog", route)
        assertNotNull("a disabled route dead-ends unless it names its setting", route?.settingsIntent)
    }

    @Test
    fun `the calculator route names the setting a disabled tile opens`() {
        val route = InternalRouteCatalog.byKey(InternalRouteCatalog.KEY_CALCULATOR)
        assertNotNull("the calculator route is missing from the catalog", route)
        // Both launch paths fall through to a refusal when a compiled-but-disabled route declares no
        // settings intent, which would turn the tile from wrongly-launching into silently inert.
        assertNotNull("a disabled route dead-ends unless it names its setting", route?.settingsIntent)
    }
}
