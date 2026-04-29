package com.sza.fastmediasorter.data.link

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0003 — strategic §5.1 pillar E: writes a downloaded stream into the configured
 * destination resource (Local/SMB/SFTP/FTP/Cloud) via [FileOperationUseCase], or
 * falls back to MediaStore Downloads when the resource is missing/unavailable.
 *
 * Mirrors the pattern from [com.sza.fastmediasorter.ui.player.helpers.SaveVideoFrameManager].
 */
@Singleton
class LinkDownloadWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
) {

    sealed interface WriteResult {
        data class Saved(val resourceLabel: String, val fileName: String, val destinationUri: Uri?) : WriteResult
        data class FellBackToDownloads(val fileName: String, val reason: FallbackReason, val destinationUri: Uri?) : WriteResult
        data class Failed(val cause: Throwable) : WriteResult
    }

    enum class FallbackReason {
        NoResourceConfigured,
        ResourceUnavailable,
        ResourceWriteFailed,
    }

    suspend fun writeFromStream(
        stream: InputStream,
        mime: String,
        suggestedFileName: String,
        resourceId: Long?,
        onBytesCopied: (Long) -> Unit,
    ): WriteResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "link_downloads").also { it.mkdirs() }
        val fileName = sanitiseFileName(suggestedFileName)
        val tempFile = uniqueFile(tempDir, fileName)

        try {
            FileOutputStream(tempFile).use { out ->
                val buffer = ByteArray(64 * 1024)
                var totalBytes = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    totalBytes += read
                    onBytesCopied(totalBytes)
                }
            }

            // Step 2/3: try the configured resource, else fall through to Downloads.
            if (resourceId != null) {
                val destinations = getDestinationsUseCase.invoke().first()
                val resource = destinations.find { it.id == resourceId }
                if (resource == null) {
                    Timber.w("LinkDownloadWriter: resource id=%d not found, falling back to Downloads", resourceId)
                    val uri = saveToDownloads(tempFile, fileName, mime)
                    return@withContext WriteResult.FellBackToDownloads(fileName, FallbackReason.ResourceUnavailable, uri)
                }
                val operation = FileOperation.Copy(
                    sources = listOf(tempFile),
                    destination = File(resource.path),
                    overwrite = false,
                )
                when (val result = fileOperationUseCase.execute(operation)) {
                    is FileOperationResult.Success -> {
                        Timber.i("LinkDownloadWriter: saved '%s' to resource '%s'", fileName, resource.name)
                        return@withContext WriteResult.Saved(resource.name, fileName, destinationUri = null)
                    }
                    is FileOperationResult.AuthenticationRequired -> {
                        Timber.w("LinkDownloadWriter: auth required for '%s' (%s)", resource.name, result.provider)
                        val uri = saveToDownloads(tempFile, fileName, mime)
                        return@withContext WriteResult.FellBackToDownloads(fileName, FallbackReason.ResourceUnavailable, uri)
                    }
                    is FileOperationResult.Failure -> {
                        Timber.e("LinkDownloadWriter: copy failed for '%s': %s", resource.name, result.error)
                        val uri = saveToDownloads(tempFile, fileName, mime)
                        return@withContext WriteResult.FellBackToDownloads(fileName, FallbackReason.ResourceWriteFailed, uri)
                    }
                    else -> {
                        Timber.w("LinkDownloadWriter: unexpected result %s for '%s'", result::class.simpleName, resource.name)
                        val uri = saveToDownloads(tempFile, fileName, mime)
                        return@withContext WriteResult.FellBackToDownloads(fileName, FallbackReason.ResourceWriteFailed, uri)
                    }
                }
            } else {
                val uri = saveToDownloads(tempFile, fileName, mime)
                return@withContext WriteResult.FellBackToDownloads(fileName, FallbackReason.NoResourceConfigured, uri)
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Timber.e(t, "LinkDownloadWriter: write failed")
            return@withContext WriteResult.Failed(t)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    /**
     * Duplicated logic from `SaveVideoFrameManager.saveToDownloads` — see S0003 phase 05.
     */
    private fun saveToDownloads(tempFile: File, fileName: String, mime: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                resolver.query(queryUri, projection, selection, arrayOf(fileName), null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        resolver.delete(ContentUris.withAppendedId(queryUri, id), null, null)
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(queryUri, values) ?: throw IOException("MediaStore insert returned null")
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tempFile).use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Timber.i("LinkDownloadWriter: saved '%s' to Downloads via MediaStore", fileName)
                uri
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                val target = uniqueFile(downloadsDir, fileName)
                tempFile.copyTo(target, overwrite = false)
                Uri.fromFile(target)
            }
        } catch (e: Exception) {
            Timber.e(e, "LinkDownloadWriter: failed to save to Downloads")
            null
        }
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        val suffix = System.currentTimeMillis()
        return if (ext.isBlank()) File(dir, "${base}_$suffix") else File(dir, "${base}_$suffix.$ext")
    }

    private fun sanitiseFileName(value: String): String {
        return value.replace(Regex("[^a-zA-Z0-9_.\\-]"), "_").take(120).ifBlank { "download.bin" }
    }
}
