package com.sza.fastmediasorter.util

import android.os.Environment
import com.sza.fastmediasorter.domain.model.MediaResource
import java.io.File

/**
 * S0367/S0375 destination-resolution contract for the playback-adjacent capture flows
 * (microphone recordings, camera photos, and video recordings) configured under Settings →
 * Playback → "Camera, microphone and Other features".
 *
 * Each resolver takes the user-selected destination resource (or null when the selector is empty)
 * and returns the concrete target directory:
 * - microphone recordings: selected writable target, else the public Downloads folder;
 * - camera photos: selected writable target, else the device camera folder (DCIM/Camera),
 *   falling back to Downloads when that folder is unavailable.
 * - video recordings: selected writable target, else the public Movies folder.
 *
 * "Empty" is never an error - it deterministically resolves to the documented fallback.
 * A selected resource is honoured only when it is a real, writable, on-device folder
 * (`!isReadOnly && !VirtualPathUtils.isVirtualPath`); a stale/invalid selection silently
 * degrades to the same fallback rather than failing the capture.
 *
 * Pure helper - no Android Context, no DI. Mirrors [DrawingTargetPolicy] for the device-media
 * folder resolution used by the capture flows.
 */
object CaptureDestinationPolicy {

    /**
     * Resolves the target directory for a microphone recording.
     * Returns the selected resource's folder when it is a usable writable target,
     * otherwise the public Downloads folder.
     */
    fun resolveMicDestination(selectedResource: MediaResource?): File =
        usableTargetDirectory(selectedResource) ?: publicDownloadsDirectory()

    /**
     * Resolves the target directory for a camera photo.
     * Returns the selected resource's folder when it is a usable writable target,
     * otherwise the device camera folder (DCIM/Camera), falling back to Downloads
     * when the camera folder cannot be created/accessed.
     */
    fun resolveCameraDestination(selectedResource: MediaResource?): File =
        usableTargetDirectory(selectedResource) ?: resolveCameraDirectory()

    /**
     * Resolves the target directory for a video recording.
     * Returns the selected resource's folder when it is a usable writable target,
     * otherwise the public Movies folder.
     */
    fun resolveVideoDestination(selectedResource: MediaResource?): File =
        usableTargetDirectory(selectedResource) ?: publicMoviesDirectory()

    /** True when [resource] is a real, writable, on-device folder usable as a capture target. */
    fun isUsableTarget(resource: MediaResource?): Boolean {
        if (resource == null || resource.isReadOnly) return false
        return !VirtualPathUtils.isVirtualPath(resource.path)
    }

    private fun usableTargetDirectory(resource: MediaResource?): File? {
        if (!isUsableTarget(resource)) return null
        val dir = File(resource!!.path)
        // Only honour a selection that resolves to an existing directory or one we can create;
        // a missing/invalid path degrades to the caller's fallback instead of failing the capture.
        val usable = (dir.exists() && dir.isDirectory) ||
            dir.mkdirs() ||
            (dir.exists() && dir.isDirectory)
        return if (usable) dir else null
    }

    private fun resolveCameraDirectory(): File {
        val cameraDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "Camera"
        )
        val usable = (cameraDir.exists() && cameraDir.isDirectory) ||
            cameraDir.mkdirs() ||
            (cameraDir.exists() && cameraDir.isDirectory)
        return if (usable) cameraDir else publicDownloadsDirectory()
    }

    private fun publicDownloadsDirectory(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    private fun publicMoviesDirectory(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
}
