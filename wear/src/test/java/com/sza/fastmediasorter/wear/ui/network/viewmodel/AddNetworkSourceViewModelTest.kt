package com.sza.fastmediasorter.wear.ui.network.viewmodel

import android.content.Context
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S3955: the field chips stay tappable while a request runs, so an edit made during a slow test or
 * save must survive the request's answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddNetworkSourceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository: NetworkSourceRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val capabilities: WearRestrictedCapabilities = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a field edited during a connection test survives the answer`() = runTest(dispatcher) {
        val answer = CompletableDeferred<Result<Boolean>>()
        coEvery { repository.testConnection(any()) } coAnswers { answer.await() }
        val viewModel = filledForm()

        viewModel.testConnection()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.setShareName(EDITED_SHARE)
        answer.complete(Result.success(true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EDITED_SHARE, state.shareName)
        assertEquals(SERVER, state.server)
        assertFalse(state.isLoading)
        assertFalse(state.isError)
    }

    @Test
    fun `a field edited during a save survives the answer`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.addSource(any()) } coAnswers { gate.await() }
        val viewModel = filledForm()

        viewModel.saveSource()
        advanceUntilIdle()
        viewModel.setName(EDITED_NAME)
        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EDITED_NAME, state.name)
        assertFalse(state.isLoading)
        assertTrue(state.isSaved)
        coVerify(exactly = 1) { repository.addSource(any()) }
    }

    private fun filledForm(): AddNetworkSourceViewModel =
        AddNetworkSourceViewModel(repository, context, capabilities).apply {
            setServer(SERVER)
            setShareName(ORIGINAL_SHARE)
        }

    private companion object {
        const val SERVER = "192.168.1.10"
        const val ORIGINAL_SHARE = "media"
        const val EDITED_SHARE = "photos"
        const val EDITED_NAME = "Living room"
    }
}
