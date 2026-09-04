package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSyncLeg
import com.sza.fastmediasorter.domain.model.WearSyncLegResult
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SLIDESHOW_INTERVAL_SECONDS = 5

/**
 * S2484: the orchestrator's contract is partial success - one leg failing must not hide the other's
 * result, which is exactly what a single boolean verdict would have done.
 */
class SyncWithWatchUseCaseTest {

    private lateinit var wearableRepository: WearableDataLayerRepository
    private lateinit var sendResources: SendResourcesToWatchUseCase
    private lateinit var pushSettings: PushWearSettingsUseCase
    private lateinit var useCase: SyncWithWatchUseCase

    private val settings = WearSettingsPayload(
        audioEnabled = true,
        videoEnabled = true,
        imagesEnabled = true,
        slideshowEnabled = false,
        slideshowIntervalSeconds = SLIDESHOW_INTERVAL_SECONDS,
        downloadAlbumArt = false
    )

    @Before
    fun setup() {
        wearableRepository = mockk()
        sendResources = mockk()
        pushSettings = mockk()
        useCase = SyncWithWatchUseCase(wearableRepository, sendResources, pushSettings)
        coEvery { wearableRepository.getConnectedNodes() } returns listOf(WearNode("node-1", "Watch"))
    }

    @Test
    fun `no connected watch fails every leg without calling either use case`() = runTest {
        coEvery { wearableRepository.getConnectedNodes() } returns emptyList()

        val outcome = useCase(settings)

        assertFalse(outcome.allSucceeded)
        assertEquals(WearSyncLeg.entries.size, outcome.failedLegs.size)
    }

    @Test
    fun `both legs succeeding reports the exchange as complete`() = runTest {
        coEvery { sendResources() } returns Result.success(SendResult(sent = 3, skipped = 0))
        coEvery { pushSettings(settings) } returns Result.success(Unit)

        val outcome = useCase(settings)

        assertTrue(outcome.allSucceeded)
        assertEquals(
            WearSyncLegResult.Succeeded(3),
            outcome.legs[WearSyncLeg.RESOURCES_OUT]
        )
    }

    @Test
    fun `an empty resource selection is not a failure and does not stop the settings leg`() = runTest {
        coEvery { sendResources() } returns Result.success(SendResult(sent = 0, skipped = 0))
        coEvery { pushSettings(settings) } returns Result.success(Unit)

        val outcome = useCase(settings)

        assertEquals(WearSyncLegResult.NothingToSend, outcome.legs[WearSyncLeg.RESOURCES_OUT])
        assertTrue(outcome.failedLegs.isEmpty())
    }

    @Test
    fun `a failing settings leg leaves the resource leg's success intact`() = runTest {
        coEvery { sendResources() } returns Result.success(SendResult(sent = 2, skipped = 0))
        coEvery { pushSettings(settings) } returns Result.failure(IllegalStateException("rejected"))

        val outcome = useCase(settings)

        assertEquals(listOf(WearSyncLeg.SETTINGS_OUT), outcome.failedLegs)
        assertEquals(
            WearSyncLegResult.Succeeded(2),
            outcome.legs[WearSyncLeg.RESOURCES_OUT]
        )
    }
}
