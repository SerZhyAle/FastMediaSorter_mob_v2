package com.sza.fastmediasorter.ui.exchange.drive

import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.domain.model.transfer.DriveListing
import com.sza.fastmediasorter.domain.model.transfer.DriveListingEntry
import com.sza.fastmediasorter.domain.model.transfer.DriveRefusalReason
import com.sza.fastmediasorter.domain.transfer.DriveFileChannel
import com.sza.fastmediasorter.domain.transfer.DriveProducerTarget
import com.sza.fastmediasorter.domain.transfer.DriveRequestProgress
import com.sza.fastmediasorter.domain.usecase.transfer.ResolveDriveProducerUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** S4231: browsing a published folder via Google Drive - listing, the root boundary, refusals, saving. */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveChannelBrowseViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val channel = mockk<DriveFileChannel>()
    private val listing = DriveListing(
        asOf = "2026-10-15T08:00:00Z",
        entries = listOf(
            DriveListingEntry("b.jpg", dir = false, size = 1L, modifiedAt = "2026-10-14T08:00:00Z"),
            DriveListingEntry("Zeta", dir = true, size = 0L, modifiedAt = "2026-10-14T08:00:00Z"),
            DriveListingEntry("a.jpg", dir = false, size = 1L, modifiedAt = "2026-10-14T08:00:00Z")
        )
    )

    private val resolve = mockk<ResolveDriveProducerUseCase>().also {
        coEvery { it("res", "sftp://host:2222/Camera") } returns
            DriveProducerTarget("dev-producer", "Pixel 8", null, "res", "Camera", pinProtected = false)
    }

    private fun viewModel() = DriveChannelBrowseViewModel(
        channel,
        resolve,
        SavedStateHandle(
            mapOf(
                DriveChannelBrowseViewModel.ARG_EXCHANGE_RESOURCE_ID to "res",
                DriveChannelBrowseViewModel.ARG_RESOURCE_URI to "sftp://host:2222/Camera"
            )
        )
    )

    @Test
    fun `the listing shows folders first and asks below the resource root`() = runTest {
        every { channel.list(any(), any(), any()) } returns
            flowOf(DriveRequestProgress.Waiting("Pixel 8", null), DriveRequestProgress.Listing(listing))
        val viewModel = viewModel()

        viewModel.open()
        advanceUntilIdle()

        assertEquals(listOf("Zeta", "a.jpg", "b.jpg"), viewModel.state.value.entries.map { it.name })
        verify { channel.list("dev-producer", "res", "Camera") }
    }

    @Test
    fun `up never climbs above the resource root`() = runTest {
        every { channel.list(any(), any(), any()) } returns flowOf(DriveRequestProgress.Listing(listing))
        val viewModel = viewModel()

        viewModel.open()
        viewModel.up()
        advanceUntilIdle()

        verify(exactly = 1) { channel.list(any(), any(), any()) }
        assertTrue(!viewModel.state.value.canGoUp)
    }

    @Test
    fun `a protected root and a silent producer are said in words`() = runTest {
        every { channel.list(any(), any(), "Camera") } returns
            flowOf(DriveRequestProgress.Refused(DriveRefusalReason.PIN_REQUIRED))
        every { channel.list(any(), any(), "Camera/Zeta") } returns
            flowOf(DriveRequestProgress.TimedOut("Pixel 8", null))
        val viewModel = viewModel()

        viewModel.open()
        advanceUntilIdle()
        assertEquals(DriveBrowseStatus.Locked, viewModel.state.value.status)

        viewModel.enter("Zeta")
        advanceUntilIdle()
        assertEquals(DriveBrowseStatus.TimedOut("Pixel 8"), viewModel.state.value.status)
    }

    @Test
    fun `saving fetches the picked files and counts what was placed`() = runTest {
        every { channel.list(any(), any(), any()) } returns flowOf(DriveRequestProgress.Listing(listing))
        every { channel.fetch(any(), any(), any()) } returns flowOf(
            DriveRequestProgress.Files(listOf("/in/a.jpg"), "x", final = false),
            DriveRequestProgress.Files(listOf("/in/b.jpg"), "x", final = true)
        )
        val viewModel = viewModel()
        viewModel.open()
        advanceUntilIdle()

        viewModel.toggle("a.jpg")
        viewModel.toggle("b.jpg")
        viewModel.saveSelected()
        advanceUntilIdle()

        verify { channel.fetch("dev-producer", "res", listOf("Camera/a.jpg", "Camera/b.jpg")) }
        assertEquals(DriveBrowseStatus.Saved(2), viewModel.state.value.status)
        assertTrue(viewModel.state.value.selected.isEmpty())
    }

    @Test
    fun `a resource nobody publishes on Drive is unavailable`() = runTest {
        coEvery { resolve(any<String>(), any()) } returns null
        val viewModel = viewModel()

        viewModel.open()
        advanceUntilIdle()

        assertEquals(DriveBrowseStatus.Unavailable, viewModel.state.value.status)
    }
}
