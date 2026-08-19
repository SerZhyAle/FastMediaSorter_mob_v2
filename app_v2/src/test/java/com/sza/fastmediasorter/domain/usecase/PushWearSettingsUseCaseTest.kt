package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PushWearSettingsUseCaseTest {

    private val repository = mockk<WearableDataLayerRepository>()
    private val gson = Gson()
    private lateinit var useCase: PushWearSettingsUseCase

    private val payload = WearSettingsPayload(
        audioEnabled = true,
        videoEnabled = false,
        imagesEnabled = true,
        slideshowEnabled = true,
        slideshowIntervalSeconds = 7,
        slideshowWaitForFinish = false,
        downloadAlbumArt = true,
        appLanguage = "ru"
    )

    @Before
    fun setup() {
        useCase = PushWearSettingsUseCase(repository, gson)
    }

    @Test
    fun `failure when no watch is connected`() = runTest {
        coEvery { repository.getConnectedNodes() } returns emptyList()

        val result = useCase(payload)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.putEnvelopeDataItem(any(), any()) }
    }

    @Test
    fun `connected node pushes envelope with settings event type and json bytes`() = runTest {
        coEvery { repository.getConnectedNodes() } returns listOf(WearNode("node-1", "Watch"))
        val pathSlot = slot<String>()
        val envelopeSlot = slot<WearEventEnvelope>()
        coEvery { repository.putEnvelopeDataItem(capture(pathSlot), capture(envelopeSlot)) } just Runs

        val result = useCase(payload)

        assertTrue(result.isSuccess)
        assertEquals(WearDataLayerPaths.SETTINGS_PUSH, pathSlot.captured)
        assertEquals(WearDataLayerPaths.EVENT_SETTINGS, envelopeSlot.captured.eventType)
        val decoded = gson.fromJson(
            String(envelopeSlot.captured.data, Charsets.UTF_8),
            WearSettingsPayload::class.java,
        )
        assertEquals(payload, decoded)
    }
}
