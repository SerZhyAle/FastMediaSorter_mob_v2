package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class PlayerNavigationCoordinatorShuffleTest {

    @Test
    fun `a drawn cycle never opens on the current index`() {
        for (size in listOf(2, SMALL_LIST, LARGE_LIST)) {
            for (seed in 0 until DRAWS) {
                val current = seed % size
                val cycle = PlayerNavigationCoordinator.drawShuffleCycle(size, current, Random(seed))
                assertNotEquals("size=$size seed=$seed", current, cycle.first())
                assertEquals("size=$size seed=$seed", (0 until size).toList(), cycle.sorted())
            }
        }
    }

    @Test
    fun `a single file cycle returns the current index`() {
        assertEquals(listOf(0), PlayerNavigationCoordinator.drawShuffleCycle(1, 0, Random(1)))
    }

    @Test
    fun `next at a cycle seam never repeats the file just shown`() = runTest {
        for (size in listOf(2, LARGE_LIST)) {
            repeat(DRAWS) {
                val current = it % size
                val seamOrder = (0 until size).filter { index -> index != current } + current
                val state = shuffleState(size, current, seamOrder)
                newCoordinator(backgroundScope, state).nextFile()
                assertNotEquals("size=$size draw=$it", current, state.value.currentIndex)
            }
        }
    }

    @Test
    fun `first step after switching into shuffle never repeats the file just shown`() = runTest {
        repeat(DRAWS) {
            val state = shuffleState(LARGE_LIST, current = 3, order = emptyList())
            newCoordinator(backgroundScope, state).nextFile()
            assertNotEquals("draw=$it", 3, state.value.currentIndex)
            assertEquals(LARGE_LIST, state.value.shuffleIndices.size)
        }
    }

    @Test
    fun `a single file list stays on that file`() = runTest {
        val state = shuffleState(1, current = 0, order = listOf(0))
        newCoordinator(backgroundScope, state).nextFile()
        assertEquals(0, state.value.currentIndex)
    }

    private fun shuffleState(size: Int, current: Int, order: List<Int>) = MutableStateFlow(
        PlayerViewModel.PlayerState(
            files = (0 until size).map { mediaFile(it) },
            currentIndex = current,
            playbackOrderMode = PlaybackOrderMode.SHUFFLE,
            shuffleIndices = order
        )
    )

    private fun newCoordinator(scope: CoroutineScope, state: MutableStateFlow<PlayerViewModel.PlayerState>) =
        PlayerNavigationCoordinator(
            scope = scope,
            resourceRepository = mockk<ResourceRepository>(relaxed = true),
            stateFlow = state,
            updateState = { transform -> state.value = transform(state.value) },
            saveResumeState = {}
        )

    private fun mediaFile(index: Int) = MediaFile(
        name = "img$index.jpg",
        path = "/storage/test/img$index.jpg",
        type = MediaType.IMAGE,
        size = 1L,
        createdDate = 0L
    )

    private companion object {
        const val SMALL_LIST = 3
        const val LARGE_LIST = 10
        const val DRAWS = 500
    }
}
