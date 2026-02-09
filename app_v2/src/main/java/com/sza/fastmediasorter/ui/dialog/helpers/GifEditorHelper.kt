package com.sza.fastmediasorter.ui.dialog.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Helper class for GIF editing operations
 * Handles network file downloads, cleanup, and messaging
 */
class GifEditorHelper(
    private val context: Context,
    private val downloadNetworkFileUseCase: DownloadNetworkFileUseCase
) {
    private var downloadedTempFile: File? = null
    
    /**
     * Check if path is a network file (SMB/S/FTP/Cloud)
     */
    fun isNetworkPath(path: String): Boolean {
        return path.startsWith("smb://") || 
               path.startsWith("sftp://") || 
               path.startsWith("ftp://") || 
               path.startsWith("cloud://")
    }
    
    /**
     * Prepare GIF file for editing
     * - For local files: returns original path
     * - For network files: downloads to temp cache and returns temp path
     * - Caches downloaded file to avoid re-downloading
     */
    suspend fun prepareGifFile(
        gifPath: String,
        onProgress: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        if (!isNetworkPath(gifPath)) {
            return@withContext gifPath // Already local
        }
        
        // Check if already downloaded
        downloadedTempFile?.let {
            if (it.exists()) {
                Timber.d("GifEditorHelper: Using cached temp file: ${it.absolutePath}")
                return@withContext it.absolutePath
            }
        }
        
        // Update progress to show downloading
        withContext(Dispatchers.Main) {
            onProgress(context.getString(R.string.msg_gif_downloading))
        }
        
        // Download to temp cache
        val tempDir = context.cacheDir
        val fileName = gifPath.substringAfterLast('/').substringBefore('?')
        val tempFile = File(tempDir, "gif_edit_${System.currentTimeMillis()}_$fileName")
        
        Timber.d("GifEditorHelper: Downloading network file to ${tempFile.absolutePath}")
        
        val success = downloadNetworkFileUseCase.execute(
            remotePath = gifPath,
            targetFile = tempFile
        )
        
        if (success) {
            downloadedTempFile = tempFile
            Timber.d("GifEditorHelper: Download successful")
            return@withContext tempFile.absolutePath
        } else {
            Timber.e("GifEditorHelper: Download failed")
            return@withContext null
        }
    }
    
    /**
     * Clean up downloaded temp file
     * Call this when dialog is dismissed or operation is complete
     */
    fun cleanup() {
        downloadedTempFile?.let { file ->
            if (file.exists()) {
                file.delete()
                Timber.d("GifEditorHelper: Cleaned up temp file: ${file.absolutePath}")
            }
        }
        downloadedTempFile = null
    }
    
    /**
     * Get success message for operation
     */
    fun getSuccessMessage(
        operation: GifOperation,
        isNetwork: Boolean,
        vararg args: Any
    ): String {
        return when (operation) {
            GifOperation.EXTRACT_FRAMES -> {
                val frameCount = args[0] as Int
                if (isNetwork) {
                    context.getString(R.string.msg_gif_network_extract_success, frameCount)
                } else {
                    context.getString(R.string.msg_gif_extract_success, frameCount)
                }
            }
            GifOperation.CHANGE_SPEED -> {
                val multiplier = args[0] as Float
                if (isNetwork) {
                    context.getString(R.string.msg_gif_network_speed_success, multiplier)
                } else {
                    context.getString(R.string.msg_gif_speed_success, multiplier)
                }
            }
            GifOperation.SAVE_FIRST_FRAME -> {
                if (isNetwork) {
                    context.getString(R.string.msg_gif_network_first_frame_success)
                } else {
                    context.getString(R.string.msg_gif_first_frame_success)
                }
            }
        }
    }
    
    /**
     * Get progress message for preparing file
     */
    fun getPreparingMessage(operation: GifOperation, isNetwork: Boolean): String {
        return if (isNetwork) {
            context.getString(R.string.msg_preparing_file)
        } else {
            when (operation) {
                GifOperation.EXTRACT_FRAMES -> context.getString(R.string.msg_gif_extracting)
                GifOperation.CHANGE_SPEED -> context.getString(R.string.msg_gif_speed_success, 1.0f)
                GifOperation.SAVE_FIRST_FRAME -> context.getString(R.string.msg_gif_first_frame_success)
            }
        }
    }
}

/**
 * GIF editing operations
 */
enum class GifOperation {
    EXTRACT_FRAMES,
    CHANGE_SPEED,
    SAVE_FIRST_FRAME
}
