package com.sza.fastmediasorter.wear.ui.phone

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceClient
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceOutcome
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourcePage
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.io.path.createTempDirectory

/** S1898: the watch's own `TRANSFER_TIMEOUT_MS`, long enough that a tap outlives the list it was made on. */
private const val TRANSFER_MS = 30_000L

/**
 * S1697: a paired phone disappears often and briefly, so what the screen shows on a failed round
 * trip decides whether the user retries or concludes the phone has no media. These tests pin that
 * mapping rather than the transport.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneResourceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val client: PhoneResourceClient = mockk()

    // S1730: the view model now reads the file-list view mode. These cases pin the browse mapping,
    // so the stored view is held at its default and never varied.
    private val preferences: WearPreferencesRepository = mockk()
    private val selectedMedia: SelectedMediaManager = mockk(relaxed = true)

    // S1846: the view model now prepares a cache directory for a delivered phone file. Only the path is
    // read at construction, so a stub context with a real temp dir is enough and no Robolectric is needed.
    private val context: Context = mockk<Context>().also {
        every { it.cacheDir } returns createTempDirectory("s1846-test").toFile()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferences.fileListViewMode } returns flowOf(WearViewMode.LIST)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a page becomes content`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("expected content", state is PhoneResourceUiState.Content)
        assertEquals(listOf("Camera"), (state as PhoneResourceUiState.Content).items.map { it.name })
    }

    @Test
    fun `an answered but empty folder is not an error`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page())

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        assertEquals(PhoneResourceUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `a disconnected phone becomes unavailable with no reason to show`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.PhoneUnavailable

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        assertEquals(PhoneResourceUiState.Unavailable(null), viewModel.uiState.value)
    }

    @Test
    fun `a refusal keeps its status so the screen can phrase it`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns
            PhoneResourceOutcome.Rejected(WearPhoneResourceResponseStatus.ACCESS_DENIED)

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        assertEquals(
            PhoneResourceUiState.Unavailable(WearPhoneResourceResponseStatus.ACCESS_DENIED),
            viewModel.uiState.value
        )
    }

    @Test
    fun `retry after a disconnect recovers the content`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.PhoneUnavailable

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()
        assertEquals(PhoneResourceUiState.Unavailable(null), viewModel.uiState.value)

        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))
        viewModel.retry()
        advanceUntilIdle()

        assertTrue("expected content after retry", viewModel.uiState.value is PhoneResourceUiState.Content)
    }

    @Test
    fun `walking into a folder and back keeps the screen on the trail`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        viewModel.openFolder("1:Camera", "Camera")
        advanceUntilIdle()

        assertTrue("back from a folder stays on the screen", viewModel.navigateUp())
        advanceUntilIdle()
        assertTrue("back at the root leaves the screen", !viewModel.navigateUp())
    }

    @Test
    fun `the root is titled by the chip that opened the screen`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        assertEquals(ScreenTitle.Resource(R.string.phone_resource_title), viewModel.contentTitle())
    }

    @Test
    fun `a folder titles the screen with its own name`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        viewModel.openFolder("1:Camera", "Camera")
        advanceUntilIdle()

        assertEquals(ScreenTitle.Text("Camera"), viewModel.contentTitle())
    }

    @Test
    fun `the second level names the second folder and back names the first again`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        viewModel.openFolder("1:Camera", "Camera")
        advanceUntilIdle()
        viewModel.openFolder("2:DCIM", "DCIM")
        advanceUntilIdle()
        assertEquals(ScreenTitle.Text("DCIM"), viewModel.contentTitle())

        viewModel.navigateUp()
        advanceUntilIdle()

        assertEquals(ScreenTitle.Text("Camera"), viewModel.contentTitle())
    }

    @Test
    fun `back from the first folder restores the chip title`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        viewModel.openFolder("1:Camera", "Camera")
        advanceUntilIdle()
        viewModel.navigateUp()
        advanceUntilIdle()

        assertEquals(ScreenTitle.Resource(R.string.phone_resource_title), viewModel.contentTitle())
    }

    private fun PhoneResourceViewModel.contentTitle(): ScreenTitle =
        (uiState.value as PhoneResourceUiState.Content).title

    /**
     * S1898: the refusal line is anchored to the screen now, so it no longer scrolls out of sight on
     * its own. Walking into another folder has to drop it, or it stays on screen naming a file the
     * folder in front of the user does not contain.
     */
    @Test
    fun `walking into a folder drops the refusal raised in the previous one`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))
        coEvery { client.open(any(), any()) } returns PhoneResourceOutcome.PhoneUnavailable

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        viewModel.openFile(fileItem("holiday.jpg"))
        advanceUntilIdle()
        assertEquals(PhoneFileOpenOutcome.Failed(null), viewModel.openOutcome.value)

        viewModel.openFolder("1:Camera", "Camera")
        advanceUntilIdle()

        assertNull("a refusal must not follow the user into the next folder", viewModel.openOutcome.value)
    }

    /**
     * S1898: walking away does not stop the transfer, which is out of scope to cancel. What it must do
     * is stop the transfer from speaking: landing 30 s later, it would otherwise repaint the pinned row
     * over a folder that never held the file it names.
     */
    @Test
    fun `a transfer that lands after the user walked away says nothing`() = runTest {
        coEvery { client.browse(any(), any(), any(), any()) } returns PhoneResourceOutcome.Page(page(item("Camera")))
        coEvery { client.open(any(), any()) } coAnswers {
            delay(TRANSFER_MS)
            PhoneResourceOutcome.PhoneUnavailable
        }

        val viewModel = PhoneResourceViewModel(client, selectedMedia, context, preferences, SavedStateHandle())
        advanceUntilIdle()

        viewModel.openFile(fileItem("holiday.jpg"))
        viewModel.openFolder("1:Camera", "Camera")
        advanceUntilIdle()

        assertNull("a transfer outlived its list and must not repaint over the new one", viewModel.openOutcome.value)
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

    private fun fileItem(name: String) = WearPhoneResourceItem(
        token = "1:$name",
        name = name,
        mimeType = "image/jpeg",
        isDirectory = false
    )
}
