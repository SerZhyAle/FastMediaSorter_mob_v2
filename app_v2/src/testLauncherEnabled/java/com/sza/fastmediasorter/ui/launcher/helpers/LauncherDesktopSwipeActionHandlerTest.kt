package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherDesktopSwipeActionHandlerTest {
    private val configured = LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.LOCK_SCREEN)

    @Test
    fun `horizontal swipes page until the boundary`() {
        val left = LauncherAllAppsGestureManager.DesktopSwipeDirection.LEFT
        val right = LauncherAllAppsGestureManager.DesktopSwipeDirection.RIGHT
        assertEquals(LauncherDesktopSwipeAction.NextScreen, resolveDesktopSwipeAction(left, 0, 3, configured))
        assertEquals(LauncherDesktopSwipeAction.NextScreen, resolveDesktopSwipeAction(left, 1, 3, configured))
        assertEquals(configured, resolveDesktopSwipeAction(left, 2, 3, configured))
        assertEquals(LauncherDesktopSwipeAction.PreviousScreen, resolveDesktopSwipeAction(right, 2, 3, configured))
        assertEquals(LauncherDesktopSwipeAction.PreviousScreen, resolveDesktopSwipeAction(right, 1, 3, configured))
        assertEquals(configured, resolveDesktopSwipeAction(right, 0, 3, configured))
        assertEquals(configured, resolveDesktopSwipeAction(left, 0, 1, configured))
        assertEquals(configured, resolveDesktopSwipeAction(right, 0, 1, configured))
        assertEquals(
            configured,
            resolveDesktopSwipeAction(LauncherAllAppsGestureManager.DesktopSwipeDirection.UP, 0, 3, configured),
        )
    }

    @Test
    fun `lock uses launcher lock callback`() = runTest {
        var locks = 0
        val handler = LauncherDesktopSwipeActionHandler(
            activity = mockk(),
            actionDispatcher = mockk(),
            screenshotLaunchers = emptySet(),
            onOpenAllApps = {},
            onNextScreen = {},
            onPreviousScreen = {},
            onBlackScreen = {},
            onLockScreen = { locks++ },
        )
        handler.handle(configured, "")
        assertEquals(1, locks)
    }
}
