package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2685: [LauncherDesktopGeometryManager.cellsMatchOrientation] decides whether the rotation callback
 * renders now or waits for the repository to answer for the new orientation. The empty-list branch is
 * the one case that must still render synchronously - answering false there would leave a first-run
 * desktop unrendered until some unrelated emission arrived.
 */
class LauncherDesktopOrientationMatchTest {

    @Test
    fun `an empty desktop matches either orientation`() {
        assertTrue(
            LauncherDesktopGeometryManager.cellsMatchOrientation(
                emptyList(),
                LauncherOrientation.PORTRAIT,
            ),
        )
        assertTrue(
            LauncherDesktopGeometryManager.cellsMatchOrientation(
                emptyList(),
                LauncherOrientation.LANDSCAPE,
            ),
        )
    }

    @Test
    fun `portrait cells match portrait and not landscape`() {
        val cells = listOf(cellUi(LauncherOrientation.PORTRAIT))

        assertTrue(
            LauncherDesktopGeometryManager.cellsMatchOrientation(cells, LauncherOrientation.PORTRAIT),
        )
        assertFalse(
            LauncherDesktopGeometryManager.cellsMatchOrientation(cells, LauncherOrientation.LANDSCAPE),
        )
    }

    @Test
    fun `landscape cells match landscape and not portrait`() {
        val cells = listOf(cellUi(LauncherOrientation.LANDSCAPE))

        assertTrue(
            LauncherDesktopGeometryManager.cellsMatchOrientation(cells, LauncherOrientation.LANDSCAPE),
        )
        assertFalse(
            LauncherDesktopGeometryManager.cellsMatchOrientation(cells, LauncherOrientation.PORTRAIT),
        )
    }

    private fun cellUi(orientation: LauncherOrientation): LauncherCellUi = LauncherCellUi(
        cell = LauncherCell(
            id = 1L,
            orientation = orientation,
            rowIndex = 0,
            colIndex = 0,
            spanW = 1,
            spanH = 1,
            kind = LauncherCellKind.SHORTCUT,
            target = "test",
            labelOverride = null,
            addedAt = 0L,
        ),
        visual = null,
        modeBadge = null,
    )
}
