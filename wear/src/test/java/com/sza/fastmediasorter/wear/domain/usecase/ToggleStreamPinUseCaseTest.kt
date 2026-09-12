package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.repository.WearStreamPinsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2497: Tests for ToggleStreamPinUseCase verifying toggle, isPinned, and sending delta.
 */
class ToggleStreamPinUseCaseTest {

    @Test
    fun `toggle unpinned stream pins it and sends delta`() = runBlocking {
        val streamPinsRepo = mockk<WearStreamPinsRepository>(relaxed = true)
        val sendDeltaUseCase = mockk<SendStreamPinsDeltaUseCase>(relaxed = true)
        val useCase = ToggleStreamPinUseCase(streamPinsRepo, sendDeltaUseCase)

        coEvery { streamPinsRepo.setPin("https://test.com/stream", true) } returns Unit

        val result = useCase.toggle("https://test.com/stream", wasPinned = false)

        assertTrue(result)
        coVerify(exactly = 1) { streamPinsRepo.setPin("https://test.com/stream", true) }
        coVerify(exactly = 1) { sendDeltaUseCase() }
    }

    @Test
    fun `toggle pinned stream unpins it and sends delta`() = runBlocking {
        val streamPinsRepo = mockk<WearStreamPinsRepository>(relaxed = true)
        val sendDeltaUseCase = mockk<SendStreamPinsDeltaUseCase>(relaxed = true)
        val useCase = ToggleStreamPinUseCase(streamPinsRepo, sendDeltaUseCase)

        coEvery { streamPinsRepo.setPin("https://test.com/stream", false) } returns Unit

        val result = useCase.toggle("https://test.com/stream", wasPinned = true)

        assertFalse(result)
        coVerify(exactly = 1) { streamPinsRepo.setPin("https://test.com/stream", false) }
        coVerify(exactly = 1) { sendDeltaUseCase() }
    }

    @Test
    fun `isPinned queries repository`() {
        val streamPinsRepo = mockk<WearStreamPinsRepository>()
        val sendDeltaUseCase = mockk<SendStreamPinsDeltaUseCase>()
        val useCase = ToggleStreamPinUseCase(streamPinsRepo, sendDeltaUseCase)

        every { streamPinsRepo.isPinned("https://test.com/stream") } returns true

        assertTrue(useCase.isPinned("https://test.com/stream"))
    }
}
