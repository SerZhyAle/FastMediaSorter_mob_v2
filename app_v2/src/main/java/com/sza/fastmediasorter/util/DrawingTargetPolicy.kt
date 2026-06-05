package com.sza.fastmediasorter.util

import android.os.Environment
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import java.io.File

/**
 * Centralizes S0363 visibility and target-path rules for creating drawings from Browse.
 *
 * Mirrors [TextNoteTargetPolicy]: a drawing may be created on a real image folder, or on one of the
 * two allow-listed virtual image resources - "all images" and "camera". "All images" has no own
 * directory, so its drawings are written to the public Downloads folder; "camera" drawings go to
 * the public DCIM/Camera folder, falling back to Downloads when that folder is unavailable.
 */
object DrawingTargetPolicy {
    fun canCreateDrawing(resource: MediaResource?): Boolean {
        if (resource == null || resource.isReadOnly || !resource.supportsImages()) return false
        return !VirtualPathUtils.isVirtualPath(resource.path) || isAllowedVirtual(resource)
    }

    fun resolveParentPath(resource: MediaResource, currentPath: String?): String = when {
        isAllImages(resource) -> publicDownloadsDirectory().absolutePath
        isCameraPhotos(resource) -> resolveCameraDirectory().absolutePath
        else -> currentPath ?: resource.path
    }

    fun ensureFallbackDirectoryIfNeeded(resource: MediaResource, parentPath: String): Result<Unit> {
        if (!isAllowedVirtual(resource)) return Result.success(Unit)

        val dir = File(parentPath)
        if (dir.exists() && dir.isDirectory) return Result.success(Unit)
        return if (dir.mkdirs() || (dir.exists() && dir.isDirectory)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Target directory is not available: $parentPath"))
        }
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

    private fun isAllowedVirtual(resource: MediaResource): Boolean =
        isAllImages(resource) || isCameraPhotos(resource)

    private fun isAllImages(resource: MediaResource): Boolean =
        resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES

    private fun isCameraPhotos(resource: MediaResource): Boolean =
        resource.path == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS

    private fun publicDownloadsDirectory(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
}
