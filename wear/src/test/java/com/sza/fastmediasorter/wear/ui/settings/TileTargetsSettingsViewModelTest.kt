package com.sza.fastmediasorter.wear.ui.settings

import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.usecase.LoadWearTileContentUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TileTargetsSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var loadWearTileContentUseCase: LoadWearTileContentUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        loadWearTileContentUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun listsOnlyKindsThatCarryAnAssignableTarget() = runTest {
        answerWith(WearTileContent.Unassigned(WearTileKind.RESOURCE))

        val rows = rowsAfterLoad()

        assertEquals(listOf(WearTileKind.RESOURCE, WearTileKind.STREAM), rows.map { it.kind })
    }

    @Test
    fun captionNamesTheAssignedTarget() = runTest {
        coEvery { loadWearTileContentUseCase(WearTileKind.RESOURCE) } returns WearTileContent.Assigned(
            title = "NAS",
            subtitle = "192.168.1.1",
            launchTarget = WearLaunchTarget.Open(WearTileTargetRef.Favourites)
        )
        coEvery { loadWearTileContentUseCase(WearTileKind.STREAM) } returns
            WearTileContent.Unassigned(WearTileKind.STREAM)

        val rows = rowsAfterLoad()

        assertEquals(TileTargetCaption.Assigned("NAS"), rows.first { it.kind == WearTileKind.RESOURCE }.caption)
    }

    @Test
    fun captionSaysNothingIsChosenWhenNoTargetIsAssigned() = runTest {
        answerWith(WearTileContent.Unassigned(WearTileKind.RESOURCE))

        val rows = rowsAfterLoad()

        assertEquals(TileTargetCaption.None, rows.first { it.kind == WearTileKind.STREAM }.caption)
    }

    @Test
    fun captionSaysTheTargetIsGoneWhenItNoLongerResolves() = runTest {
        answerWith(WearTileContent.TargetMissing(WearTileKind.RESOURCE))

        val rows = rowsAfterLoad()

        assertEquals(TileTargetCaption.Missing, rows.first { it.kind == WearTileKind.RESOURCE }.caption)
    }

    private fun answerWith(content: WearTileContent) {
        coEvery { loadWearTileContentUseCase(any()) } returns content
    }

    private fun TestScope.rowsAfterLoad(): List<TileTargetRow> {
        val viewModel = TileTargetsSettingsViewModel(loadWearTileContentUseCase)
        advanceUntilIdle()
        return viewModel.uiState.value.rows
    }
}
