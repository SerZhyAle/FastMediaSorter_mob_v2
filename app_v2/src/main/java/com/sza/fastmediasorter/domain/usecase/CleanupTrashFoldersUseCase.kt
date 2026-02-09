package com.sza.fastmediasorter.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * UseCase for cleaning up old .trash folders created during soft-delete operations
 * Deletes trash folders older than specified age (default 5 minutes)
 * When maxAgeMs=0, deletes ALL trash folders regardless of age
 */
class CleanupTrashFoldersUseCase @Inject constructor() {
    
    companion object {
        private const val TRASH_PREFIX = ".trash_"
        private const val DEFAULT_AGE_MS = 5 * 60 * 1000L // 5 minutes in milliseconds
    }
    
    /**
     * Clean up trash folders in given directory and all subdirectories
     * @param rootDirectory Directory to search for trash folders
     * @param maxAgeMs Maximum age of trash folders to keep. Set to 0 to delete ALL trash folders regardless of age.
     * @return Number of deleted trash folders
     */
    suspend fun cleanup(
        rootDirectory: File,
        maxAgeMs: Long = DEFAULT_AGE_MS
    ): Int = withContext(Dispatchers.IO) {
        if (!rootDirectory.exists() || !rootDirectory.isDirectory) {
            Timber.w("Root directory doesn't exist or is not a directory: ${rootDirectory.absolutePath}")
            return@withContext 0
        }
        
        val currentTime = System.currentTimeMillis()
        var deletedCount = 0
        
        try {
            deletedCount += cleanupTrashFoldersRecursive(rootDirectory, currentTime, maxAgeMs)
            if (maxAgeMs == 0L) {
                Timber.i("Cleanup completed: deleted ALL $deletedCount trash folders")
            } else {
                Timber.i("Cleanup completed: deleted $deletedCount trash folders older than ${maxAgeMs}ms")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during trash cleanup")
        }
        
        deletedCount
    }
    
    /**
     * Recursively search and delete old trash folders
     */
    private fun cleanupTrashFoldersRecursive(
        directory: File,
        currentTime: Long,
        maxAgeMs: Long
    ): Int {
        var deletedCount = 0
        
        try {
            val files = directory.listFiles() ?: return 0
            
            for (file in files) {
                try {
                    if (file.isDirectory) {
                        if (file.name.startsWith(TRASH_PREFIX)) {
                            // Extract timestamp from folder name
                            val timestampStr = file.name.removePrefix(TRASH_PREFIX)
                            val timestamp = timestampStr.toLongOrNull()
                            
                            if (timestamp != null) {
                                val age = currentTime - timestamp
                                
                                // maxAgeMs=0 means delete all trash folders regardless of age
                                if (maxAgeMs == 0L || age > maxAgeMs) {
                                    // Trash folder is old enough (or delete all mode), delete it
                                    if (deleteRecursively(file)) {
                                        deletedCount++
                                        Timber.d("Deleted trash folder: ${file.name} (age: ${age}ms, maxAge: ${maxAgeMs}ms)")
                                    } else {
                                        Timber.w("Failed to delete trash folder: ${file.name}")
                                    }
                                } else {
                                    Timber.d("Keeping recent trash folder: ${file.name} (age: ${age}ms, maxAge: ${maxAgeMs}ms)")
                                }
                            } else {
                                // Invalid format - try to delete anyway if maxAgeMs=0
                                if (maxAgeMs == 0L) {
                                    Timber.w("Invalid trash folder name format, deleting anyway (maxAge=0): ${file.name}")
                                    if (deleteRecursively(file)) {
                                        deletedCount++
                                    }
                                } else {
                                    Timber.w("Invalid trash folder name format: ${file.name}")
                                }
                            }
                        } else {
                            // Recursively search subdirectories for trash folders
                            deletedCount += cleanupTrashFoldersRecursive(file, currentTime, maxAgeMs)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error listing directory: ${directory.absolutePath}")
        }
        
        return deletedCount
    }
    
    /**
     * Delete directory and all its contents recursively
     */
    private fun deleteRecursively(directory: File): Boolean {
        return try {
            if (!directory.exists()) return true
            
            if (directory.isDirectory) {
                directory.listFiles()?.forEach { child ->
                    deleteRecursively(child)
                }
            }
            
            directory.delete()
        } catch (e: Exception) {
            Timber.e(e, "Error deleting recursively: ${directory.absolutePath}")
            false
        }
    }
}
