package com.sza.fastmediasorter.data.network.helpers

import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * SMB directory scanning operations with parallel support.
 * 
 * Responsibilities:
 * - Recursive directory scanning with parallel execution
 * - File counting and filtering by extensions
 * - Pagination support (offset/limit)
 * - Progress reporting via callbacks
 * - Cancellation support via coroutine context
 * 
 * Scanning strategies:
 * - Full recursive scan with parallel subdirectory processing
 * - Limited scan (early exit when maxFiles reached)
 * - Paged scan (skip offset files, collect up to limit)
 * - Non-recursive scan (root folder only)
 * - Count-only scan (no SmbFileInfo creation)
 */
class SmbDirectoryScanner(
    private val smbDispatcher: CoroutineDispatcher
) {
    
    /**
     * Data class for file information
     */
    data class SmbFileInfo(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )
    
    /**
     * Parallel recursive directory scanner using coroutines.
     * Launches separate coroutines for each subdirectory to scan them concurrently.
     * Uses dedicated thread pool (smbDispatcher) for blocking SMB I/O operations.
     * Synchronizes access to shared results list and progress callback via Mutex.
     * 
     * Performance: 2-3x speedup for deep directory trees (e.g., 72s -> 20-30s for 62k files)
     */
    suspend fun scanDirectoryRecursive(
        share: DiskShare,
        path: String,
        extensions: Set<String>?,
        results: MutableList<SmbFileInfo>,
        progressCallback: ScanProgressCallback? = null,
        lastProgressTime: LongArray = longArrayOf(System.currentTimeMillis()), // Mutable time tracker
        resultsMutex: Mutex = Mutex() // Synchronizes access to results and progress
    ): Unit = coroutineScope {
        try {
            val dirPath = path.trim('/', '\\')
            
            // Check stop flag BEFORE SMB call
            if (progressCallback?.shouldStop() == true) {
                Timber.d("Stop requested, returning partial results (${results.size} files collected)")
                return@coroutineScope
            }
            
            // Check cancellation BEFORE SMB call
            if (!isActive) {
                Timber.d("Scan cancelled by user before scanning $dirPath")
                return@coroutineScope
            }
            
            // Execute blocking SMB list() in dedicated thread pool
            val items = kotlinx.coroutines.withContext(smbDispatcher) {
                share.list(dirPath).toList() // toList() ensures we fetch all data immediately
            }
            
            for (fileInfo in items) {
                // Check stop flag and cancellation every 100 files (performance optimization)
                if (results.size % 100 == 0) {
                    if (progressCallback?.shouldStop() == true) {
                        Timber.d("Stop requested during scan, returning ${results.size} files")
                        return@coroutineScope
                    }
                    if (!isActive) return@coroutineScope
                }
                
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val fullPath = if (dirPath.isEmpty()) {
                    fileInfo.fileName
                } else {
                    "$dirPath/${fileInfo.fileName}"
                }
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L // FILE_ATTRIBUTE_DIRECTORY = 0x10
                
                if (isDirectory) {
                    if (TrashFolderContract.matchesTrashSegment(fileInfo.fileName)) continue
                    
                    // Recursively scan subdirectory in parallel (launched in coroutineScope)
                    this.launch(smbDispatcher) {
                        scanDirectoryRecursive(share, fullPath, extensions, results, progressCallback, lastProgressTime, resultsMutex)
                    }
                } else {
                    // Filter by extension
                    val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                    if (extensions == null || extension in extensions) {
                        val fileSize = fileInfo.endOfFile
                        val lastModified = fileInfo.lastWriteTime.toEpochMillis()
                        
                        val smbFile = SmbFileInfo(
                            name = fileInfo.fileName,
                            path = fullPath,
                            isDirectory = false,
                            size = fileSize,
                            lastModified = lastModified
                        )
                        
                        // Thread-safe add to results
                        resultsMutex.withLock {
                            results.add(smbFile)
                            
                            // Report progress every 10 files or every 500ms
                            val now = System.currentTimeMillis()
                            if (results.size % 10 == 0 || now - lastProgressTime[0] > 500) {
                                progressCallback?.onProgress(results.size)
                                lastProgressTime[0] = now
                            }
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.d("Scan cancelled: ${e.message}")
            throw e // Re-throw to propagate cancellation
        } catch (e: Exception) {
            Timber.w(e, "Failed to scan directory: $path")
            throw e // Re-throw to propagate error to SmbClient
        }
    }

    /**
     * Scan directory recursively with file limit (for lazy loading)
     * Returns true if limit reached
     */
    suspend fun scanDirectoryRecursiveWithLimit(
        share: DiskShare,
        path: String,
        extensions: Set<String>?,
        results: MutableList<SmbFileInfo>,
        maxFiles: Int
    ): Boolean { // Returns true if limit reached
        try {
            if (results.size >= maxFiles) return true
            
            currentCoroutineContext().ensureActive()
            
            val dirPath = path.trim('/', '\\')
            Timber.d("S0415: SMB scan listing dir='$dirPath' collected=${results.size}")
            val items = share.list(dirPath)
            Timber.d("S0415: SMB scan listed dir='$dirPath' entries=${items.size}")

            // First pass: process files (faster, no recursion)
            for (fileInfo in items) {
                if (results.size >= maxFiles) return true
                
                if (results.size % 50 == 0) currentCoroutineContext().ensureActive()
                
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                if (isDirectory) continue // Skip directories in first pass
                
                val fullPath = if (dirPath.isEmpty()) {
                    fileInfo.fileName
                } else {
                    "$dirPath/${fileInfo.fileName}"
                }
                
                val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                if (extensions == null || extension in extensions) {
                    val fileSize = fileInfo.endOfFile
                    val lastModified = fileInfo.lastWriteTime.toEpochMillis()
                    
                    results.add(SmbFileInfo(
                        name = fileInfo.fileName,
                        path = fullPath,
                        isDirectory = false,
                        size = fileSize,
                        lastModified = lastModified
                    ))
                }
            }
            
            // Second pass: recurse into directories
            for (fileInfo in items) {
                if (results.size >= maxFiles) return true
                
                currentCoroutineContext().ensureActive()
                
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                if (!isDirectory) continue // Skip files in second pass
                
                if (TrashFolderContract.matchesTrashSegment(fileInfo.fileName)) continue
                
                val fullPath = if (dirPath.isEmpty()) {
                    fileInfo.fileName
                } else {
                    "$dirPath/${fileInfo.fileName}"
                }
                
                Timber.d("S0415: SMB scan descend into dir='$fullPath'")
                val limitReached = scanDirectoryRecursiveWithLimit(share, fullPath, extensions, results, maxFiles)
                if (limitReached) return true
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "Failed to scan directory: $path")
            throw e
        }
        return results.size >= maxFiles
    }

    /**
     * Scan directory non-recursively (root folder only)
     * Used when scanSubdirectories=false
     */
    suspend fun scanDirectoryNonRecursive(
        share: DiskShare,
        path: String,
        extensions: Set<String>?,
        results: MutableList<SmbFileInfo>,
        maxFiles: Int,
        progressCallback: ScanProgressCallback? = null,
        includeDirectories: Boolean = false
    ) {
        try {
            if (results.size >= maxFiles) return
            
            currentCoroutineContext().ensureActive()
            
            val dirPath = path.trim('/', '\\')
            
            Timber.d("SmbDirectoryScanner.scanNonRecursive: dirPath='$dirPath' (root only)")
            
            val items = share.list(dirPath)
            
            // Process only files in root folder, skip subdirectories
            for (fileInfo in items) {
                if (results.size >= maxFiles) return
                
                if (results.size % 50 == 0) currentCoroutineContext().ensureActive()
                
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                val fullPath = if (dirPath.isEmpty()) {
                    fileInfo.fileName
                } else {
                    "$dirPath/${fileInfo.fileName}"
                }

                if (isDirectory) {
                    if (includeDirectories) {
                         // Add subdirectory to results
                         val lastModified = fileInfo.lastWriteTime.toEpochMillis()
                         results.add(SmbFileInfo(
                            name = fileInfo.fileName,
                            path = fullPath,
                            isDirectory = true,
                            size = 0L, // Directories don't have size
                            lastModified = lastModified
                        ))
                    }
                    continue // access next item
                }
                
                val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                if (extensions == null || extension in extensions) {
                    val fileSize = fileInfo.endOfFile
                    val lastModified = fileInfo.lastWriteTime.toEpochMillis()
                    
                    results.add(SmbFileInfo(
                        name = fileInfo.fileName,
                        path = fullPath,
                        isDirectory = false,
                        size = fileSize,
                        lastModified = lastModified
                    ))
                    
                    // Report progress
                    if (results.size % 10 == 0) {
                        progressCallback?.onProgress(results.size)
                    }
                }
            }
            
            Timber.d("SmbDirectoryScanner.scanNonRecursive: Found ${results.size} files in root")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "Error scanning directory non-recursively: $path")
            throw e
        }
    }

    /**
     * Scan directory non-recursively with offset/limit support (for pagination)
     */
    suspend fun scanDirectoryNonRecursiveWithOffset(
        share: DiskShare,
        path: String,
        extensions: Set<String>?,
        results: MutableList<SmbFileInfo>,
        offset: Int,
        limit: Int
    ) {
        try {
            currentCoroutineContext().ensureActive()
            
            val dirPath = path.trim('/', '\\')
            Timber.d("SmbDirectoryScanner.scanNonRecursiveWithOffset: dirPath='$dirPath', offset=$offset, limit=$limit")
            
            val items = share.list(dirPath)
            var skipped = 0
            
            for (fileInfo in items) {
                if (results.size >= limit) break
                
                if (results.size % 50 == 0) currentCoroutineContext().ensureActive()
                
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                if (isDirectory) continue
                
                val fullPath = if (dirPath.isEmpty()) fileInfo.fileName else "$dirPath/${fileInfo.fileName}"
                
                val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                if (extensions == null || extension in extensions) {
                    // Skip first 'offset' files
                    if (skipped < offset) {
                        skipped++
                        continue
                    }
                    
                    val fileSize = fileInfo.endOfFile
                    val lastModified = fileInfo.lastWriteTime.toEpochMillis()
                    
                    results.add(SmbFileInfo(
                        name = fileInfo.fileName,
                        path = fullPath,
                        isDirectory = false,
                        size = fileSize,
                        lastModified = lastModified
                    ))
                }
            }
            
            Timber.d("SmbDirectoryScanner.scanNonRecursiveWithOffset: Returned ${results.size} files")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "Error scanning directory non-recursively with offset: $path")
            throw e
        }
    }

    /**
     * Scan directory with offset/limit support (optimized for pagination)
     * Skips first 'offset' files, collects up to 'limit' files
     */
    fun scanDirectoryWithOffsetLimit(
        share: DiskShare,
        path: String,
        extensions: Set<String>?,
        results: MutableList<SmbFileInfo>,
        offset: Int,
        limit: Int,
        skippedSoFar: Int
    ): Int { // Returns total skipped count
        // Early exit if we collected enough files
        if (results.size >= limit) return skippedSoFar
        
        var skipped = skippedSoFar
        try {
            val dirPath = path.trim('/', '\\')
            val allItems = share.list(dirPath).toList()
            
            // Separate files and directories, filter out "." and ".."
            val files = allItems.filter { 
                it.fileName != "." && it.fileName != ".." && (it.fileAttributes and 0x10 == 0L)
            }.sortedBy { it.fileName.lowercase() }
            
            val directories = allItems.filter {
                it.fileName != "." && it.fileName != ".." && (it.fileAttributes and 0x10 != 0L) &&
                !TrashFolderContract.matchesTrashSegment(it.fileName)
            }.sortedBy { it.fileName.lowercase() }
            
            // Process files first
            for (fileInfo in files) {
                if (results.size >= limit) return skipped
                
                val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                if (extensions == null || extension in extensions) {
                    // Skip until we reach offset
                    if (skipped < offset) {
                        skipped++
                        continue
                    }
                    
                    val fullPath = if (dirPath.isEmpty()) fileInfo.fileName else "$dirPath/${fileInfo.fileName}"
                    val fileSize = fileInfo.endOfFile
                    val lastModified = fileInfo.lastWriteTime.toEpochMillis()
                    
                    results.add(SmbFileInfo(
                        name = fileInfo.fileName,
                        path = fullPath,
                        isDirectory = false,
                        size = fileSize,
                        lastModified = lastModified
                    ))
                }
            }
            
            // Then recurse into subdirectories (already sorted)
            for (fileInfo in directories) {
                if (results.size >= limit) return skipped
                val fullPath = if (dirPath.isEmpty()) fileInfo.fileName else "$dirPath/${fileInfo.fileName}"
                skipped = scanDirectoryWithOffsetLimit(share, fullPath, extensions, results, offset, limit, skipped)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to scan directory with offset/limit: $path")
            throw e
        }
        return skipped
    }

    /**
     * Count media files recursively (optimized, no object creation)
     */
    fun countDirectoryRecursive(
        share: DiskShare,
        path: String,
        extensions: Set<String>?,
        maxCount: Int = 1000,
        currentCount: Int = 0
    ): Int {
        // Early exit if limit reached
        if (currentCount >= maxCount) {
            return currentCount
        }
        
        var count = currentCount
        try {
            val dirPath = path.trim('/', '\\')
            
            for (fileInfo in share.list(dirPath)) {
                if (count >= maxCount) return count
                
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                
                if (isDirectory) {
                    if (TrashFolderContract.matchesTrashSegment(fileInfo.fileName)) continue
                    
                    val fullPath = if (dirPath.isEmpty()) fileInfo.fileName else "$dirPath/${fileInfo.fileName}"
                    count = countDirectoryRecursive(share, fullPath, extensions, maxCount, count)
                } else {
                    val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                    if (extensions == null || extension in extensions) {
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to count in directory: $path")
        }
        return count
    }

    /**
     * Count media files non-recursively (root folder only)
     */
    fun countDirectoryNonRecursive(
        share: DiskShare,
        path: String,
        extensions: Set<String>?,
        maxCount: Int
    ): Int {
        try {
            val dirPath = path.trim('/', '\\')
            val items = share.list(dirPath)
            var count = 0
            
            for (fileInfo in items) {
                if (count >= maxCount) return count
                
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                if (isDirectory) continue // Skip subdirectories
                
                val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                if (extensions == null || extension in extensions) count++
            }
            
            return count
        } catch (e: Exception) {
            Timber.w(e, "Error counting directory non-recursively: $path")
            return 0
        }
    }
}

