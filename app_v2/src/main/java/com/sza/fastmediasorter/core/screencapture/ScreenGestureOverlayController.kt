package com.sza.fastmediasorter.core.screencapture

import android.content.Context

interface ScreenGestureOverlayController {
    fun isOverlayPermissionGranted(context: Context): Boolean
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
}
