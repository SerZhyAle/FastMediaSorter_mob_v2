package com.sza.fastmediasorter.ui.launcher.helpers

import android.app.Activity
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1680: the one-shot rotation hint is a decision compared against a stored flag, so it must wait for
 * storage to answer instead of deciding on a seed (the rule S1535 recorded).
 *
 * Both tests make storage answer only after a delay - the ~100 ms a cold start spends before DataStore
 * has read. Deciding inside that window is exactly the defect: it re-showed a hint already dismissed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherEditModeRotationHintTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `dismissed hint is not shown again when storage answers late`() =
        runTest(dispatcherRule.testDispatcher) {
            val viewModel = viewModelWith(storedHintShown = true)

            manager(viewModel).onOrientationChanged()
            advanceUntilIdle()

            verify(exactly = 0) { viewModel.markRotationHintShown() }
        }

    @Test
    fun `hint is still shown on the first rotation`() =
        runTest(dispatcherRule.testDispatcher) {
            val viewModel = viewModelWith(storedHintShown = false)

            manager(viewModel).onOrientationChanged()
            advanceUntilIdle()

            verify(exactly = 1) { viewModel.markRotationHintShown() }
        }

    private fun viewModelWith(storedHintShown: Boolean): LauncherHomeViewModel {
        val viewModel = mockk<LauncherHomeViewModel>(relaxed = true)
        every { viewModel.rotationHintShown } returns flow {
            delay(STORAGE_ANSWER_DELAY_MS)
            emit(storedHintShown)
        }
        every { viewModel.cells } returns MutableStateFlow(listOf(cellUi()))
        return viewModel
    }

    private fun manager(viewModel: LauncherHomeViewModel): LauncherEditModeManager {
        // A real content view, because the shown branch raises a Snackbar against it.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val content = activity.findViewById<View>(android.R.id.content)
        return LauncherEditModeManager(
            lifecycleOwner = ResumedLifecycleOwner(),
            desktop = mockk(relaxed = true),
            doneButton = mockk(relaxed = true),
            addCellButton = mockk(relaxed = true),
            snackbarAnchor = content,
            viewModel = viewModel,
            onAddCellClick = {},
        )
    }

    private fun cellUi(): LauncherCellUi = LauncherCellUi(
        cell = LauncherCell(
            id = 1L,
            orientation = LauncherOrientation.PORTRAIT,
            rowIndex = 0,
            colIndex = 0,
            spanW = 1,
            spanH = 1,
            kind = LauncherCellKind.SHORTCUT,
            target = "app:com.example",
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

    private companion object {
        /** Measured on RFCR110NBQJ 2026-08-15: 101 ms between the seed and the stored value. */
        const val STORAGE_ANSWER_DELAY_MS = 100L
    }
}
