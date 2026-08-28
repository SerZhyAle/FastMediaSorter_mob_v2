package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1930: the whole non-collision argument rests on the two ranges being disjoint, so these are the
 * claims Phase 02's guards are allowed to assume - not a sample of them.
 */
class LauncherWidgetTokenTest {

    @Test
    fun `the invalid platform id is not a launcher token`() {
        assertFalse(LauncherWidgetToken.isLauncherToken(AppWidgetManager.INVALID_APPWIDGET_ID))
        assertFalse(LauncherWidgetToken.isLauncherToken(0))
    }

    @Test
    fun `platform ids are never launcher tokens`() {
        assertFalse(LauncherWidgetToken.isLauncherToken(1))
        assertFalse(LauncherWidgetToken.isLauncherToken(42))
        assertFalse(LauncherWidgetToken.isLauncherToken(Int.MAX_VALUE))
    }

    @Test
    fun `the no-token sentinel is not itself a token`() {
        assertFalse(LauncherWidgetToken.isLauncherToken(LauncherWidgetToken.NONE))
    }

    @Test
    fun `the top of the reserved range is a token`() {
        assertTrue(LauncherWidgetToken.isLauncherToken(LauncherWidgetToken.MAX_TOKEN))
    }

    @Test
    fun `the value just above the sentinel is a token`() {
        assertTrue(LauncherWidgetToken.isLauncherToken(LauncherWidgetToken.NONE + 1))
    }

    @Test
    fun `the reserved range sits entirely below the platform range`() {
        assertTrue(LauncherWidgetToken.MAX_TOKEN < AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    @Test
    fun `distinct sentinel and top of range`() {
        assertNotEquals(LauncherWidgetToken.NONE, LauncherWidgetToken.MAX_TOKEN)
    }

    /**
     * The platform is not the only allocator of ids that travel in `EXTRA_APPWIDGET_ID`. The app parks
     * its own hand-written sentinels there, and the first version of this range began at -1, so its
     * thousandth token would have been the app-launch panel's -1000 - a cell whose captures went to the
     * panel's camera folder instead of its own target, with no exception and no log.
     *
     * Every such value goes in the list below when it is introduced. A new one that lands inside the
     * token range fails here rather than in the field.
     */
    @Test
    fun `ids this app reserves for itself are not launcher tokens`() {
        RESERVED_APP_IDS.forEach { (name, id) ->
            assertFalse(
                "$name = $id falls inside the launcher token range, so a minted token can impersonate it",
                LauncherWidgetToken.isLauncherToken(id),
            )
        }
    }

    private companion object {
        val RESERVED_APP_IDS = listOf(
            "CameraQuickCaptureLaunchManager.PANEL_APP_WIDGET_ID" to
                CameraQuickCaptureLaunchManager.PANEL_APP_WIDGET_ID,
        )
    }
}
