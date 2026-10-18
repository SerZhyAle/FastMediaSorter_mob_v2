package com.sza.fastmediasorter.wear.ui.exchange

import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcastSection
import com.sza.fastmediasorter.wear.domain.repository.WearAccountBroadcastRepository
import com.sza.fastmediasorter.wear.domain.usecase.ObserveAccountBroadcastsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PlayAccountBroadcastUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareEphemeralStreamPlaybackUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4339: the screen's list is followed only while the state is collected - a backgrounded screen stops the
 * directory (and its credentials) after the subscription grace, and a re-subscription starts it again. The
 * loading grace and the tap semantics ride along unchanged. Virtual time throughout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearAccountBroadcastsViewModelTest {

    private val repository = mockk<WearAccountBroadcastRepository>()
    private val upstream = MutableSharedFlow<List<WearAccountBroadcastSection>>(extraBufferCapacity = 4)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        observeFlow: Flow<List<WearAccountBroadcastSection>> = upstream,
    ): WearAccountBroadcastsViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        every { repository.observe() } returns observeFlow
        return WearAccountBroadcastsViewModel(
            observeBroadcasts = ObserveAccountBroadcastsUseCase(repository),
            playBroadcast = PlayAccountBroadcastUseCase(repository, mockk<PrepareEphemeralStreamPlaybackUseCase>()),
        )
    }

    @Test
    fun `the directory is followed while collected, dropped after the grace, resumed on return`() = runTest {
        val viewModel = viewModel()
        val collected = launch { viewModel.uiState.collect() }
        runCurrent()
        assertEquals(1, upstream.subscriptionCount.value)

        upstream.emit(listOf(section("ex:1")))
        runCurrent()
        assertTrue(viewModel.uiState.value.hasAnswered)
        assertEquals(1, viewModel.uiState.value.sections.size)

        collected.cancelAndJoin()
        runCurrent()
        // Still inside the grace: the upstream is kept, so a quick screen cycle does not restart the list.
        advanceTimeBy(WITHIN_GRACE_MS)
        runCurrent()
        assertEquals(1, upstream.subscriptionCount.value)

        advanceTimeBy(PAST_GRACE_MS)
        runCurrent()
        assertEquals(0, upstream.subscriptionCount.value)

        val returned = launch { viewModel.uiState.collect() }
        runCurrent()
        assertEquals(1, upstream.subscriptionCount.value)

        upstream.emit(listOf(section("ex:1"), section("ex:2")))
        runCurrent()
        assertEquals(2, viewModel.uiState.value.sections.size)
        returned.cancelAndJoin()
    }

    @Test
    fun `the loading grace ends even when the list never answers`() = runTest {
        // The real directory answers a subscription with its initial not-yet-loaded row at once and then stays
        // silent until a server answer; the fixture mirrors that, so only the grace can end the loading row.
        val viewModel = viewModel(
            observeFlow = flow<List<WearAccountBroadcastSection>> {
                emit(emptyList())
                awaitCancellation()
            },
        )
        val collected = launch { viewModel.uiState.collect() }
        runCurrent()

        assertFalse(viewModel.uiState.value.isGraceOver)
        advanceTimeBy(LOADING_GRACE_MS)
        runCurrent()

        assertTrue(viewModel.uiState.value.isGraceOver)
        assertTrue(viewModel.uiState.value.sections.isEmpty())
        collected.cancelAndJoin()
    }

    private fun section(entryIdentity: String): WearAccountBroadcastSection = WearAccountBroadcastSection(
        entryIdentity = entryIdentity,
        entryLabel = "phone.example:7443",
        groups = emptyList(),
        isReachable = true,
        isLoaded = true,
    )

    private companion object {
        // The grace under test is 5 s: WITHIN_GRACE stays inside it, PAST_GRACE crosses it, so the test also
        // fails when the value is widened past a screen cycle or shortened to nothing.
        const val WITHIN_GRACE_MS = 4_000L
        const val PAST_GRACE_MS = 2_000L
        const val LOADING_GRACE_MS = 15_000L
    }
}
