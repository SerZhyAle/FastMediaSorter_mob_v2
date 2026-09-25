package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherDesktopSwipeActionTest {

    @Test
    fun `black screen round-trips through its persisted name`() {
        val restored = LauncherDesktopSwipeAction.fromName(
            LauncherDesktopSwipeAction.BlackScreen.persistedName,
            LauncherDesktopSwipeAction.OpenAllApps,
        )

        assertEquals(LauncherDesktopSwipeAction.BlackScreen, restored)
    }

    @Test
    fun `black screen token does not shadow an edge gesture action`() {
        val restored = LauncherDesktopSwipeAction.fromName(
            ScreenshotGestureAction.LOCK_SCREEN.name,
            LauncherDesktopSwipeAction.OpenAllApps,
        )

        assertEquals(
            LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.LOCK_SCREEN),
            restored,
        )
    }
}
