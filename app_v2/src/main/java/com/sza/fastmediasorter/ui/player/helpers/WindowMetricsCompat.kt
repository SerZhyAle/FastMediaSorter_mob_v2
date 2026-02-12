package com.sza.fastmediasorter.ui.player.helpers

import android.os.Build
import android.view.WindowManager

/**
 * Compatibility helper for getting window metrics on Android 9+
 * Handles API differences between Android 9-10 and Android 11+
 */
object WindowMetricsCompat {
    
    /**
     * Get device screen width in pixels
     * Compatible with API 28+
     */
    fun getScreenWidth(windowManager: WindowManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ (Android 11+): Use WindowMetrics
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            // API 28-29 (Android 9-10): Use deprecated DisplayMetrics
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.let { display ->
                android.graphics.Point().also { size ->
                    display.getRealSize(size)
                }.x
            }
        }
    }
    
    /**
     * Get device screen height in pixels
     * Compatible with API 28+
     */
    fun getScreenHeight(windowManager: WindowManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ (Android 11+): Use WindowMetrics
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            // API 28-29 (Android 9-10): Use deprecated DisplayMetrics
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.let { display ->
                android.graphics.Point().also { size ->
                    display.getRealSize(size)
                }.y
            }
        }
    }
    
    /**
     * Get both width and height as Pair
     * Compatible with API 28+
     */
    fun getScreenSize(windowManager: WindowManager): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ (Android 11+): Use WindowMetrics
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            // API 28-29 (Android 9-10): Use deprecated DisplayMetrics
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.let { display ->
                android.graphics.Point().also { size ->
                    display.getRealSize(size)
                }.let { it.x to it.y }
            }
        }
    }
}
