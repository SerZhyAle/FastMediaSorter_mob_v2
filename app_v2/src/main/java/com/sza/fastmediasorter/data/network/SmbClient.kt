package com.sza.fastmediasorter.data.network

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.sza.fastmediasorter.core.util.InputStreamExt.copyToWithProgress
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * SMB/CIFS client for network file operations using SMBJ library.
 * Provides connection management, authentication, file listing, and data transfer
 * capabilities for accessing remote SMB shares.
 * 
 * Supports SMB2/SMB3 protocols.
 * Uses connection pooling to reduce authentication overhead when loading multiple files.
 */
@Singleton
class SmbClient @Inject constructor() {
    
    companion object {
        private const val CONNECTION_TIMEOUT_MS = 30000L
        private const val READ_TIMEOUT_MS = 30000L
        private const val WRITE_TIMEOUT_MS = 30000L
        private const val MAX_CONCURRENT_CONNECTIONS = 8 // Limit parallel SMB connections
        private const val CONNECTION_IDLE_TIMEOUT_MS = 5000L // 5 seconds idle timeout
    }

    // Lazy initialization of config and client to speed up app startup
    // SMBClient initialization is expensive (~900ms due to SLF4J and BouncyCastle)
    private val config by lazy {
        SmbConfig.builder()
            .withTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .withSoTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .withMultiProtocolNegotiate(true)
            .build()
    }

    private val client by lazy { SMBClient(config) }
    
    // Connection pool with automatic cleanup
    private data class PooledConnection(
        val connection: Connection,
        val session: Session,
        val share: DiskShare,
        var lastUsed: Long = System.currentTimeMillis()
    )
    
    private data class ConnectionKey(
        val server: String,
        val port: Int,
        val shareName: String,
        val username: String,
        val domain: String
    )
    
    private val connectionPool = ConcurrentHashMap<ConnectionKey, PooledConnection>()
    private val connectionSemaphore = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    
    /**
     * Data class for SMB connection parameters
     */
    data class SmbConnectionInfo(
        val server: String,
        val shareName: String,
        val username: String = "",
        val password: String = "",
        val domain: String = "",
        val port: Int = 445
    )
    
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
     * Result wrapper for operations
     */
    sealed class SmbResult<out T> {
        data class Success<T>(val data: T) : SmbResult<T>()
        data class Error(val message: String, val exception: Exception? = null) : SmbResult<Nothing>()
    }

    /**
     * Test connection to SMB server
     * - If shareName is empty: tests server accessibility and lists available shares
     * - If shareName is provided: tests share accessibility and provides folder/file statistics
     */
    suspend fun testConnection(connectionInfo: SmbConnectionInfo): SmbResult<String> {
        return try {
            if (connectionInfo.shareName.isEmpty()) {
                // Test server only - list available shares
                val sharesResult = listShares(
                    connectionInfo.server,
                    connectionInfo.username,
                    connectionInfo.password,
                    connectionInfo.domain,
                    connectionInfo.port
                )
                
                when (sharesResult) {
                    is SmbResult.Success -> {
                        val sharesList = sharesResult.data.joinToString("\n• ", prefix = "• ")
                        val message = """
                            |✓ Server accessible: ${connectionInfo.server}
                            |
                            |Available shares (${sharesResult.data.size}):
                            |$sharesList
                        """.trimMargin()
                        SmbResult.Success(message)
                    }
                    is SmbResult.Error -> sharesResult
                }
            } else {
                // Test specific share - provide detailed statistics
                withConnection(connectionInfo) { share ->
                    // Count folders and media files in root
                    val files = share.list("").filter { !it.fileName.startsWith(".") }
                    val folders = files.count { (it.fileAttributes and 0x10L) != 0L }
                    val mediaFiles = files.filter { file ->
                        val ext = file.fileName.substringAfterLast('.', "").lowercase()
                        ext in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp",
                                     "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm",
                                     "mp3", "wav", "aac", "flac", "ogg", "m4a")
                    }
                    
                    val message = """
                        |✓ Share accessible: ${connectionInfo.server}\${connectionInfo.shareName}
                        |
                        |Share statistics:
                        |• Subfolders: $folders
                        |• Media files in root: ${mediaFiles.size}
                        |• Total items: ${files.size}
                    """.trimMargin()
                    
                    SmbResult.Success(message)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB connection test failed")
            SmbResult.Error(
                buildDiagnosticMessage(e, connectionInfo),
                e
            )
        }
    }

    /**
     * List files and folders in SMB directory
     */
    suspend fun listFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = ""
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            withConnection(connectionInfo) { share ->
                val files = mutableListOf<SmbFileInfo>()
                val dirPath = if (remotePath.isEmpty()) "" else remotePath.trim('/', '\\')
                
                for (fileInfo in share.list(dirPath)) {
                    if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                    
                    val fullPath = if (dirPath.isEmpty()) {
                        fileInfo.fileName
                    } else {
                        "$dirPath/${fileInfo.fileName}"
                    }
                    
                    files.add(
                        SmbFileInfo(
                            name = fileInfo.fileName,
                            path = fullPath,
                            isDirectory = fileInfo.fileAttributes and 0x10 != 0L, // FILE_ATTRIBUTE_DIRECTORY = 0x10
                            size = fileInfo.allocationSize,
                            lastModified = fileInfo.lastWriteTime.toEpochMillis()
                        )
                    )
                }
                SmbResult.Success(files)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to list SMB files")
            SmbResult.Error("Failed to list files: ${e.message}", e)
        }
    }

    /**
     * Scan SMB folder for media files (recursive)
     * @param progressCallback Optional callback for progress updates (called every 10 files)
     */
    suspend fun scanMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String> = setOf("jpg", "jpeg", "png", "gif", "mp4", "mov", "avi", "mp3", "wav"),
        progressCallback: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback? = null
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            val startTime = System.currentTimeMillis()
            val mediaFiles = mutableListOf<SmbFileInfo>()
            
            withConnection(connectionInfo) { share ->
                scanDirectoryRecursive(share, remotePath, extensions, mediaFiles, progressCallback)
                SmbResult.Success(mediaFiles)
            }.also {
                if (it is SmbResult.Success) {
                    val durationMs = System.currentTimeMillis() - startTime
                    progressCallback?.onComplete(it.data.size, durationMs)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan SMB media files")
            SmbResult.Error("Failed to scan media files: ${e.message}", e)
        }
    }

    /**
     * Scan SMB folder with limit (for lazy loading)
     * Returns early after finding maxFiles files
     */
    suspend fun scanMediaFilesChunked(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String> = setOf("jpg", "jpeg", "png", "gif", "mp4", "mov", "avi", "mp3", "wav"),
        maxFiles: Int = 100
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            Timber.d("SmbClient.scanMediaFilesChunked: START - share=${connectionInfo.shareName}, remotePath=$remotePath, maxFiles=$maxFiles")
            
            val mediaFiles = mutableListOf<SmbFileInfo>()
            
            withConnection(connectionInfo) { share ->
                Timber.d("SmbClient.scanMediaFilesChunked: Connection established, starting recursive scan")
                scanDirectoryRecursiveWithLimit(share, remotePath, extensions, mediaFiles, maxFiles)
                Timber.d("SmbClient.scanMediaFilesChunked: Scan completed, found ${mediaFiles.size} files")
                SmbResult.Success(mediaFiles)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan SMB media files (chunked)")
            SmbResult.Error("Failed to scan media files: ${e.message}", e)
        }
    }

    /**
     * Scan media files with pagination support (optimized for lazy loading)
     * Skips first 'offset' files, then collects up to 'limit' files
     * Much faster than scanMediaFiles() for large folders with offset > 0
     */
    suspend fun scanMediaFilesPaged(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String> = setOf("jpg", "jpeg", "png", "gif", "mp4", "mov", "avi", "mp3", "wav"),
        offset: Int = 0,
        limit: Int = 50
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            val startTime = System.currentTimeMillis()
            val mediaFiles = mutableListOf<SmbFileInfo>()
            var skippedCount = 0
            
            withConnection(connectionInfo) { share ->
                scanDirectoryWithOffsetLimit(share, remotePath, extensions, mediaFiles, offset, limit, skippedCount)
                Timber.d("SmbClient.scanMediaFilesPaged: offset=$offset, limit=$limit, returned=${mediaFiles.size}, took ${System.currentTimeMillis() - startTime}ms")
                SmbResult.Success(mediaFiles)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan SMB media files (paged)")
            SmbResult.Error("Failed to scan media files: ${e.message}", e)
        }
    }

    /**
     * Count media files in SMB folder (recursive, optimized)
     * Returns count without creating SmbFileInfo objects
     */
    suspend fun countMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String> = setOf("jpg", "jpeg", "png", "gif", "mp4", "mov", "avi", "mp3", "wav"),
        maxCount: Int = 10000 // Stop counting after this limit to avoid long scans
    ): SmbResult<Int> {
        return try {
            withConnection(connectionInfo) { share ->
                val count = countDirectoryRecursive(share, remotePath, extensions, maxCount)
                SmbResult.Success(count)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to count SMB media files")
            SmbResult.Error("Failed to count media files: ${e.message}", e)
        }
    }

    private suspend fun scanDirectoryRecursive(
        share: DiskShare,
        path: String,
        extensions: Set<String>,
        results: MutableList<SmbFileInfo>,
        progressCallback: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback? = null
    ) {
        try {
            val dirPath = path.trim('/', '\\')
            
            for (fileInfo in share.list(dirPath)) {
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val fullPath = if (dirPath.isEmpty()) {
                    fileInfo.fileName
                } else {
                    "$dirPath/${fileInfo.fileName}"
                }
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L // FILE_ATTRIBUTE_DIRECTORY = 0x10
                
                if (isDirectory) {
                    // Recursively scan subdirectories
                    scanDirectoryRecursive(share, fullPath, extensions, results, progressCallback)
                } else {
                    // Check if file has media extension
                    val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                    if (extension in extensions) {
                        results.add(
                            SmbFileInfo(
                                name = fileInfo.fileName,
                                path = fullPath,
                                isDirectory = false,
                                size = fileInfo.allocationSize,
                                lastModified = fileInfo.lastWriteTime.toEpochMillis()
                            )
                        )
                        
                        // Report progress every 10 files
                        if (results.size % 10 == 0) {
                            progressCallback?.onProgress(results.size, fileInfo.fileName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to scan directory: $path")
        }
    }

    /**
     * Scan directory recursively with file limit (for lazy loading)
     * Returns early when maxFiles is reached
     */
    private fun scanDirectoryRecursiveWithLimit(
        share: DiskShare,
        path: String,
        extensions: Set<String>,
        results: MutableList<SmbFileInfo>,
        maxFiles: Int
    ): Boolean { // Returns true if limit reached
        try {
            if (results.size >= maxFiles) return true
            
            val dirPath = path.trim('/', '\\')
            
            Timber.d("SmbClient.scanDirectoryRecursiveWithLimit: Scanning dirPath='$dirPath', current results=${results.size}")
            
            val items = share.list(dirPath)
            
            // First pass: process files (faster, no recursion)
            for (fileInfo in items) {
                if (results.size >= maxFiles) return true
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                if (isDirectory) continue // Skip directories in first pass
                
                val fullPath = if (dirPath.isEmpty()) {
                    fileInfo.fileName
                } else {
                    "$dirPath/${fileInfo.fileName}"
                }
                
                val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                if (extension in extensions) {
                    Timber.d("SmbClient.scanDirectoryRecursiveWithLimit: Found media file ${fileInfo.fileName}")
                    results.add(
                        SmbFileInfo(
                            name = fileInfo.fileName,
                            path = fullPath,
                            isDirectory = false,
                            size = fileInfo.allocationSize,
                            lastModified = fileInfo.lastWriteTime.toEpochMillis()
                        )
                    )
                }
            }
            
            // Second pass: recurse into directories
            for (fileInfo in items) {
                if (results.size >= maxFiles) return true
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                if (!isDirectory) continue // Skip files in second pass
                
                val fullPath = if (dirPath.isEmpty()) {
                    fileInfo.fileName
                } else {
                    "$dirPath/${fileInfo.fileName}"
                }
                
                val limitReached = scanDirectoryRecursiveWithLimit(share, fullPath, extensions, results, maxFiles)
                if (limitReached) return true
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to scan directory: $path")
        }
        return results.size >= maxFiles
    }

    /**
     * Count media files recursively (optimized, no object creation)
     */
    private fun countDirectoryRecursive(
        share: DiskShare,
        path: String,
        extensions: Set<String>,
        maxCount: Int = 10000,
        currentCount: Int = 0
    ): Int {
        // Early exit if limit reached
        if (currentCount >= maxCount) {
            Timber.d("Count limit reached: $maxCount files, stopping scan")
            return currentCount
        }
        
        var count = currentCount
        try {
            val dirPath = path.trim('/', '\\')
            
            for (fileInfo in share.list(dirPath)) {
                // Check limit on each iteration to stop quickly
                if (count >= maxCount) {
                    Timber.d("Count limit reached during scan: $maxCount files")
                    return count
                }
                
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L
                
                if (isDirectory) {
                    val fullPath = if (dirPath.isEmpty()) {
                        fileInfo.fileName
                    } else {
                        "$dirPath/${fileInfo.fileName}"
                    }
                    count = countDirectoryRecursive(share, fullPath, extensions, maxCount, count)
                } else {
                    val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                    if (extension in extensions) {
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
     * Scan directory with offset/limit support (optimized for pagination)
     * Skips first 'offset' files, collects up to 'limit' files
     * Tracks skipped count using MutableInt wrapper to share state across recursion
     */
    private fun scanDirectoryWithOffsetLimit(
        share: DiskShare,
        path: String,
        extensions: Set<String>,
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
                it.fileName != "." && it.fileName != ".." && (it.fileAttributes and 0x10 != 0L)
            }.sortedBy { it.fileName.lowercase() }
            
            // Process files first
            for (fileInfo in files) {
                if (results.size >= limit) return skipped
                
                val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                if (extension in extensions) {
                    if (skipped < offset) {
                        skipped++
                        continue
                    }
                    
                    val fullPath = if (dirPath.isEmpty()) fileInfo.fileName else "$dirPath/${fileInfo.fileName}"
                    results.add(
                        SmbFileInfo(
                            name = fileInfo.fileName,
                            path = fullPath,
                            isDirectory = false,
                            size = fileInfo.allocationSize,
                            lastModified = fileInfo.lastWriteTime.toEpochMillis()
                        )
                    )
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
        }
        return skipped
    }

    /**
     * List available shares on SMB server
     * 
     * SMBJ library limitations:
     * - No direct API for share enumeration
     * - Cannot use IPC$ administrative share to list shares (requires admin rights)
     * - Must use trial connection approach or RAP/DCE-RPC protocols (not exposed by SMBJ)
     * 
     * Current implementation tries common share names, which may miss custom-named shares.
     * This is a known limitation of SMBJ library v0.12.1.
     * 
     * Alternative solutions:
     * 1. Use jCIFS library (older, but has share enumeration)
     * 2. Use RAP protocol via custom implementation
     * 3. Ask user to enter share names manually
     */
    suspend fun listShares(
        server: String,
        username: String = "",
        password: String = "",
        domain: String = "",
        port: Int = 445
    ): SmbResult<List<String>> {
        return try {
            val connection = client.connect(server, port)
            val authContext = if (username.isEmpty()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(username, password.toCharArray(), domain)
            }
            
            val session = connection.authenticate(authContext)
            // Use LinkedHashSet to preserve insertion order and auto-deduplicate case-insensitive share names
            val shares = mutableSetOf<String>()
            
            try {
                // Attempt 1: Try to list shares using IPC$ administrative share
                // This works if user has proper permissions
                try {
                    val ipcShare = session.connectShare("IPC$")
                    // Try to get share list through IPC$
                    // Note: SMBJ doesn't expose direct API for this, but connection success indicates permissions
                    ipcShare.close()
                    Timber.d("IPC$ connection successful - user may have admin rights")
                    
                    // Try to use ServerService to enumerate shares (if available in SMBJ)
                    // This is a best-effort attempt
                    try {
                        // SMBJ doesn't expose RAP or SRVSVC directly, so we fall back to trial method
                        Timber.d("Share enumeration via IPC$ not directly supported by SMBJ")
                    } catch (e: Exception) {
                        Timber.d("Share enumeration through IPC$ failed: ${e.message}")
                    }
                } catch (e: Exception) {
                    Timber.d("IPC$ access denied or not available: ${e.message}")
                }
                
                // Attempt 2: Try common and typical share names (extended list)
                // This is the main workaround for SMBJ's lack of share enumeration API
                val commonShareNames = listOf(
                    // Standard Windows shares
                    "Public", "Users", "Documents", "Downloads",
                    "Pictures", "Photos", "Images",
                    "Videos", "Movies", "Media",
                    "Music", "Audio",
                    // Common custom names
                    "Shared", "Share", "Data", "Files", 
                    "Transfer", "Common", "Backup",
                    // NAS typical names
                    "home", "public", "web", "multimedia",
                    // Work/Personal variations
                    "Work", "Personal", "Private", "Projects",
                    // Archive/Storage variations
                    "Archive", "Storage", "Repository", "Vault",
                    // Year-based (try recent years)
                    "2024", "2025", "Archive2024",
                    // Department names
                    "IT", "Finance", "HR", "Sales",
                    // Media server names
                    "Plex", "Media", "Library", "Content",
                    // Additional common patterns
                    "Temp", "Temporary", "Exchange", "FTP",
                    "Upload", "Inbox", "Outbox", "Downloads",
                    // User-specific patterns
                    "Docs", "MyDocuments", "MyFiles", "MyData",
                    // Try lowercase variations
                    "shared", "public", "users", "documents",
                    "photos", "videos", "music", "data"
                )
                
                Timber.d("Scanning for shares using trial connection method (${commonShareNames.size} attempts)...")
                
                for (shareName in commonShareNames) {
                    try {
                        val share = session.connectShare(shareName)
                        
                        // Filter out administrative shares
                        val isAdminShare = shareName.endsWith("$") || 
                                         shareName.equals("IPC$", ignoreCase = true) ||
                                         shareName.equals("ADMIN$", ignoreCase = true) ||
                                         shareName.matches(Regex("[A-Za-z]\\$")) // Drive shares like C$, D$
                        
                        if (!isAdminShare) {
                            // Add with case-insensitive deduplication
                            // Check if a case-insensitive variant already exists
                            val alreadyExists = shares.any { it.equals(shareName, ignoreCase = true) }
                            if (!alreadyExists) {
                                shares.add(shareName)
                                Timber.d("Found accessible share: $shareName")
                            } else {
                                Timber.d("Skipping duplicate share (case variant): $shareName")
                            }
                        } else {
                            Timber.d("Skipping administrative share: $shareName")
                        }
                        
                        share.close()
                    } catch (e: Exception) {
                        // Share doesn't exist, not accessible, or hidden - skip silently
                        // This is expected behavior for non-existent shares
                    }
                }
                
                Timber.i("Found ${shares.size} accessible shares on $server using trial method")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to enumerate shares")
                session.close()
                connection.close()
                return SmbResult.Error(
                    "Share enumeration failed. SMBJ library limitation: cannot list shares automatically. " +
                    "Please enter share name manually. Technical details: ${e.message}", 
                    e
                )
            }
            
            session.close()
            connection.close()
            
            if (shares.isEmpty()) {
                return SmbResult.Error(
                    "No accessible shares found using trial method.\n\n" +
                    "SMBJ library limitation: Cannot automatically discover all shares.\n\n" +
                    "Tried multiple common share names, but none were accessible.\n\n" +
                    "Your shares may have custom names. Please enter share name manually.\n\n" +
                    "To find share names on Windows:\n" +
                    "1. Open File Explorer on server computer\n" +
                    "2. Right-click shared folder → Properties → Sharing tab\n" +
                    "3. Look for 'Network Path' (e.g., \\\\ServerName\\ShareName)\n" +
                    "4. Use the ShareName part in the app\n\n" +
                    "Or use 'net share' command in Windows Command Prompt to list all shares.",
                    null
                )
            }
            
            // Return found shares as sorted list with helpful message if only few found
            val sharesList = shares.toList().sorted()
            val result = SmbResult.Success(sharesList)
            if (sharesList.size < 3) {
                Timber.w("Only ${sharesList.size} share(s) found. There may be more shares with custom names.")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to SMB server for share enumeration")
            SmbResult.Error("Connection failed: ${e.message}. Please verify server address and credentials.", e)
        }
    }

    /**
     * Copy file from SMB to local
     */
    suspend fun downloadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localOutputStream: OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<Unit> {
        return try {
            withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                file.use { smbFile ->
                    smbFile.inputStream.use { input ->
                        if (progressCallback != null) {
                            input.copyToWithProgress(
                                output = localOutputStream,
                                totalBytes = fileSize,
                                progressCallback = progressCallback
                            )
                        } else {
                            input.copyTo(localOutputStream)
                        }
                    }
                }
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download file from SMB")
            SmbResult.Error("Failed to download file: ${e.message}", e)
        }
    }

    /**
     * Read file bytes from SMB (useful for thumbnails and image loading)
     */
    suspend fun readFileBytes(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): SmbResult<ByteArray> {
        return try {
            withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                file.use { smbFile ->
                    smbFile.inputStream.use { input ->
                        val bytes = if (maxBytes < Long.MAX_VALUE) {
                            input.readNBytes(maxBytes.toInt())
                        } else {
                            input.readBytes()
                        }
                        SmbResult.Success(bytes)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to read file bytes from SMB")
            SmbResult.Error("Failed to read file: ${e.message}", e)
        }
    }

    /**
     * Upload file from local to SMB
     */
    suspend fun uploadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localInputStream: InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<Unit> {
        return try {
            withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath,
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null
                )
                
                file.use { smbFile ->
                    smbFile.outputStream.use { output ->
                        if (progressCallback != null) {
                            localInputStream.copyToWithProgress(
                                output = output,
                                totalBytes = fileSize,
                                progressCallback = progressCallback
                            )
                        } else {
                            localInputStream.copyTo(output)
                        }
                    }
                }
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload file to SMB")
            SmbResult.Error("Failed to upload file: ${e.message}", e)
        }
    }

    /**
     * Delete file on SMB share
     */
    suspend fun deleteFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        return try {
            withConnection(connectionInfo) { share ->
                share.rm(remotePath)
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete file from SMB")
            SmbResult.Error("Failed to delete file: ${e.message}", e)
        }
    }

    /**
     * Rename file on SMB share
     * @param connectionInfo SMB connection information
     * @param oldPath Current path (relative to share)
     * @param newName New filename (without path)
     */
    suspend fun renameFile(
        connectionInfo: SmbConnectionInfo,
        oldPath: String,
        newName: String
    ): SmbResult<Unit> {
        return try {
            withConnection(connectionInfo) { share ->
                // Extract directory path and construct new full path
                val directory = oldPath.substringBeforeLast('/', "")
                val newPath = if (directory.isEmpty()) newName else "$directory/$newName"
                
                Timber.d("Renaming SMB file: $oldPath → $newPath")
                
                // Check if target exists
                if (share.fileExists(newPath)) {
                    return@withConnection SmbResult.Error("File with name '$newName' already exists")
                }
                
                // Open source file for rename
                val file = share.openFile(
                    oldPath,
                    EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                file.use {
                    it.rename(newPath)
                }
                
                Timber.i("Successfully renamed SMB file to: $newPath")
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to rename file on SMB")
            SmbResult.Error("Failed to rename file: ${e.message}", e)
        }
    }

    /**
     * Create directory on SMB share
     */
    suspend fun createDirectory(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        return try {
            withConnection(connectionInfo) { share ->
                share.mkdir(remotePath)
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create directory on SMB")
            SmbResult.Error("Failed to create directory: ${e.message}", e)
        }
    }

    /**
     * Check if path exists on SMB share
     */
    suspend fun exists(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Boolean> {
        return try {
            withConnection(connectionInfo) { share ->
                val exists = share.fileExists(remotePath)
                SmbResult.Success(exists)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check if path exists on SMB")
            SmbResult.Error("Failed to check path: ${e.message}", e)
        }
    }

    /**
     * Helper function to manage connection lifecycle with connection pooling
     */
    private suspend fun <T> withConnection(
        connectionInfo: SmbConnectionInfo,
        block: suspend (DiskShare) -> SmbResult<T>
    ): SmbResult<T> = connectionSemaphore.withPermit {
        val key = ConnectionKey(
            server = connectionInfo.server,
            port = connectionInfo.port,
            shareName = connectionInfo.shareName,
            username = connectionInfo.username,
            domain = connectionInfo.domain
        )
        
        // Note: cleanupIdleConnections() removed from here to avoid blocking
        // Idle cleanup now happens lazily when connection pool is full or on explicit call
        
        try {
            // Try to reuse existing connection
            val pooled = connectionPool[key]
            
            if (pooled != null && isConnectionValid(pooled)) {
                pooled.lastUsed = System.currentTimeMillis()
                try {
                    return@withPermit block(pooled.share)
                } catch (e: Exception) {
                    // Connection might be stale, remove from pool and create new
                    Timber.w(e, "Pooled connection failed, creating new")
                    removeConnection(key)
                }
            }
            
            // Before creating new connection, check if pool is too large
            if (connectionPool.size >= MAX_CONCURRENT_CONNECTIONS) {
                cleanupIdleConnectionsQuick()
            }
            
            // Create new connection
            val connection = client.connect(connectionInfo.server, connectionInfo.port)
            
            val authContext = if (connectionInfo.username.isEmpty()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(
                    connectionInfo.username,
                    connectionInfo.password.toCharArray(),
                    connectionInfo.domain
                )
            }
            
            val session = connection.authenticate(authContext)
            val share = session.connectShare(connectionInfo.shareName) as DiskShare
            
            // Store in pool for reuse
            val newPooled = PooledConnection(connection, session, share)
            connectionPool[key] = newPooled
            
            block(share)
        } catch (e: Exception) {
            Timber.w(e, "SMB resource unavailable")
            removeConnection(key) // Remove failed connection from pool
            SmbResult.Error("Connection error: ${e.message}", e)
        }
    }
    
    /**
     * Check if pooled connection is still valid
     */
    private fun isConnectionValid(pooled: PooledConnection): Boolean {
        return try {
            pooled.connection.isConnected &&
            pooled.session.connection.isConnected
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Remove connection from pool and close it
     */
    private fun removeConnection(key: ConnectionKey) {
        connectionPool.remove(key)?.let { pooled ->
            try {
                pooled.share.close()
                pooled.session.close()
                pooled.connection.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing pooled SMB connection")
            }
        }
    }
    
    /**
     * Quick cleanup: identify and remove dead connections without blocking close() calls
     * Used when connection pool reaches MAX_CONCURRENT_CONNECTIONS
     */
    private fun cleanupIdleConnectionsQuick() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<ConnectionKey>()
        
        // Identify dead or idle connections
        connectionPool.entries.forEach { (key, pooled) ->
            val isIdle = (now - pooled.lastUsed) > CONNECTION_IDLE_TIMEOUT_MS
            val isDead = !isConnectionAlive(pooled)
            
            if (isDead || isIdle) {
                keysToRemove.add(key)
            }
        }
        
        // Remove without trying to close (avoids blocking)
        keysToRemove.forEach { key ->
            connectionPool.remove(key)
            Timber.d("Quick-removed idle/dead SMB connection to ${key.server}")
        }
    }
    
    /**
     * Check if connection is still alive (non-blocking check)
     */
    private fun isConnectionAlive(pooled: PooledConnection): Boolean {
        return try {
            pooled.connection.isConnected
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Clean up idle connections from pool
     * Non-blocking: uses iterator to avoid TimeoutException blocking main connection flow
     */
    private fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<ConnectionKey>()
        
        // First pass: identify idle connections without blocking
        connectionPool.entries.forEach { (key, pooled) ->
            val isIdle = (now - pooled.lastUsed) > CONNECTION_IDLE_TIMEOUT_MS
            if (isIdle) {
                keysToRemove.add(key)
            }
        }
        
        // Second pass: remove and close in background (non-blocking)
        keysToRemove.forEach { key ->
            val pooled = connectionPool.remove(key)
            if (pooled != null) {
                // Close connection in background thread to avoid blocking
                try {
                    // Try quick non-blocking check if connection is alive
                    if (!pooled.connection.isConnected) {
                        Timber.d("Removed dead idle SMB connection to ${key.server}")
                        return@forEach
                    }
                    
                    // Attempt graceful close with timeout protection
                    pooled.share.close()
                    pooled.session.close()
                    pooled.connection.close()
                    Timber.d("Closed idle SMB connection to ${key.server}")
                } catch (e: java.util.concurrent.TimeoutException) {
                    // Connection already dead, no action needed
                    Timber.d("Timeout closing idle SMB connection to ${key.server} (already terminated)")
                } catch (e: com.hierynomus.protocol.transport.TransportException) {
                    // Transport error means connection already disconnected
                    Timber.d("Transport error closing idle SMB connection to ${key.server} (already disconnected)")
                } catch (e: Exception) {
                    // Check if timeout/transport error is wrapped
                    val isExpected = e.cause?.cause is java.util.concurrent.TimeoutException ||
                                   e.cause is java.util.concurrent.TimeoutException ||
                                   e.cause is com.hierynomus.protocol.transport.TransportException ||
                                   e is com.hierynomus.smbj.common.SMBRuntimeException
                    if (isExpected) {
                        Timber.d("Expected error closing idle SMB connection to ${key.server}: ${e.javaClass.simpleName}")
                    } else {
                        Timber.w(e, "Unexpected error closing idle SMB connection to ${key.server}")
                    }
                }
            }
        }
    }
    
    /**
     * Clear all pooled connections (call on app shutdown or resource cleanup)
     */
    fun clearConnectionPool() {
        connectionPool.keys.toList().forEach { key ->
            removeConnection(key)
        }
    }

    /**
     * Build diagnostic message for connection errors
     */
    private fun buildDiagnosticMessage(
        exception: Exception,
        connectionInfo: SmbConnectionInfo
    ): String {
        val sb = StringBuilder()
        sb.append("=== SMB CONNECTION DIAGNOSTIC ===\n")
        sb.append("Server: ${connectionInfo.server}:${connectionInfo.port}\n")
        sb.append("Share: ${connectionInfo.shareName}\n")
        sb.append("Username: ${if (connectionInfo.username.isEmpty()) "anonymous" else connectionInfo.username}\n")
        sb.append("\nError: ${exception.javaClass.simpleName}\n")
        sb.append("Message: ${exception.message}\n")
        
        sb.append("\nCommon solutions:\n")
        sb.append("• Verify server address is correct\n")
        sb.append("• Check network connectivity\n")
        sb.append("• Ensure SMB port ${connectionInfo.port} is not blocked\n")
        sb.append("• Verify username and password\n")
        sb.append("• Check share name and permissions\n")
        sb.append("• Ensure SMB2/SMB3 is enabled on server\n")
        
        return sb.toString()
    }

    /**
     * Check write permission by attempting to create and write a test file.
     * Creates .fms_write_test_<timestamp>.tmp in the specified path, then deletes it.
     * 
     * @param connectionInfo SMB connection parameters
     * @param remotePath Path within the share to test (empty string for share root)
     * @return SmbResult.Success(true) if write operations succeed, Success(false) or Error otherwise
     */
    suspend fun checkWritePermission(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = ""
    ): SmbResult<Boolean> {
        return try {
            withConnection(connectionInfo) { share ->
                // Create test file name with timestamp to avoid conflicts
                val testFileName = ".fms_write_test_${System.currentTimeMillis()}.tmp"
                val testFilePath = if (remotePath.isEmpty()) {
                    testFileName
                } else {
                    "${remotePath.trimEnd('/')}/$testFileName"
                }
                
                Timber.d("Testing write permission: $testFilePath")
                
                var file: File? = null
                val canWrite = try {
                    // Test 1: Try to create the test file
                    file = share.openFile(
                        testFilePath,
                        EnumSet.of(AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_CREATE,
                        null
                    )
                    
                    // Test 2: Try to write some data to verify write access
                    file.outputStream.use { output ->
                        output.write("test".toByteArray())
                        output.flush()
                    }
                    
                    Timber.d("Write test successful")
                    true
                } catch (e: Exception) {
                    Timber.w("Write test failed: ${e.message}")
                    false
                } finally {
                    // Test 3: Try to delete the test file (cleanup)
                    try {
                        file?.close()
                        share.rm(testFilePath)
                        Timber.d("Test file cleaned up")
                    } catch (e: Exception) {
                        Timber.w("Failed to cleanup test file: ${e.message}")
                    }
                }
                
                SmbResult.Success(canWrite)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking write permission")
            SmbResult.Error("Failed to check write permission: ${e.message}", e)
        }
    }

    /**
     * Close client and cleanup resources
     */
    fun close() {
        try {
            client.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing SMB client")
        }
    }
}
