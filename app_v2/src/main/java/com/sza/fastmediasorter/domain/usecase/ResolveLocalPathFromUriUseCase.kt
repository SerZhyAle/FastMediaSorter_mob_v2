package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Outcome of resolving an external [Uri] to a filesystem location.
 * [NotLocal] means no real path is reachable (network / SAF-only / unresolvable provider).
 */
sealed interface LocalPathResolution {
    data class Local(val absolutePath: String, val parentFolderPath: String) : LocalPathResolution
    data object NotLocal : LocalPathResolution
}

/**
 * Single source of truth for "is this external file local, and where".
 * Standalone folder paging and the Open-in-FMS resolver both gate on a non-[NotLocal] result.
 */
class ResolveLocalPathFromUriUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend operator fun invoke(uri: Uri): LocalPathResolution = withContext(Dispatchers.IO) {
        val absolutePath = resolveAbsolutePath(uri)
        if (absolutePath.isNullOrBlank()) {
            LocalPathResolution.NotLocal
        } else {
            val parent = File(absolutePath).parent
            if (parent.isNullOrBlank()) {
                LocalPathResolution.NotLocal
            } else {
                LocalPathResolution.Local(absolutePath, parent)
            }
        }
    }

    private fun resolveAbsolutePath(uri: Uri): String? = when (uri.scheme) {
        SCHEME_FILE -> uri.path?.takeIf { File(it).exists() }
        SCHEME_CONTENT -> resolveContentPath(uri)
        // A bare path with no scheme (legacy callers) is local only if it points to a real file.
        null -> uri.path?.takeIf { it.startsWith("/") && File(it).exists() }
        else -> null
    }

    private fun resolveContentPath(uri: Uri): String? {
        resolveViaDataColumn(uri)?.let { return it }
        return resolveViaDocumentId(uri)
    }

    @Suppress("DEPRECATION")
    private fun resolveViaDataColumn(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() && File(it).exists() }

    private fun resolveViaDocumentId(uri: Uri): String? {
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val parts = documentId.split(':', limit = 2)
        if (parts.size < 2) return null
        // Downloads provider encodes the real filesystem path directly as `raw:/abs/path`, so the
        // embedded path is authoritative and needs no volume mapping.
        if (parts[0].equals(RAW_DOCUMENT_PREFIX, ignoreCase = true)) {
            val rawFile = File(parts[1])
            return parts[1].takeIf { rawFile.isAbsolute && rawFile.exists() }
        }
        // Only the primary external volume maps deterministically to a filesystem path; other
        // volumes (SD card, USB, provider-specific ids) stay NotLocal.
        if (!parts[0].equals(PRIMARY_VOLUME, ignoreCase = true)) return null
        val root = runCatching { Environment.getExternalStorageDirectory()?.absolutePath }
            .getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        return "$root${File.separator}${parts[1]}".takeIf { File(it).exists() }
    }

    private companion object {
        private const val SCHEME_FILE = "file"
        private const val SCHEME_CONTENT = "content"
        private const val PRIMARY_VOLUME = "primary"
        private const val RAW_DOCUMENT_PREFIX = "raw"
    }
}
