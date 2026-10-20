package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.core.network.ExchangeNetworkEvidenceReader
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminReplyException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminSession
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminSessionLapsedException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeRefusedException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeWireException
import com.sza.fastmediasorter.data.repository.ExchangeServerAdminRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeAdminOutcome
import com.sza.fastmediasorter.domain.model.ExchangeAdminReplyCode
import com.sza.fastmediasorter.domain.model.ExchangeAdminSignIn
import com.sza.fastmediasorter.domain.model.ExchangeCheckEvidence
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AdministerExchangeServerUseCaseTest {
    private val repository = mockk<ExchangeServerAdminRepository>()
    private val network = mockk<ExchangeNetworkEvidenceReader> {
        every { read(any()) } returns ExchangeCheckEvidence()
    }
    private val useCase = AdministerExchangeServerUseCase(repository, DiagnoseExchangeFailureUseCase(network))
    private val session = mockk<DeviceExchangeAdminSession>()
    private val entry = DeviceExchangeAccountEntry("server.example", PORT, "operator", "device", null)

    @Test
    fun `sign in preserves each contract refusal`() = runTest {
        val reasons = mapOf(
            "admin-disabled" to ExchangeMessage.ADMIN_DISABLED,
            "bad-credentials" to ExchangeMessage.BAD_CREDENTIALS,
            "rate-limited" to ExchangeMessage.RATE_LIMITED,
            "capacity" to ExchangeMessage.CAPACITY,
            "tls-required" to ExchangeMessage.TLS_REQUIRED,
        )
        for ((reason, message) in reasons) {
            coEvery { repository.signIn(entry, any()) } throws DeviceExchangeRefusedException(reason)
            val outcome = useCase.signIn(entry, charArrayOf()) as ExchangeAdminSignIn.Refused
            assertEquals(message, outcome.diagnosis.message)
        }
    }

    @Test
    fun `wire failure offers unanswered diagnosis at sign in and during operations`() = runTest {
        coEvery { repository.signIn(entry, any()) } throws DeviceExchangeWireException("missing frame")
        coEvery { repository.status(session) } throws DeviceExchangeWireException("missing frame")
        val signIn = useCase.signIn(entry, charArrayOf()) as ExchangeAdminSignIn.Refused
        val operation = useCase.status(session, entry.host) as ExchangeAdminOutcome.Unreachable
        assertEquals(ExchangeMessage.UNANSWERED, signIn.diagnosis.message)
        assertEquals(ExchangeMessage.UNANSWERED, operation.diagnosis.message)
    }

    @Test
    fun `reply failure maps known and unknown codes`() = runTest {
        val codes = mapOf(
            "exists" to ExchangeAdminReplyCode.EXISTS,
            "future" to ExchangeAdminReplyCode.INTERNAL,
        )
        for ((code, expected) in codes) {
            coEvery { repository.status(session) } throws DeviceExchangeAdminReplyException(code)
            assertEquals(ExchangeAdminOutcome.Failed(expected), useCase.status(session, entry.host))
        }
    }

    @Test
    fun `expired session requires sign in again`() = runTest {
        coEvery { repository.status(session) } throws DeviceExchangeAdminSessionLapsedException()
        assertSame(ExchangeAdminOutcome.Lapsed, useCase.status(session, entry.host))
    }

    @Test
    fun `io failure uses the existing diagnosis ladder`() = runTest {
        val failure = IOException("socket closed")
        coEvery { repository.status(session) } throws failure
        val expected = DiagnoseExchangeFailureUseCase(network).fromCause(failure, entry.host)
        assertEquals(ExchangeAdminOutcome.Unreachable(expected), useCase.status(session, entry.host))
    }

    @Test
    fun `successful sign in returns the owned session`() = runTest {
        coEvery { repository.signIn(entry, any()) } returns session
        assertEquals(ExchangeAdminSignIn.SignedIn(session), useCase.signIn(entry, charArrayOf()))
    }

    @Test
    fun `cancellation escapes sign in and operations`() = runTest {
        val cancellation = CancellationException("screen closed")
        coEvery { repository.signIn(entry, any()) } throws cancellation
        coEvery { repository.status(session) } throws cancellation
        try {
            useCase.signIn(entry, charArrayOf())
            fail("sign in swallowed cancellation")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
        try {
            useCase.status(session, entry.host)
            fail("operation swallowed cancellation")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
    }

    private companion object {
        const val PORT = 8443
    }
}
