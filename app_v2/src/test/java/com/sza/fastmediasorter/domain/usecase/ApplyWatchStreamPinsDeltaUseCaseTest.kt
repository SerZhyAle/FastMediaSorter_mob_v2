package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.model.WearStreamPinDeltaItem
import com.sza.fastmediasorter.domain.model.WearStreamPinsDeltaPayload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * S2497: Unit tests for ApplyWatchStreamPinsDeltaUseCase on phone.
 */
class ApplyWatchStreamPinsDeltaUseCaseTest {

    @Test
    fun `apply watch stream pins delta updates repository`() = runBlocking {
        val streamSourceRepo = mockk<StreamSourceRepository>(relaxed = true)
        val useCase = ApplyWatchStreamPinsDeltaUseCase(streamSourceRepo)

        val delta1 = WearStreamPinDeltaItem(urlOrIdentity = "https://stream.example.com/one", isPinned = true, changedAt = 1000L)
        val delta2 = WearStreamPinDeltaItem(urlOrIdentity = "https://stream.example.com/two", isPinned = false, changedAt = 2000L)
        val payload = WearStreamPinsDeltaPayload(items = listOf(delta1, delta2))

        coEvery { streamSourceRepo.pinByIdentity(any()) } returns Unit
        coEvery { streamSourceRepo.unpinByIdentity(any()) } returns Unit

        useCase(payload)

        coVerify(exactly = 1) { streamSourceRepo.pinByIdentity("web://stream.example.com/one") }
        coVerify(exactly = 1) { streamSourceRepo.unpinByIdentity("web://stream.example.com/two") }
    }
}

