package com.sza.fastmediasorter.util

import android.os.Build
import android.os.Environment
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.allowsWriteOperations
import java.io.File

/**
 * Destination resolution for the capture flows (S0367/S0375/S0774/S0523), aligned with the
 * CAPTURE-OUTPUT contract rules 9 and 10: the user's selected resource wins, otherwise each kind
 * lands in its own public folder, and only a folder that cannot be created falls back to Downloads.
 *
 * Default folder per kind:
 * - camera photos: DCIM/Camera;
 * - camera and screen video recordings: Movies;
 * - voice recordings (every dictaphone entry point): Recordings on API 31+, Music below;
 * - video frames: Pictures/Frames;
 * - recognized text and translations: Documents.
 *
 * "Empty" is never an error - it deterministically resolves to the documented default.
 * A selected resource is honoured only when it is a real, writable, on-device folder
 * (`allowsWriteOperations() && !VirtualPathUtils.isVirtualPath`); a stale/invalid selection degrades
 * to the same default rather than failing the capture.
 *
 * Pure helper - no Android Context, no DI. Mirrors [DrawingTargetPolicy] for the device-media
 * folder resolution used by the capture flows.
 */
object CaptureDestinationPolicy {

    private const val CAMERA_SUBFOLDER = "Camera"
    private const val FRAMES_SUBFOLDER = "Frames"

    /** Voice recording: the selected usable folder, else the recordings folder. */
    fun resolveMicDestination(selectedResource: MediaResource?): File =
        usableTargetDirectory(selectedResource) ?: recordingsDirectory()

    /** Camera photo: the selected usable folder, else DCIM/Camera. */
    fun resolveCameraDestination(selectedResource: MediaResource?): File =
        usableTargetDirectory(selectedResource) ?: orDownloads(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), CAMERA_SUBFOLDER)
        )

    /** Camera video recording: the selected usable folder, else Movies. */
    fun resolveVideoDestination(selectedResource: MediaResource?): File =
        usableTargetDirectory(selectedResource) ?: publicMoviesDirectory()

    /** S0774 screen video recording: the selected usable folder, else Movies. */
    fun resolveScreenRecordingDestination(selectedResource: MediaResource?): File =
        usableTargetDirectory(selectedResource) ?: orDownloads(publicMoviesDirectory())

    /**
     * S0523 quick voice note from the main overflow menu: the same recordings folder as every other
     * dictaphone entry point. No resource parameter - the quick-capture entry never targets a sorting
     * resource.
     */
    fun resolveQuickVoiceDestination(): File = recordingsDirectory()

    /** Video frame: the selected usable folder, else Pictures/Frames. */
    fun resolveFrameDestination(selectedResource: MediaResource?): File =
        usableTargetDirectory(selectedResource) ?: orDownloads(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FRAMES_SUBFOLDER)
        )

    /** Recognized text and translation results: Documents. */
    fun resolveDocumentsDestination(): File =
        orDownloads(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))

    /** The one fallback folder of rule 9, used when a default or a chosen folder cannot be written. */
    fun downloadsDirectory(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** True when [resource] is a real, writable, on-device folder usable as a capture target. */
    fun isUsableTarget(resource: MediaResource?): Boolean {
        if (resource == null || !resource.allowsWriteOperations()) return false
        return !VirtualPathUtils.isVirtualPath(resource.path)
    }

    private fun usableTargetDirectory(resource: MediaResource?): File? {
        if (!isUsableTarget(resource)) return null
        return File(resource!!.path).takeIf(::existsOrCreated)
    }

    private fun recordingsDirectory(): File {
        // DIRECTORY_RECORDINGS exists only on API 31+; on older devices the dictaphone artifact goes
        // to the public Music folder, which also classifies as AUDIO.
        val preferred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
        } else {
            publicMusicDirectory()
        }
        return when {
            existsOrCreated(preferred) -> preferred
            existsOrCreated(publicMusicDirectory()) -> publicMusicDirectory()
            else -> downloadsDirectory()
        }
    }

    private fun orDownloads(dir: File): File = if (existsOrCreated(dir)) dir else downloadsDirectory()

    // A concurrent mkdirs() from another capture can return false while the folder now exists,
    // so the existence check runs again after it.
    private fun existsOrCreated(dir: File): Boolean =
        dir.isDirectory || dir.mkdirs() || dir.isDirectory

    private fun publicMoviesDirectory(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

    private fun publicMusicDirectory(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
}
