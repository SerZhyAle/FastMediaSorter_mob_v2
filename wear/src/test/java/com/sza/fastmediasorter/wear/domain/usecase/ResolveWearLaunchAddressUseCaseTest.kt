package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchAddress
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private data class FakeCapabilities(
    override val offersCredentialEntry: Boolean = true,
    override val offersBodySensorDiagnostics: Boolean = true,
    override val locksSystemShade: Boolean = true,
    override val offersHealthFeatures: Boolean = true,
    // S3178: the eight store-boundary answers. Defaulted to the offering build so every test written
    // before the boundary keeps asserting the full product; a test about the store variant pins them.
    override val offersMediaAccess: Boolean = true,
    override val offersVoiceRecording: Boolean = true,
    override val offersRemoteSources: Boolean = true,
    override val offersDeviceDiagnostics: Boolean = true,
    override val offersNearbyDeviceState: Boolean = true,
    override val offersScreenCapture: Boolean = true,
    override val offersContentTransfer: Boolean = true,
    override val offersExternalEntryPoints: Boolean = true,
    // S3362: the ninth answer, defaulted the same way - the offering build keeps the two programs
    // whose screens swallow the dismiss gesture.
    override val offersScreenTakeoverPrograms: Boolean = true,
) : WearRestrictedCapabilities

/**
 * S3362: the three repository collaborators answer nothing on a destination target, so one builder
 * serves every case and a new withholding case is one line instead of a six-line copy.
 */
private fun useCaseWith(capabilities: WearRestrictedCapabilities) = ResolveWearLaunchAddressUseCase(
    // S3368: the deferred dependency is satisfied with a SAM Lazy over the mock; none of these cases
    // reads the sources, so the mock inside is never even created.
    networkSourceRepository = dagger.Lazy { mockk<NetworkSourceRepository>() },
    streamChannelRepository = mockk<WearStreamChannelRepository>(),
    prepareWearStreamPlayback = mockk<PrepareWearStreamPlaybackUseCase>(),
    prepareWearFilePlayback = mockk<PrepareWearFilePlaybackUseCase>(),
    capabilities = capabilities
)

class ResolveWearLaunchAddressUseCaseTest {

    private val useCase = useCaseWith(FakeCapabilities())

    @Test
    fun `every destination resolves to its own screen when offered`() = runTest {
        WearDestinationId.entries.forEach { id ->
            assertEquals(
                "$id is offered on a tile, so it must have somewhere to go",
                WearLaunchAddress.Screen(id),
                useCase(WearLaunchTarget.Destination(id))
            )
        }
    }

    @Test
    fun `restricted health destinations return null when withheld in capabilities`() = runTest {
        val withholdingUseCase = useCaseWith(
            FakeCapabilities(offersBodySensorDiagnostics = false, offersHealthFeatures = false)
        )

        assertNull(withholdingUseCase(WearLaunchTarget.Destination(WearDestinationId.BODY_SENSOR)))
        assertNull(withholdingUseCase(WearLaunchTarget.Destination(WearDestinationId.BLOOD_PRESSURE)))
        assertNull(withholdingUseCase(WearLaunchTarget.Destination(WearDestinationId.MOTION_MONITOR)))
    }

    /**
     * S3362: the three programs the store build gave up, refused at the one place an OUTSIDE caller
     * reaches - a tile cell, a shortcut or a launch intent never passes through a drawn screen.
     */
    @Test
    fun `the screen takeover destinations return null when that capability is withheld`() = runTest {
        val withholdingUseCase = useCaseWith(FakeCapabilities(offersScreenTakeoverPrograms = false))

        assertNull(withholdingUseCase(WearLaunchTarget.Destination(WearDestinationId.WATER_FLASHLIGHT)))
        assertNull(withholdingUseCase(WearLaunchTarget.Destination(WearDestinationId.SOS)))
    }

    @Test
    fun `the clipboard destination returns null when content transfer is withheld`() = runTest {
        val withholdingUseCase = useCaseWith(FakeCapabilities(offersContentTransfer = false))

        assertNull(withholdingUseCase(WearLaunchTarget.Destination(WearDestinationId.CLIPBOARD)))
    }

    @Test
    fun `a pick target names the tile it is picking for`() = runTest {
        val kind = WearTileKind.STREAM

        assertEquals(
            WearLaunchAddress.TileTargetPicker(kind),
            useCase(WearLaunchTarget.Pick(kind))
        )
    }
}
