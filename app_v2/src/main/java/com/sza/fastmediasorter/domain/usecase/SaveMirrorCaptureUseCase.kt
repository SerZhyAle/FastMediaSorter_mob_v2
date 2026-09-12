package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.capture.CameraCaptureSaver
import com.sza.fastmediasorter.data.capture.CameraCaptureTarget
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Puts what the mirror captures where the capture screen would have put it (strategic S1924 6.1, owner
 * decision 2026-09-05): the same main capture folder, so a selfie taken here is not hunted for somewhere
 * else. ADR-1 forbids a second save backend beside [CameraCaptureSaver].
 *
 * Distinct from [SaveCapturedMediaUseCase], which pins its output to the public folders for the
 * generic-result camera entry points: this one honours the user's configured photo and video
 * destinations, which is the whole of what 6.1 decided.
 */
class SaveMirrorCaptureUseCase @Inject constructor(
    private val cameraCaptureSaver: CameraCaptureSaver,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
) {

    /**
     * The saved file keeps the temp file's own name, which the caller already allocated through
     * `CaptureFileNamer`. Allocating a second name here would burn an ordinal per shot and let the file
     * the camera wrote and the file the user finds be called different things.
     */
    suspend operator fun invoke(tempFile: File, isVideo: Boolean): Boolean {
        val target = resolveTarget(configuredDestination(isVideo))
        // The mirror carries no transfer backend, and resolveTarget admits only on-device targets, so a
        // network resource can never reach this lambda - refusing here would hide a routing bug.
        val result = cameraCaptureSaver.save(tempFile, tempFile.name, target) { _, _, _ -> false }
        if (result !is SaveResult.Success) {
            Timber.w("SaveMirrorCaptureUseCase: save failed target=%s result=%s", target, result)
        }
        return result is SaveResult.Success
    }

    private suspend fun configuredDestination(isVideo: Boolean): MediaResource? {
        val settings = settingsRepository.getSettings().first()
        val rawId = if (isVideo) {
            settings.videoRecordingDestinationResourceId
        } else {
            settings.cameraPhotosDestinationResourceId
        }
        val id = rawId?.toLongOrNull() ?: return null
        return resourceRepository.getResourceById(id)
    }

    /**
     * A configured destination is honoured only when it is a real, writable, on-device folder. The
     * capture screen can fall back to an upload for a network resource; the mirror cannot, so a network
     * or unusable selection degrades to DCIM/Camera rather than failing the shot.
     */
    private fun resolveTarget(configured: MediaResource?): CameraCaptureTarget {
        val usable = configured?.takeIf {
            it.type == ResourceType.LOCAL && CaptureDestinationPolicy.isUsableTarget(it)
        }
        return usable?.let {
            CameraCaptureTarget.Resource(
                id = it.id,
                name = it.name,
                path = it.path,
                type = it.type,
            )
        } ?: CameraCaptureTarget.CameraFolder
    }
}
