package com.sza.fastmediasorter.ui.flashlight.helpers

import android.content.Context
import com.sza.fastmediasorter.core.screencapture.gesture.DeviceActionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Keeps the short-lived launcher activity free of hardware-operation details. */
class FlashlightToggleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceActionHandler: DeviceActionHandler,
) {
    fun toggle() = deviceActionHandler.toggleFlashlight(context)
}
