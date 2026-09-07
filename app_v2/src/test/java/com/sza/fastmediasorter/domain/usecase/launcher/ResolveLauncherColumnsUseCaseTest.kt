package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveLauncherColumnsUseCaseTest {

    private fun useCaseWith(cells: List<LauncherCell>): ResolveLauncherColumnsUseCase =
        ResolveLauncherColumnsUseCase(
            mockk<LauncherDesktopRepository> {
                every { observeCells(any()) } returns flowOf(cells)
            },
        )

    private fun cellAt(colIndex: Int, spanW: Int = 1): LauncherCell = LauncherCell(
        id = 0,
        orientation = LauncherOrientation.LANDSCAPE,
        rowIndex = 0,
        colIndex = colIndex,
        spanW = spanW,
        spanH = 1,
        kind = LauncherCellKind.SHORTCUT,
        target = "cmd:noop",
        labelOverride = null,
        addedAt = 0,
    )

    @Test
    fun `a stored width is returned as it stands`() = runBlocking {
        val columns = useCaseWith(listOf(cellAt(colIndex = 0)))(LauncherOrientation.LANDSCAPE, 11)

        assertEquals(
            "the surface that measured the screen is the authority whenever it has spoken",
            11,
            columns,
        )
    }

    @Test
    fun `an unknown width is derived from the rightmost occupied column`() = runBlocking {
        val seeded = listOf(cellAt(colIndex = 0), cellAt(colIndex = 4, spanW = 2), cellAt(colIndex = 10))

        val columns = useCaseWith(seeded)(LauncherOrientation.LANDSCAPE, 0)

        assertEquals(
            "a desktop occupying columns 0..10 is at least eleven columns wide, never the constant four",
            11,
            columns,
        )
    }

    @Test
    fun `an empty desktop falls back to the floor`() = runBlocking {
        val columns = useCaseWith(emptyList())(LauncherOrientation.PORTRAIT, 0)

        assertEquals("no cell can say how wide an empty grid is, so the placement still lands", 4, columns)
    }
}
