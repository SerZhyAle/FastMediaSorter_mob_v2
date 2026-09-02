package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.data.transfer.local.MediaStoreLocalDestinationWriter
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SaveFallbackReason
import com.sza.fastmediasorter.domain.stats.CaptureKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.util.CaptureFileNamer
import com.sza.fastmediasorter.util.ScreenshotDestinationPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

class SaveScreenshotUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileOperationUseCase: FileOperationUseCase,
    private val mediaStoreLocalDestinationWriter: MediaStoreLocalDestinationWriter,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink
) {

    sealed interface SaveResult {
        data class Success(
            val fileName: String,
            val destinationLabel: String,
            val savedUri: Uri?,
            // Non-null when the screenshot was redirected to a local folder because the selected
            // network resource was unreachable; the caller turns this into a user notification.
            val fallbackReason: SaveFallbackReason? = null
        ) : SaveResult

        data class Failure(val error: Throwable) : SaveResult
    }

    suspend operator fun invoke(
        bitmap: Bitmap,
        target: ScreenshotDestinationPolicy.Target
    ): SaveResult = withContext(Dispatchers.IO) {
        val fileName = CaptureFileNamer.shared.allocate(CaptureFileNamer.CaptureKind.SCREENSHOT, ".png")
        val tempDir = File(context.cacheDir, TEMP_DIR_NAME)
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            return@withContext SaveResult.Failure(
                IOException("Failed to create temp dir: ${tempDir.absolutePath}")
            )
        }
        val tempFile = File(tempDir, fileName)

        try {
            writeTempPng(bitmap, tempFile)
            val result = when (target) {
                is ScreenshotDestinationPolicy.Target.SelectedResource ->
                    saveToSelectedResource(tempFile, fileName, target.resource)
                is ScreenshotDestinationPolicy.Target.PublicCollection ->
                    saveToPublicCollection(tempFile, fileName, target)
            }
            // S0473: one gesture-captured screenshot saved.
            if (result is SaveResult.Success) statsSink.record(StatsEvent.Capture(CaptureKind.SCREENSHOT))
            result
        } catch (e: Exception) {
            SaveResult.Failure(e)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun writeTempPng(bitmap: Bitmap, tempFile: File) {
        FileOutputStream(tempFile).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)) {
                throw IOException("Bitmap.compress returned false for ${tempFile.name}")
            }
        }
    }

    private suspend fun saveToSelectedResource(
        tempFile: File,
        fileName: String,
        resource: MediaResource
    ): SaveResult {
        val operation = FileOperation.Copy(
            sources = listOf(tempFile),
            destination = File(resource.path),
            overwrite = true
        )
        return when (val result = fileOperationUseCase.execute(operation)) {
            is FileOperationResult.Success ->
                SaveResult.Success(
                    fileName = fileName,
                    destinationLabel = resource.name,
                    // Null when the destination path lies outside FileProvider scope (e.g. a network
                    // resource); post-capture actions then degrade to the silent save already done.
                    savedUri = runCatching {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            File(resource.path, fileName)
                        )
                    }.getOrNull()
                )
            is FileOperationResult.Failure ->
                SaveResult.Failure(IOException(result.error))
            is FileOperationResult.PartialSuccess ->
                SaveResult.Failure(IOException(result.errors.joinToString(separator = "; ")))
            is FileOperationResult.AuthenticationRequired ->
                SaveResult.Failure(SecurityException(result.message))
            is FileOperationResult.PermissionRequired ->
                SaveResult.Failure(SecurityException("Permission required for screenshot save"))
        }
    }

    private suspend fun saveToPublicCollection(
        tempFile: File,
        fileName: String,
        target: ScreenshotDestinationPolicy.Target.PublicCollection
    ): SaveResult {
        val destination = LocalDestinationCategory.PublicCollection(
            collection = target.collection,
            relativePath = target.relativePath,
            displayName = fileName,
            mimeType = PNG_MIME_TYPE
        )
        val sink = mediaStoreLocalDestinationWriter
            .open(destination, overwrite = true)
            .getOrElse { return SaveResult.Failure(it) }

        try {
            FileInputStream(tempFile).use { input ->
                input.copyTo(sink.outputStream)
            }
        } catch (e: Exception) {
            sink.abort()
            return SaveResult.Failure(e)
        }

        return sink.commit()
            .fold(
                onSuccess = { committedUriString ->
                    SaveResult.Success(
                        fileName = fileName,
                        destinationLabel = publicFolderLabel(target.relativePath),
                        savedUri = Uri.parse(committedUriString),
                        fallbackReason = target.fallbackReason
                    )
                },
                onFailure = { SaveResult.Failure(it) }
            )
    }

    private fun publicFolderLabel(relativePath: String): String {
        val normalized = relativePath.trim('/')
        if (normalized.equals(Environment.DIRECTORY_DOWNLOADS, ignoreCase = true)) {
            return "Downloads"
        }
        return normalized.substringAfterLast('/').ifBlank { "Screenshots" }
    }

    companion object {
        private const val TEMP_DIR_NAME = "screenshot_capture"
        private const val PNG_QUALITY = 100
        private const val PNG_MIME_TYPE = "image/png"
    }
}
