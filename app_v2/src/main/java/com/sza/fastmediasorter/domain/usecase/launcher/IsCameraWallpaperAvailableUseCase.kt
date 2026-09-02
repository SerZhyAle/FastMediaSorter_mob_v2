package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.data.capture.CameraHardwareDataSource
import javax.inject.Inject

/**
 * S2076: whether the launcher desktop can actually open a camera for its live background.
 *
 * Two questions with different lifetimes, so they are answered separately: hardware never changes on a
 * device, while the CAMERA grant can be revoked between one desktop draw and the next. The settings
 * dropdown asks [hasHardware] to decide whether offering the mode makes sense at all; the wallpaper
 * resolver asks [invoke] every time it maps the stored token, so a revoked grant degrades to the branded
 * backdrop instead of leaving a black layer on screen (strategic ADR-2).
 */
class IsCameraWallpaperAvailableUseCase @Inject constructor(
    private val cameraHardware: CameraHardwareDataSource,
) {

    operator fun invoke(): Boolean = hasHardware() && cameraHardware.isCameraPermissionGranted()

    fun hasHardware(): Boolean = cameraHardware.hasAnyCamera()
}
