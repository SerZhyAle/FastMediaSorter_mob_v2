package com.sza.fastmediasorter.data.cloud

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

/**
 * Owns inter-provider cloud file operations for CloudFileOperationHandler:
 * - delete by cloud path;
 * - copy (native same-provider; cross-provider download-to-temp + upload);
 * - move (native same-provider; cross-provider = copy + delete; also fallback when native move fails).
 *
 * Extracted to keep CloudFileOperationHandler below the 1000-line cap.
 */
class CloudToCloudTransferHelper(
    private val context: Context,
    private val cloudPathParser: CloudPathParser,
    private val cloudAuthHelper: CloudAuthenticationHelper
) {

    /** Delete file at a cloud path. Returns true on success. */
    suspend fun deleteFromCloud(cloudPath: String): Boolean {
        Timber.d("deleteFromCloud: $cloudPath")

        val pathInfo = cloudPathParser.parseCloudPath(cloudPath)
        if (pathInfo == null) {
            Timber.e("deleteFromCloud: Failed to parse cloud path: $cloudPath")
            return false
        }

        val result = cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client ->
            client.deleteFile(pathInfo.fileId)
        }

        return when (result) {
            is CloudResult.Success -> {
                Timber.i("deleteFromCloud: SUCCESS")
                true
            }
            is CloudResult.Error -> {
                Timber.e("deleteFromCloud: FAILED - ${result.message}")
                false
            }
            null -> {
                Timber.e("deleteFromCloud: Re-authentication failed or cancelled")
                false
            }
        }
    }

    /** Copy file between cloud folders (same or different providers). Returns destination path or null. */
    suspend fun copyCloudToCloud(sourcePath: String, destPath: String): String? {
        Timber.d("copyCloudToCloud: $sourcePath → $destPath")

        val sourceInfo = cloudPathParser.parseCloudPath(sourcePath)
        val destInfo = cloudPathParser.parseCloudPath(destPath)

        if (sourceInfo == null || destInfo == null) {
            Timber.e("copyCloudToCloud: Failed to parse paths")
            return null
        }

        // Same provider: native copy
        if (sourceInfo.provider == destInfo.provider) {
            val fileName = sourcePath.substringAfterLast('/')
            val result = cloudAuthHelper.executeWithAutoReauth(sourceInfo.provider) { client ->
                client.copyFile(sourceInfo.fileId, destInfo.folderId ?: "root", fileName)
            }

            return when (result) {
                is CloudResult.Success -> {
                    Timber.i("copyCloudToCloud: SUCCESS - native copy")
                    "cloud://${destInfo.provider}/${result.data.path}"
                }
                is CloudResult.Error -> {
                    Timber.e("copyCloudToCloud: Native copy FAILED - ${result.message}")
                    null
                }
                null -> {
                    Timber.e("copyCloudToCloud: Re-authentication failed or cancelled")
                    null
                }
            }
        }

        // Cross-provider: download to temp file, then upload
        Timber.d("copyCloudToCloud: Cross-provider copy via temp file")
        val sourceClient = cloudAuthHelper.getCloudClient(sourceInfo.provider) ?: return null
        val destClient = cloudAuthHelper.getCloudClient(destInfo.provider) ?: return null

        val tempFile = File.createTempFile("cloud_copy_", ".tmp", context.cacheDir)

        return try {
            val outputStream = tempFile.outputStream()
            val downloadResult = sourceClient.downloadFile(sourceInfo.fileId, outputStream, null)
            outputStream.close()

            when (downloadResult) {
                is CloudResult.Success -> {
                    Timber.d("copyCloudToCloud: Downloaded ${tempFile.length()} bytes from source to temp")

                    val fileName = sourcePath.substringAfterLast('/')
                    val mimeType = CloudFileOperationPathUtils.getMimeType(fileName)
                    val uploadResult = FileInputStream(tempFile).use { inputStream ->
                        destClient.uploadFile(
                            inputStream = inputStream,
                            fileName = fileName,
                            mimeType = mimeType,
                            parentFolderId = destInfo.folderId,
                            fileSize = tempFile.length(),
                            progressCallback = null
                        )
                    }

                    when (uploadResult) {
                        is CloudResult.Success -> {
                            Timber.i("copyCloudToCloud: SUCCESS - ${tempFile.length()} bytes copied between providers")
                            "cloud://${destInfo.provider}/${uploadResult.data.path}"
                        }
                        is CloudResult.Error -> {
                            Timber.e("copyCloudToCloud: Upload FAILED - ${uploadResult.message}")
                            null
                        }
                    }
                }
                is CloudResult.Error -> {
                    Timber.e("copyCloudToCloud: Download FAILED - ${downloadResult.message}")
                    null
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Move file between cloud folders. Uses native move for same-provider; for cross-provider
     * (or when native move fails) falls back to copy + delete.
     */
    suspend fun moveCloudToCloud(sourcePath: String, destPath: String): String? {
        Timber.d("moveCloudToCloud: $sourcePath → $destPath")

        val sourceInfo = cloudPathParser.parseCloudPath(sourcePath)
        val destInfo = cloudPathParser.parseCloudPath(destPath)

        if (sourceInfo == null || destInfo == null) {
            Timber.e("moveCloudToCloud: Failed to parse paths")
            return null
        }

        // Cross-provider: fall back to copy + delete (no native cross-provider move API)
        if (sourceInfo.provider != destInfo.provider) {
            Timber.w("moveCloudToCloud: Cross-provider move not supported, fallback to copy+delete")
            val copied = copyCloudToCloud(sourcePath, destPath)
            return if (copied != null && deleteFromCloud(sourcePath)) copied else null
        }

        val result = cloudAuthHelper.executeWithAutoReauth(sourceInfo.provider) { client ->
            client.moveFile(sourceInfo.fileId, destInfo.folderId ?: "root")
        }

        return when (result) {
            is CloudResult.Success -> {
                Timber.i("moveCloudToCloud: SUCCESS - native move")
                "cloud://${destInfo.provider}/${result.data.path}"
            }
            is CloudResult.Error -> {
                Timber.w("moveCloudToCloud: Native move failed (${result.message}), fallback to copy+delete")
                val copied = copyCloudToCloud(sourcePath, destPath)
                if (copied != null && deleteFromCloud(sourcePath)) {
                    Timber.i("moveCloudToCloud: SUCCESS - fallback copy+delete")
                    copied
                } else {
                    Timber.e("moveCloudToCloud: FAILED - fallback copy+delete unsuccessful")
                    null
                }
            }
            null -> {
                Timber.e("moveCloudToCloud: Re-authentication failed or cancelled")
                null
            }
        }
    }
}
