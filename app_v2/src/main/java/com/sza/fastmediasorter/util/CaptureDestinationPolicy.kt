package com.sza.fastmediasorter.util

import android.os.Build
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
 * - screen recordings (S0774): selected writable target, else the public Downloads folder.
 * - quick voice notes (S0523, main-menu capture): always the public recordings folder
 *   (Recordings on API 31+, Music below) - no resource selection.
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

    /**
     * S0774: resolves the target directory for a screen video recording.
     * Returns the selected resource's folder when it is a usable writable target,
     * otherwise the public Downloads folder (owner-specified empty-selection fallback).
     */
    fun resolveScreenRecordingDestination(selectedResource: MediaResource?): File =
        usableTargetDirectory(selectedResource) ?: publicDownloadsDirectory()

    /**
     * S0523: resolves the target directory for a quick voice note captured from the main overflow
     * menu. Always a public folder - the phone's recordings collection on API 31+, the public Music
     * folder below (and as a fallback when the recordings folder cannot be created). No resource
     * parameter: the quick-capture entry never targets a sorting resource.
     */
    fun resolveQuickVoiceDestination(): File = publicRecordingsDirectory()

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

    private fun publicMusicDirectory(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

    private fun publicRecordingsDirectory(): File {
        // DIRECTORY_RECORDINGS exists only on API 31+; on older devices the dictaphone artifact goes
        // to the public Music folder (the in-repo precedent), which also classifies as AUDIO.
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
        } else {
            publicMusicDirectory()
        }
        val usable = (dir.exists() && dir.isDirectory) ||
            dir.mkdirs() ||
            (dir.exists() && dir.isDirectory)
        return if (usable) dir else publicMusicDirectory()
    }
}
