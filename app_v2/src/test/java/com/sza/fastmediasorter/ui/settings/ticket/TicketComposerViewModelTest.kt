package com.sza.fastmediasorter.ui.settings.ticket

import android.app.Activity
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentityFailureReason
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccount
import com.sza.fastmediasorter.domain.model.feedback.FeedbackFolderChoice
import com.sza.fastmediasorter.domain.model.ticket.TicketDestination
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxState
import com.sza.fastmediasorter.domain.usecase.feedback.ObserveFeedbackFoldersUseCase
import com.sza.fastmediasorter.domain.usecase.ticket.GetTicketProjectsUseCase
import com.sza.fastmediasorter.domain.usecase.ticket.GrantTicketDriveAccessUseCase
import com.sza.fastmediasorter.domain.usecase.ticket.ObserveTicketOutboxUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** S4765: when the composer offers the Drive consent action and what each outcome of that screen posts. */
@OptIn(ExperimentalCoroutinesApi::class)
class TicketComposerViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val outboxRows = MutableStateFlow<List<TicketOutboxEntry>>(emptyList())
    private val getProjects = mockk<GetTicketProjectsUseCase>()
    private val observeOutbox = mockk<ObserveTicketOutboxUseCase>()
    private val grantAccess = mockk<GrantTicketDriveAccessUseCase>()
    private val observeFolders = mockk<ObserveFeedbackFoldersUseCase>()
    private val activity = mockk<Activity>()

    private fun viewModel(): TicketComposerViewModel {
        coEvery { getProjects() } returns null
        every { observeOutbox() } returns outboxRows
        coEvery { observeOutbox.refresh() } just Runs
        every { observeFolders() } returns flowOf(FeedbackFolderChoice())
        return TicketComposerViewModel(mockk(), getProjects, observeOutbox, grantAccess, observeFolders)
    }

    private fun entry(
        failure: String?,
        destination: TicketDestination = TicketDestination.Drive,
        state: TicketOutboxState = TicketOutboxState.WAITING
    ) = TicketOutboxEntry(
        packetId = "p1",
        createdAtEpochMs = 0L,
        title = "t",
        project = null,
        state = state,
        failure = failure,
        destination = destination
    )

    @Test
    fun `access is offered only while a Drive packet waits for a missing sign-in`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.driveAccessNeeded)

        outboxRows.value = listOf(entry(TicketOutboxEntry.NOT_SIGNED_IN_FAILURE))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.driveAccessNeeded)

        outboxRows.value = listOf(entry("timeout: socket is not established"))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.driveAccessNeeded)

        outboxRows.value = listOf(entry(TicketOutboxEntry.NOT_SIGNED_IN_FAILURE, TicketDestination.Folder(7L)))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.driveAccessNeeded)

        outboxRows.value = listOf(entry(TicketOutboxEntry.NOT_SIGNED_IN_FAILURE, TicketDestination.ExchangeDirect))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.driveAccessNeeded)

        outboxRows.value = listOf(entry(TicketOutboxEntry.NOT_SIGNED_IN_FAILURE, state = TicketOutboxState.QUEUED))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.driveAccessNeeded)
    }

    @Test
    fun `a grant posts its notice, reloads the project list and asks for a delivery pass`() = runTest {
        val account = PrimaryGoogleAccount("owner@example.com", null, null, setOf(GoogleScope.DRIVE), Instant.EPOCH)
        coEvery { grantAccess(any(), any(), any()) } returns IdentitySignInResult.Success(account)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.grantDriveAccess(activity)
        advanceUntilIdle()

        assertEquals(TicketComposerNotice.DriveAccessGranted, viewModel.state.value.notice)
        assertFalse(viewModel.state.value.busy)
        coVerify(exactly = 2) { getProjects() }
        coVerify(exactly = 2) { observeOutbox.refresh() }
    }

    @Test
    fun `a declined consent screen posts its notice and leaves the queue alone`() = runTest {
        coEvery { grantAccess(any(), any(), any()) } returns IdentitySignInResult.Cancelled
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.grantDriveAccess(activity)
        advanceUntilIdle()

        assertEquals(TicketComposerNotice.DriveAccessDeclined, viewModel.state.value.notice)
        assertFalse(viewModel.state.value.busy)
        coVerify(exactly = 1) { getProjects() }
        coVerify(exactly = 1) { observeOutbox.refresh() }
    }

    @Test
    fun `a consent screen that could not be shown posts the failure notice`() = runTest {
        coEvery { grantAccess(any(), any(), any()) } returns
            IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.grantDriveAccess(activity)
        advanceUntilIdle()

        assertEquals(TicketComposerNotice.DriveAccessFailed, viewModel.state.value.notice)
        assertFalse(viewModel.state.value.busy)
    }
}
