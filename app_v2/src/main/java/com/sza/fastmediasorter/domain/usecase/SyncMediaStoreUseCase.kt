package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Synchronizes local folder files with MediaStore database.
 * 
 * Scans all files in a local resource folder and registers them in MediaStore
 * to ensure the app can see them (especially files copied without MediaStore notification).
 * 
 * **Use cases:**
 * - User presses "Refresh" button and wants to sync ghost files
 * - Files were copied to shared storage without MediaStore registration
 * - Manual synchronization to resolve file visibility issues
 * 
 * **How it works:**
 * 1. Recursively scans folder using File API (bypasses MediaStore)
 * 2. For each file found, triggers MediaScannerConnection to register in MediaStore
 * 3. Skips app-private directories and hidden files (starting with dot)
 * 
 * @param context Application context for MediaStore notifications
 */
class SyncMediaStoreUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Synchronizes all files in a local resource with MediaStore.
     * 
     * @param resource The resource to synchronize (must be LOCAL type)
     * @return Result with count of files synchronized
     */
    suspend fun invoke(resource: MediaResource): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (resource.type != ResourceType.LOCAL) {
                Timber.w("SyncMediaStore: Skipping non-local resource '${resource.name}' (type=${resource.type})")
                return@withContext Result.success(0)
            }
            
            val rootFolder = File(resource.path)
            if (!rootFolder.exists() || !rootFolder.isDirectory) {
                Timber.e("SyncMediaStore: Path does not exist or is not a directory: ${resource.path}")
                return@withContext Result.failure(IllegalArgumentException("Invalid directory: ${resource.path}"))
            }
            
            Timber.i("SyncMediaStore: START synchronization for '${resource.name}' at ${resource.path}")
            var syncedCount = 0
            
            // Scan all files recursively
            val filesToSync = mutableListOf<File>()
            collectFiles(rootFolder, filesToSync, resource.scanSubdirectories)
            
            Timber.d("SyncMediaStore: Found ${filesToSync.size} files to sync")
            
            // Register each file in MediaStore
            filesToSync.forEach { file ->
                try {
                    MediaStoreNotifier.notifyFile(
                        context = context,
                        path = file.absolutePath,
                        reason = "sync-mediastore"
                    )
                    syncedCount++
                    
                    if (syncedCount % 100 == 0) {
                        Timber.d("SyncMediaStore: Progress - synced $syncedCount/${filesToSync.size} files")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "SyncMediaStore: Failed to notify MediaStore for ${file.name}")
                }
            }
            
            Timber.i("SyncMediaStore: COMPLETE - Successfully synced $syncedCount files for '${resource.name}'")
            Result.success(syncedCount)
            
        } catch (e: Exception) {
            Timber.e(e, "SyncMediaStore: FAILED for resource '${resource.name}'")
            Result.failure(e)
        }
    }
    
    /**
     * Recursively collects all files from a directory.
     * 
     * @param directory Directory to scan
     * @param outFiles List to collect files into
     * @param scanSubdirectories Whether to scan subdirectories recursively
     */
    private fun collectFiles(directory: File, outFiles: MutableList<File>, scanSubdirectories: Boolean) {
        try {
            val entries = directory.listFiles() ?: return
            
            for (entry in entries) {
                // Skip hidden files and directories (starting with dot)
                if (entry.name.startsWith(".")) {
                    continue
                }
                
                when {
                    entry.isFile -> {
                        outFiles.add(entry)
                    }
                    entry.isDirectory && scanSubdirectories -> {
                        collectFiles(entry, outFiles, scanSubdirectories)
                    }
                }
            }
        } catch (e: SecurityException) {
            Timber.w(e, "SyncMediaStore: No access to directory ${directory.absolutePath}")
        }
    }
}
