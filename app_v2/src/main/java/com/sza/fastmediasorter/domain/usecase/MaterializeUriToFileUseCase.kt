package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Outcome of copying an external [Uri] into a private cache file.
 * [Failed] means the URI was unreadable or produced an empty file.
 */
sealed interface UriMaterialization {
    data class Materialized(val absolutePath: String) : UriMaterialization
    data object Failed : UriMaterialization
}

/**
 * Fallback for [ResolveLocalPathFromUriUseCase.NotLocal]: copies a readable content/file URI into a
 * private cache file so operations that require a real filesystem source (crop-to-file, compress)
 * can run on images shared from another app that expose no resolvable path. The materialized file is
 * a throwaway copy - operations save their result as a new file, never back to the original URI.
 */
class MaterializeUriToFileUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend operator fun invoke(uri: Uri, displayName: String?): UriMaterialization =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, SUBFOLDER).apply { mkdirs() }
                val ext = resolveExtension(uri, displayName)
                val outFile = File(dir, "edit_${System.nanoTime()}.$ext")
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                when {
                    copied == null -> UriMaterialization.Failed
                    outFile.length() == 0L -> {
                        outFile.delete()
                        UriMaterialization.Failed
                    }
                    else -> UriMaterialization.Materialized(outFile.absolutePath)
                }
            } catch (e: Exception) {
                Timber.w(e, "MaterializeUriToFile: failed to copy %s", uri)
                UriMaterialization.Failed
            }
        }

    /** Delete a single materialized file (safe to call when it is already gone). */
    fun cleanup(absolutePath: String) {
        runCatching {
            val file = File(absolutePath)
            if (file.parentFile?.name == SUBFOLDER) file.delete()
        }
    }

    /** Drop the whole materialization cache subfolder. */
    fun cleanupAll() {
        runCatching { File(context.cacheDir, SUBFOLDER).deleteRecursively() }
    }

    private fun resolveExtension(uri: Uri, displayName: String?): String {
        displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }?.let { return it }
        val mime = context.contentResolver.getType(uri)
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it }
        return "jpg"
    }

    private companion object {
        const val SUBFOLDER = "standalone_edit"
    }
}
