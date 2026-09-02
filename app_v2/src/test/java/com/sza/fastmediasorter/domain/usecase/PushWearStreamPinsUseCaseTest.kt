package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearStreamPinsPayload
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.streams.ObservePinnedStreamSourcesUseCase
import com.sza.fastmediasorter.service.WearDataLayerPaths
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S2149 / S2366: tests stream pin publishing and gating on enableWearCompanion.
 */
class PushWearStreamPinsUseCaseTest {

    private val wearRepository = mockk<WearableDataLayerRepository>()
    private val observePinned = mockk<ObservePinnedStreamSourcesUseCase>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val gson = Gson()

    @Before
    fun setUp() {
        every { settingsRepository.getSettings() } returns flowOf(AppSettings(enableWearCompanion = true))
    }

    private fun useCase() = PushWearStreamPinsUseCase(wearRepository, gson, observePinned, settingsRepository)

    private fun source(id: String, url: String, identityKey: String = "") = StreamSourceEntity(
        id = id,
        url = url,
        title = id,
        mediaKind = "VIDEO",
        sourceOrigin = "MANUAL",
        sortIndex = 0,
        pinned = true,
        addedAt = 0L,
        identityKey = identityKey
    )

    private fun captureEnvelope(): Pair<CapturingSlot<String>, CapturingSlot<WearEventEnvelope>> {
        val pathSlot = slot<String>()
        val envelopeSlot = slot<WearEventEnvelope>()
        coEvery { wearRepository.getConnectedNodes() } returns listOf(WearNode("node-1", "Watch"))
        coEvery { wearRepository.putEnvelopeDataItem(capture(pathSlot), capture(envelopeSlot)) } just Runs
        return pathSlot to envelopeSlot
    }

    private fun decode(envelope: WearEventEnvelope): WearStreamPinsPayload =
        gson.fromJson(String(envelope.data, Charsets.UTF_8), WearStreamPinsPayload::class.java)

    @Test
    fun `pinned sources publish their identities in pin order`() = runTest {
        every { observePinned() } returns flowOf(
            listOf(
                source("a", "https://host.tv/one", identityKey = "web://host.tv/one"),
                source("b", "https://host.tv/two", identityKey = "web://host.tv/two")
            )
        )
        val (pathSlot, envelopeSlot) = captureEnvelope()

        val result = useCase()()

        assertTrue(result.isSuccess)
        assertEquals(WearDataLayerPaths.STREAM_PINS, pathSlot.captured)
        assertEquals(WearDataLayerPaths.EVENT_STREAM_PINS, envelopeSlot.captured.eventType)
        assertEquals(
            listOf("web://host.tv/one", "web://host.tv/two"),
            decode(envelopeSlot.captured).identities
        )
    }

    @Test
    fun `empty pinned set still publishes, carrying an empty list`() = runTest {
        every { observePinned() } returns flowOf(emptyList())
        val (_, envelopeSlot) = captureEnvelope()

        val result = useCase()()

        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), decode(envelopeSlot.captured).identities)
    }

    @Test
    fun `row with an empty identity column falls back to deriving from its url`() = runTest {
        every { observePinned() } returns flowOf(listOf(source("a", "http://Host.TV/one/")))
        val (_, envelopeSlot) = captureEnvelope()

        useCase()()

        assertEquals(listOf("web://host.tv/one"), decode(envelopeSlot.captured).identities)
    }

    @Test
    fun `no connected watch publishes nothing`() = runTest {
        every { observePinned() } returns flowOf(listOf(source("a", "https://host.tv/one")))
        coEvery { wearRepository.getConnectedNodes() } returns emptyList()

        val result = useCase()()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { wearRepository.putEnvelopeDataItem(any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `observeAndPush does nothing when enableWearCompanion is false`() = runTest {
        every { settingsRepository.getSettings() } returns flowOf(AppSettings(enableWearCompanion = false))
        every { observePinned() } returns flowOf(listOf(source("a", "https://host.tv/one")))

        val job = useCase().observeAndPush(this)
        advanceUntilIdle()

        coVerify(exactly = 0) { wearRepository.putEnvelopeDataItem(any(), any()) }
        job.cancel()
    }
}

