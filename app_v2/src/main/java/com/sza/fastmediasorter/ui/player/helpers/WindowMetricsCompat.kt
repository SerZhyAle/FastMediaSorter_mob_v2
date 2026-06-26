package com.sza.fastmediasorter.ui.player.helpers

import android.os.Build
import android.view.WindowManager

/**
 * S0702: returns the size of the current app WINDOW, not the full physical display.
 *
 * Callers (image scale-type and Glide decode caps) need the area the media is shown in, so in
 * split-screen / freeform the value must shrink to the window. API 30+ uses
 * [WindowManager.getCurrentWindowMetrics] bounds (window-aware); the legacy path uses
 * [android.view.Display.getSize] (the app-usable, multi-window-aware size) instead of `getRealSize`,
 * which returned the whole physical screen and broke multi-window scaling.
 */
object WindowMetricsCompat {

    /** Current app window width in pixels. */
    fun getScreenWidth(windowManager: WindowManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.let { display ->
                android.graphics.Point().also { size ->
                    display.getSize(size)
                }.x
            }
        }
    }

    /** Current app window height in pixels. */
    fun getScreenHeight(windowManager: WindowManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.let { display ->
                android.graphics.Point().also { size ->
                    display.getSize(size)
                }.y
            }
        }
    }

    /** Current app window size (width to height) in pixels. */
    fun getScreenSize(windowManager: WindowManager): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.let { display ->
                android.graphics.Point().also { size ->
                    display.getSize(size)
                }.let { it.x to it.y }
            }
        }
    }
}
