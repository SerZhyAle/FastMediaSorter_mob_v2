package com.sza.fastmediasorter.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S0406: exports the unified backup payload to a JSON file in Downloads.
 * Same structure as the Google Drive backup - both build the payload via
 * [BuildBackupPayloadUseCase]. Replaces the legacy hand-rolled XML export.
 */
class ExportSettingsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val buildBackupPayloadUseCase: BuildBackupPayloadUseCase
) {
    companion object {
        const val EXPORT_FILE_NAME = "FastMediaSorter_backup.json"
        private const val MIME_JSON = "application/json"
    }

    suspend operator fun invoke(): Result<String> {
        return try {
            Timber.d("S0406: export unified backup to local JSON file")
            val payload = buildBackupPayloadUseCase()
            val json = GsonBuilder().setPrettyPrinting().create().toJson(payload)
            val exportPath = writeToDownloads(json, EXPORT_FILE_NAME)
            Timber.i("Settings exported to: %s", exportPath)
            Result.success(exportPath)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export settings")
            Result.failure(e)
        }
    }

    /**
     * Write content to Downloads using the appropriate API for the Android version.
     * MediaStore for Android 10+ (API 29+), direct file access below.
     * Returns the content URI (Android 10+) or file path (older).
     */
    private fun writeToDownloads(content: String, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, MIME_JSON)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            // Delete existing file with same name (overwrite).
            resolver.delete(
                collection,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName)
            )

            val uri = resolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Failed to create file in Downloads")

            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Failed to open output stream")

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            Timber.i("Settings exported successfully to URI: %s", uri)
            uri.toString()
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportFile = File(downloadsDir, fileName)
            exportFile.writeText(content)
            MediaStoreNotifier.notifyFile(context, exportFile.absolutePath, "settings-export")
            exportFile.absolutePath
        }
    }
}
