package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminSession
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeServerAdminRepositoryCancellationTest {
    @Test
    fun `cancellation during return to caller closes unclaimed session`() = runTest {
        val session = mockk<DeviceExchangeAdminSession>(relaxed = true)
        val client = mockk<DeviceExchangeAdminClient>()
        coEvery { client.signIn(any(), any()) } coAnswers {
            currentCoroutineContext().cancel()
            session
        }
        val repository = ExchangeServerAdminRepository(client, StandardTestDispatcher(testScheduler))
        val entry = DeviceExchangeAccountEntry("server.example", PORT, "operator", "device", null)
        val attempt = launch { repository.signIn(entry, charArrayOf('p')) }
        advanceUntilIdle()
        assertTrue(attempt.isCancelled)
        verify(exactly = 1) { session.close() }
    }

    private companion object {
        const val PORT = 9010
    }
}
