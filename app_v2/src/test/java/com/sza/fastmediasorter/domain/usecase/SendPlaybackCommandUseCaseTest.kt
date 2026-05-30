package com.sza.fastmediasorter.domain.usecase

import com.google.android.gms.wearable.Node
import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SendPlaybackCommandUseCaseTest {

    private val repository = mockk<WearableDataLayerRepository>(relaxed = true)
    private val useCase = SendPlaybackCommandUseCase(repository, Gson())

    private fun node(id: String): Node = mockk {
        every { getId() } returns id
    }

    @Test
    fun `returns failure when no watch is connected`() = runTest {
        coEvery { repository.getConnectedNodes() } returns emptyList()

        val result = useCase(WearPlaybackCommand.values().first())

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `sends message to every connected node`() = runTest {
        coEvery { repository.getConnectedNodes() } returns listOf(node("a"), node("b"))

        val result = useCase(WearPlaybackCommand.values().first())

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.sendMessage("a", any(), any()) }
        coVerify(exactly = 1) { repository.sendMessage("b", any(), any()) }
    }

    @Test
    fun `wraps repository exception in Result failure`() = runTest {
        coEvery { repository.getConnectedNodes() } throws RuntimeException("boom")

        val result = useCase(WearPlaybackCommand.values().first())

        assertTrue(result.isFailure)
    }
}
