package com.sza.fastmediasorter.ui.exchange

import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminSession
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccount
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccountAdded
import com.sza.fastmediasorter.domain.model.ExchangeAdminCreatedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminKinds
import com.sza.fastmediasorter.domain.model.ExchangeAdminOutcome
import com.sza.fastmediasorter.domain.model.ExchangeAdminPolicy
import com.sza.fastmediasorter.domain.model.ExchangeAdminReplyCode
import com.sza.fastmediasorter.domain.model.ExchangeAdminSharedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminSignIn
import com.sza.fastmediasorter.domain.model.ExchangeAdminStatus
import com.sza.fastmediasorter.domain.model.ExchangeDiagnosis
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.usecase.exchange.AdministerExchangeServerUseCase
import com.sza.fastmediasorter.domain.usecase.exchange.ManageExchangeAccountsUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeServerAdminViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val entry = DeviceExchangeAccountEntry("server.example", PORT, "operator", "device", null)
    private val entries = MutableStateFlow(listOf(entry))
    private val accounts = mockk<ManageExchangeAccountsUseCase> {
        every { entries } returns this@ExchangeServerAdminViewModelTest.entries
    }
    private val admin = mockk<AdministerExchangeServerUseCase>()
    private val session = mockk<DeviceExchangeAdminSession>(relaxed = true)
    private val status = mockk<ExchangeAdminStatus>()
    private val shared = ExchangeAdminSharedInvitation(enabled = false, kind = null)

    private fun stubSections(
        listed: List<ExchangeAdminAccount> = emptyList(),
        invitations: List<ExchangeAdminInvitation> = emptyList(),
        policy: ExchangeAdminPolicy = mockk(),
    ) {
        coEvery { admin.accounts(session, entry.host) } returns ExchangeAdminOutcome.Done(listed)
        coEvery { admin.invitations(session, entry.host) } returns ExchangeAdminOutcome.Done(invitations)
        coEvery { admin.sharedInvitation(session, entry.host) } returns ExchangeAdminOutcome.Done(shared)
        coEvery { admin.policy(session, entry.host) } returns ExchangeAdminOutcome.Done(policy)
    }

    @Test
    fun `sign in loads status and erases password`() = runTest {
        coEvery { admin.signIn(entry, any()) } returns ExchangeAdminSignIn.SignedIn(session)
        coEvery { admin.status(session, entry.host) } returns ExchangeAdminOutcome.Done(status)
        val policy = mockk<ExchangeAdminPolicy>()
        stubSections(policy = policy)
        val model = createModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        val password = "operator-password".toCharArray()
        model.signIn(password)
        advanceUntilIdle()
        assertEquals(
            ExchangeAdminScreenState.SignedIn(status, emptyList(), emptyList(), shared, policy),
            model.state.value,
        )
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 1) { admin.status(session, entry.host) }
        model.endSession()
        advanceUntilIdle()
    }

    @Test
    fun `disabled administration replaces sign in`() = runTest {
        coEvery { admin.signIn(entry, any()) } returns ExchangeAdminSignIn.Refused(
            ExchangeDiagnosis(ExchangeMessage.ADMIN_DISABLED, emptyList()),
        )
        val model = createModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        model.signIn(charArrayOf('p'))
        advanceUntilIdle()
        assertEquals(ExchangeAdminScreenState.Disabled, model.state.value)
    }

    @Test
    fun `ending session closes stream once and asks for password`() = runTest {
        coEvery { admin.signIn(entry, any()) } returns ExchangeAdminSignIn.SignedIn(session)
        coEvery { admin.status(session, entry.host) } returns ExchangeAdminOutcome.Done(status)
        stubSections()
        val model = createModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        model.signIn(charArrayOf('p'))
        advanceUntilIdle()
        model.endSession()
        model.endSession()
        advanceUntilIdle()
        verify(exactly = 1) { session.close() }
        assertEquals(ExchangeAdminScreenState.SignedOut("server.example:$PORT"), model.state.value)
    }

    @Test
    fun `lapsed reply closes stream and asks for password again`() = runTest {
        coEvery { admin.signIn(entry, any()) } returns ExchangeAdminSignIn.SignedIn(session)
        coEvery { admin.status(session, entry.host) } returns ExchangeAdminOutcome.Lapsed
        val model = createModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        model.signIn(charArrayOf('p'))
        advanceUntilIdle()
        assertEquals(ExchangeAdminScreenState.Lapsed, model.state.value)
        verify(exactly = 1) { session.close() }
    }

    @Test
    fun `background before coroutine starts still erases password`() = runTest {
        val model = createModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        val password = charArrayOf('p')
        model.signIn(password)
        model.endSession()
        advanceUntilIdle()
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 0) { admin.signIn(any(), any()) }
    }

    @Test
    fun `removed entry finishes screen and closes session`() = runTest {
        coEvery { admin.signIn(entry, any()) } returns ExchangeAdminSignIn.SignedIn(session)
        coEvery { admin.status(session, entry.host) } returns ExchangeAdminOutcome.Done(status)
        stubSections()
        val model = createModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        model.signIn(charArrayOf('p'))
        advanceUntilIdle()
        entries.value = emptyList()
        advanceUntilIdle()
        assertEquals(ExchangeAdminScreenState.MissingEntry, model.state.value)
        verify(exactly = 1) { session.close() }
    }

    @Test
    fun `add account reloads accounts`() = runTest {
        coEvery { admin.signIn(entry, any()) } returns ExchangeAdminSignIn.SignedIn(session)
        coEvery { admin.status(session, entry.host) } returns ExchangeAdminOutcome.Done(status)
        val listed = listOf(mockk<ExchangeAdminAccount>())
        val policy = mockk<ExchangeAdminPolicy>()
        stubSections(listed = listed, policy = policy)
        coEvery { admin.addAccount(session, entry.host, "new", any(), ExchangeAdminKinds.GUEST) } returns
            ExchangeAdminOutcome.Done(ExchangeAdminAccountAdded("new", overCap = false))
        val model = createModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        model.signIn(charArrayOf('p'))
        advanceUntilIdle()
        val password = "secret".toCharArray()
        model.addAccount("new", password, ExchangeAdminKinds.GUEST)
        advanceUntilIdle()
        assertEquals(
            ExchangeAdminScreenState.SignedIn(status, listed, emptyList(), shared, policy),
            model.state.value,
        )
        coVerify(exactly = 2) { admin.accounts(session, entry.host) }
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun `exists emits the reply failure and keeps the section`() = runTest {
        coEvery { admin.signIn(entry, any()) } returns ExchangeAdminSignIn.SignedIn(session)
        coEvery { admin.status(session, entry.host) } returns ExchangeAdminOutcome.Done(status)
        val listed = listOf(mockk<ExchangeAdminAccount>())
        stubSections(listed = listed)
        coEvery { admin.addAccount(session, entry.host, "taken", any(), ExchangeAdminKinds.GUEST) } returns
            ExchangeAdminOutcome.Failed(ExchangeAdminReplyCode.EXISTS)
        val model = createModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        model.signIn(charArrayOf('p'))
        advanceUntilIdle()
        val failures = mutableListOf<ExchangeAdminReplyCode>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.replyFailures.collect { failures += it }
        }
        model.addAccount("taken", "secret".toCharArray(), ExchangeAdminKinds.GUEST)
        advanceUntilIdle()
        assertEquals(listOf(ExchangeAdminReplyCode.EXISTS), failures)
        assertEquals(listed, (model.state.value as ExchangeAdminScreenState.SignedIn).accounts)
        coVerify(exactly = 1) { admin.accounts(session, entry.host) }
    }

    @Test
    fun `created invitation is emitted once and kept out of state`() = runTest {
        coEvery { admin.signIn(entry, any()) } returns ExchangeAdminSignIn.SignedIn(session)
        coEvery { admin.status(session, entry.host) } returns ExchangeAdminOutcome.Done(status)
        stubSections()
        val created = ExchangeAdminCreatedInvitation(
            code = "code-1",
            invitation = ExchangeAdminInvitation(
                codeHash = "hash-1",
                kind = ExchangeAdminKinds.GUEST,
                createdAt = "2026-10-18T10:00:00Z",
                expectedLogin = null,
                used = false,
                usedAt = null,
                usedBy = null,
            ),
            connectionString = "connection-string-1",
        )
        coEvery { admin.createInvitation(session, entry.host, ExchangeAdminKinds.GUEST, null) } returns
            ExchangeAdminOutcome.Done(created)
        val model = createModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        model.signIn(charArrayOf('p'))
        advanceUntilIdle()
        val seen = mutableListOf<ExchangeAdminCreatedInvitation>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.createdInvitation.collect { seen += it }
        }
        model.createInvitation(ExchangeAdminKinds.GUEST, null)
        advanceUntilIdle()
        assertEquals(listOf(created), seen)
        val state = model.state.value as ExchangeAdminScreenState.SignedIn
        assertEquals(emptyList<ExchangeAdminInvitation>(), state.invitations)
    }

    private fun createModel(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = ExchangeServerAdminViewModel(
        SavedStateHandle(mapOf(ExchangeServerAdminViewModel.IDENTITY to entry.identity)),
        accounts,
        admin,
        dispatcher,
    )

    private companion object {
        const val PORT = 9010
    }
}
