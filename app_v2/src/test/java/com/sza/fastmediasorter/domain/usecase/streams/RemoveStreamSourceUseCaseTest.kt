package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RemoveStreamSourceUseCaseTest {

    @Test
    fun removingAChannelAlsoClearsItsStoredPassword() = runTest {
        val repository = mockk<StreamSourceRepository>(relaxed = true)
        val passwords = mockk<BroadcastPasswordStore>(relaxed = true)
        val source = mockk<StreamSourceEntity>()
        every { source.id } returns STREAM_ID

        RemoveStreamSourceUseCase(repository, passwords)(source)

        coVerifyOrder {
            repository.remove(source)
            passwords.remove(STREAM_ID)
        }
    }

    private companion object {
        const val STREAM_ID = "stream-1"
    }
}
