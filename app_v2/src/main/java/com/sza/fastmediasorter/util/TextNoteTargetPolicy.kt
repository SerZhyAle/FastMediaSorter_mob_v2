package com.sza.fastmediasorter.util

import android.os.Environment
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import java.io.File

/**
 * Centralizes S0189 visibility and target-path rules for creating text notes from Browse.
 */
object TextNoteTargetPolicy {
    fun canCreateTextNote(resource: MediaResource?): Boolean {
        if (resource == null || resource.isReadOnly || !resource.supportsDocuments()) return false
        return !VirtualPathUtils.isVirtualPath(resource.path) || isAllDocuments(resource)
    }

    fun resolveParentPath(resource: MediaResource, currentPath: String?): String {
        val candidate = currentPath ?: resource.path
        return if (isAllDocuments(resource) && VirtualPathUtils.isVirtualPath(candidate)) {
            publicDocumentsDirectory().absolutePath
        } else {
            candidate
        }
    }

    fun ensureFallbackDirectoryIfNeeded(resource: MediaResource, parentPath: String): Result<Unit> {
        if (!isAllDocuments(resource) || parentPath != publicDocumentsDirectory().absolutePath) {
            return Result.success(Unit)
        }

        val dir = publicDocumentsDirectory()
        if (dir.exists() && dir.isDirectory) return Result.success(Unit)
        return if (dir.mkdirs() || (dir.exists() && dir.isDirectory)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Documents directory is not available: ${dir.absolutePath}"))
        }
    }

    private fun isAllDocuments(resource: MediaResource): Boolean =
        resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS

    private fun publicDocumentsDirectory(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
}
