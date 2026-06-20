package com.sza.fastmediasorter.domain.usecase

import android.os.Environment
import com.sza.fastmediasorter.data.capture.CameraCaptureSaver
import com.sza.fastmediasorter.data.capture.CameraCaptureTarget
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import java.io.File
import javax.inject.Inject

/**
 * Single source of truth for persisting a freshly captured file from a generic-result camera entry
 * point (the main-menu "Camera" action and the home-screen camera launch widget) into the device's
 * public folders by the actually-captured media kind: photo -> DCIM/Camera, video -> Movies.
 *
 * The destination is fixed (public folders, never a sorting resource), so [CameraCaptureSaver]'s
 * network/upload hook is unused. A photo routes through [CameraCaptureTarget.CameraFolder]; a video
 * routes through a LOCAL [CameraCaptureTarget.Resource] over the public Movies directory, because the
 * camera-folder target always lands in DCIM/Camera regardless of media kind.
 */
class SaveCapturedMediaUseCase @Inject constructor(
    private val cameraCaptureSaver: CameraCaptureSaver,
) {

    suspend operator fun invoke(captured: File, isVideo: Boolean): SaveResult {
        val target: CameraCaptureTarget = if (isVideo) moviesTarget() else CameraCaptureTarget.CameraFolder
        return cameraCaptureSaver.save(captured, captured.name, target) { _, _, _ -> false }
    }

    /** Public Movies folder target (mirrors BrowseCameraCaptureManager.localVideoFallbackTarget). */
    private fun moviesTarget(): CameraCaptureTarget.Resource {
        val moviesDir = CaptureDestinationPolicy.resolveVideoDestination(null).also { it.mkdirs() }
        return CameraCaptureTarget.Resource(
            id = -1L,
            name = Environment.DIRECTORY_MOVIES,
            path = moviesDir.absolutePath,
            type = ResourceType.LOCAL,
        )
    }
}
