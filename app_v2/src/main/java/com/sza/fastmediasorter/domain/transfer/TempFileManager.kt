package com.sza.fastmediasorter.domain.transfer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized temporary file management with automatic cleanup.
 * Thread-safe for concurrent operations.
 */
@Singleton
class TempFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tempFiles = ConcurrentHashMap<String, File>()
    
    /**
     * Create temporary file with unique identifier.
     * 
     * @param prefix File name prefix (e.g., "download_", "upload_")
     * @param suffix File extension (e.g., ".mp4", ".jpg")
     * @return Temporary file in app cache directory
     */
    fun createTempFile(prefix: String, suffix: String): File {
        val uniqueId = UUID.randomUUID().toString()
        val fileName = "temp_${uniqueId}_${prefix}${suffix}"
        val tempFile = File(context.cacheDir, fileName)
        
        // Track for cleanup
        tempFiles[uniqueId] = tempFile
        
        Timber.d("Created temp file: ${tempFile.name} (${tempFiles.size} active)")
        return tempFile
    }
    
    /**
     * Create temporary file from existing file name.
     * Preserves original extension.
     * 
     * @param originalFileName Original file name (e.g., "video.mp4")
     * @return Temporary file with same extension
     */
    fun createTempFileFromName(originalFileName: String): File {
        val extension = originalFileName.substringAfterLast('.', "")
        val prefix = originalFileName.substringBeforeLast('.')
            .take(20) // Limit prefix length
            .replace(Regex("[^a-zA-Z0-9_-]"), "_") // Sanitize
        
        return createTempFile(prefix, if (extension.isNotEmpty()) ".$extension" else "")
    }
    
    /**
     * Cleanup temporary file and remove from tracking.
     * Safe to call multiple times or with non-existent files.
     * 
     * @param file File to cleanup
     */
    fun cleanupTempFile(file: File) {
        try {
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Timber.d("Cleaned up temp file: ${file.name}")
                } else {
                    Timber.w("Failed to delete temp file: ${file.name}")
                }
            }
            
            // Remove from tracking (find by value)
            tempFiles.entries.removeIf { it.value.absolutePath == file.absolutePath }
            
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up temp file: ${file.name}")
        }
    }
    
    /**
     * Cleanup all tracked temporary files.
     * Called during app shutdown or resource cleanup.
     * 
     * @return Number of files deleted
     */
    fun cleanupAllTempFiles(): Int {
        var deletedCount = 0
        
        tempFiles.values.forEach { file ->
            try {
                if (file.exists() && file.delete()) {
                    deletedCount++
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting temp file: ${file.name}")
            }
        }
        
        tempFiles.clear()
        
        Timber.i("Cleaned up $deletedCount temporary files")
        return deletedCount
    }
    
    /**
     * Cleanup old temporary files (older than threshold).
     * Useful for recovering from crashes that prevented cleanup.
     * 
     * @param maxAgeMs Maximum age in milliseconds (default: 24 hours)
     * @return Number of old files deleted
     */
    fun cleanupOldTempFiles(maxAgeMs: Long = 24 * 60 * 60 * 1000): Int {
        val now = System.currentTimeMillis()
        var deletedCount = 0
        
        // Check all files in cache directory
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("temp_")) {
                val age = now - file.lastModified()
                if (age > maxAgeMs) {
                    try {
                        if (file.delete()) {
                            deletedCount++
                            Timber.d("Deleted old temp file: ${file.name} (age: ${age / 1000}s)")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error deleting old temp file: ${file.name}")
                    }
                }
            }
        }
        
        if (deletedCount > 0) {
            Timber.i("Cleaned up $deletedCount old temporary files")
        }
        
        return deletedCount
    }
    
    /**
     * Get total size of all tracked temporary files.
     * 
     * @return Total size in bytes
     */
    fun getTotalTempFileSize(): Long {
        return tempFiles.values.sumOf { file ->
            if (file.exists()) file.length() else 0L
        }
    }
    
    /**
     * Get number of active temporary files.
     */
    fun getActiveTempFileCount(): Int = tempFiles.size
    
    /**
     * Check if disk space is available for temporary file.
     * 
     * @param requiredBytes Required space in bytes
     * @return True if space available
     */
    fun hasAvailableSpace(requiredBytes: Long): Boolean {
        val usableSpace = context.cacheDir.usableSpace
        return usableSpace > requiredBytes
    }
}
