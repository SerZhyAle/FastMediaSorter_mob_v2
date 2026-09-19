package com.sza.fastmediasorter.ui.launcher.helpers

import android.app.Activity
import android.view.DragEvent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.grid.LauncherDesktopLayout
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherEditModeManagerSectionDragTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `dropping a section drag routes to moveSectionBlock`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = mockk<LauncherHomeViewModel>(relaxed = true)
        val sectionUi = cellUi(id = 10L, kind = LauncherCellKind.SECTION)
        val shortcutUi = cellUi(id = 20L, kind = LauncherCellKind.SHORTCUT)
        every { viewModel.cells } returns MutableStateFlow(listOf(sectionUi, shortcutUi))

        val desktop = mockk<LauncherDesktopLayout>(relaxed = true)
        val dragListenerSlot = slot<View.OnDragListener>()
        every { desktop.setOnDragListener(capture(dragListenerSlot)) } answers {}
        every { desktop.columns } returns 4
        every {
            desktop.cellAt(any(), any())
        } returns LauncherGridGeometry.CellFootprint(row = 3, col = 0, spanW = 1, spanH = 1)

        val manager = createManager(viewModel, desktop)
        manager.attach()
        assertTrue(dragListenerSlot.isCaptured)

        val dropEvent = mockk<DragEvent>(relaxed = true)
        every { dropEvent.action } returns DragEvent.ACTION_DROP
        every { dropEvent.localState } returns 10L
        every { dropEvent.x } returns 100f
        every { dropEvent.y } returns 200f

        val handled = dragListenerSlot.captured.onDrag(desktop, dropEvent)
        assertTrue(handled)

        verify(exactly = 1) { viewModel.moveSectionBlock(10L, 3) }
        verify(exactly = 0) { viewModel.moveCell(any(), any(), any(), any()) }
    }

    @Test
    fun `dropping an ordinary cell drag routes to moveCell`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = mockk<LauncherHomeViewModel>(relaxed = true)
        val sectionUi = cellUi(id = 10L, kind = LauncherCellKind.SECTION)
        val shortcutUi = cellUi(id = 20L, kind = LauncherCellKind.SHORTCUT)
        every { viewModel.cells } returns MutableStateFlow(listOf(sectionUi, shortcutUi))

        val desktop = mockk<LauncherDesktopLayout>(relaxed = true)
        val dragListenerSlot = slot<View.OnDragListener>()
        every { desktop.setOnDragListener(capture(dragListenerSlot)) } answers {}
        every { desktop.columns } returns 4
        every {
            desktop.cellAt(any(), any())
        } returns LauncherGridGeometry.CellFootprint(row = 2, col = 1, spanW = 1, spanH = 1)

        val manager = createManager(viewModel, desktop)
        manager.attach()
        assertTrue(dragListenerSlot.isCaptured)

        val dropEvent = mockk<DragEvent>(relaxed = true)
        every { dropEvent.action } returns DragEvent.ACTION_DROP
        every { dropEvent.localState } returns 20L
        every { dropEvent.x } returns 100f
        every { dropEvent.y } returns 150f

        val handled = dragListenerSlot.captured.onDrag(desktop, dropEvent)
        assertTrue(handled)

        verify(exactly = 1) { viewModel.moveCell(20L, 2, 1, 4) }
        verify(exactly = 0) { viewModel.moveSectionBlock(any(), any()) }
    }

    @Test
    fun `cancelled or invalid drag performs no move`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = mockk<LauncherHomeViewModel>(relaxed = true)
        every { viewModel.cells } returns MutableStateFlow(listOf(cellUi(id = 10L, kind = LauncherCellKind.SECTION)))

        val desktop = mockk<LauncherDesktopLayout>(relaxed = true)
        val dragListenerSlot = slot<View.OnDragListener>()
        every { desktop.setOnDragListener(capture(dragListenerSlot)) } answers {}

        val manager = createManager(viewModel, desktop)
        manager.attach()
        assertTrue(dragListenerSlot.isCaptured)

        // Cancelled / ended drag
        val endEvent = mockk<DragEvent>(relaxed = true)
        every { endEvent.action } returns DragEvent.ACTION_DRAG_ENDED
        dragListenerSlot.captured.onDrag(desktop, endEvent)

        // Drop with non-Long localState
        val invalidDropEvent = mockk<DragEvent>(relaxed = true)
        every { invalidDropEvent.action } returns DragEvent.ACTION_DROP
        every { invalidDropEvent.localState } returns "invalid_state"
        dragListenerSlot.captured.onDrag(desktop, invalidDropEvent)

        verify(exactly = 0) { viewModel.moveSectionBlock(any(), any()) }
        verify(exactly = 0) { viewModel.moveCell(any(), any(), any(), any()) }
    }

    private fun createManager(
        viewModel: LauncherHomeViewModel,
        desktop: LauncherDesktopLayout,
    ): LauncherEditModeManager {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val content = activity.findViewById<View>(android.R.id.content)
        return LauncherEditModeManager(
            lifecycleOwner = ResumedLifecycleOwner(),
            desktop = desktop,
            doneButton = mockk(relaxed = true),
            addCellButton = mockk(relaxed = true),
            snackbarAnchor = content,
            viewModel = viewModel,
            activeScreenIndex = { 0 },
            actions = mockk(relaxed = true),
        )
    }

    private fun cellUi(id: Long, kind: LauncherCellKind): LauncherCellUi = LauncherCellUi(
        cell = LauncherCell(
            id = id,
            orientation = LauncherOrientation.PORTRAIT,
            rowIndex = 0,
            colIndex = 0,
            spanW = 1,
            spanH = 1,
            kind = kind,
            target = if (kind == LauncherCellKind.SECTION) "sec:main" else "app:com.example",
            labelOverride = null,
            addedAt = 0L,
        ),
        visual = null,
        modeBadge = null,
    )

    private class ResumedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}
