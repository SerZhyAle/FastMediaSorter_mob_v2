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
}
