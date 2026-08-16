package com.sza.fastmediasorter.wear.ui.phone

import com.sza.fastmediasorter.wear.data.wear.PhoneResourceClient
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceOutcome
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourcePage
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1697: a paired phone disappears often and briefly, so what the screen shows on a failed round
 * trip decides whether the user retries or concludes the phone has no media. These tests pin that
 * mapping rather than the transport.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneResourceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val client: PhoneResourceClient = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a page becomes content`() = runTest {
        coEvery { client.browse(any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))

        val viewModel = PhoneResourceViewModel(client)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("expected content", state is PhoneResourceUiState.Content)
        assertEquals(listOf("Camera"), (state as PhoneResourceUiState.Content).items.map { it.name })
    }

    @Test
    fun `an answered but empty folder is not an error`() = runTest {
        coEvery { client.browse(any(), any()) } returns PhoneResourceOutcome.Page(page())

        val viewModel = PhoneResourceViewModel(client)
        advanceUntilIdle()

        assertEquals(PhoneResourceUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `a disconnected phone becomes unavailable with no reason to show`() = runTest {
        coEvery { client.browse(any(), any()) } returns PhoneResourceOutcome.PhoneUnavailable

        val viewModel = PhoneResourceViewModel(client)
        advanceUntilIdle()

        assertEquals(PhoneResourceUiState.Unavailable(null), viewModel.uiState.value)
    }

    @Test
    fun `a refusal keeps its status so the screen can phrase it`() = runTest {
        coEvery { client.browse(any(), any()) } returns
            PhoneResourceOutcome.Rejected(WearPhoneResourceResponseStatus.ACCESS_DENIED)

        val viewModel = PhoneResourceViewModel(client)
        advanceUntilIdle()

        assertEquals(
            PhoneResourceUiState.Unavailable(WearPhoneResourceResponseStatus.ACCESS_DENIED),
            viewModel.uiState.value
        )
    }

    @Test
    fun `retry after a disconnect recovers the content`() = runTest {
        coEvery { client.browse(any(), any()) } returns PhoneResourceOutcome.PhoneUnavailable

        val viewModel = PhoneResourceViewModel(client)
        advanceUntilIdle()
        assertEquals(PhoneResourceUiState.Unavailable(null), viewModel.uiState.value)

        coEvery { client.browse(any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))
        viewModel.retry()
        advanceUntilIdle()

        assertTrue("expected content after retry", viewModel.uiState.value is PhoneResourceUiState.Content)
    }

    @Test
    fun `walking into a folder and back keeps the screen on the trail`() = runTest {
        coEvery { client.browse(any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))

        val viewModel = PhoneResourceViewModel(client)
        advanceUntilIdle()

        viewModel.openFolder("1:Camera")
        advanceUntilIdle()

        assertTrue("back from a folder stays on the screen", viewModel.navigateUp())
        advanceUntilIdle()
        assertTrue("back at the root leaves the screen", !viewModel.navigateUp())
    }

    private fun page(vararg items: WearPhoneResourceItem) = WearPhoneResourcePage(
        requestId = "req-1",
        status = if (items.isEmpty()) {
            WearPhoneResourceResponseStatus.EMPTY
        } else {
            WearPhoneResourceResponseStatus.OK
        },
        items = items.toList()
    )

    private fun item(name: String) = WearPhoneResourceItem(
        token = "1:$name",
        name = name,
        isDirectory = true
    )
}
