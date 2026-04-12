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
import com.sza.fastmediasorter.data.network.helpers.SmbDirectoryScanner
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.model.MediaExtensions
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbFileInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.network.model.ConnectionKey
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * SMB/CIFS client facade for network file operations using SMBJ library.
 * Delegates file operations to SmbFileOperations and connection management to SmbConnectionManager.
 * 
 * Responsibilities:
 * - Connection testing and share enumeration
 * - Directory scanning and media file discovery
 * - Delegation to SmbFileOperations for file CRUD
 * 
 * Supports SMB2/SMB3 protocols.
 * Uses connection pooling to reduce authentication overhead when loading multiple files.
 * 
 * IMPORTANT: All path arguments in public methods are automatically trimmed of leading slashes
 * to ensure compatibility with SMBJ and various SMB server implementations.
 */
@Singleton
class SmbClient @Inject constructor(
    internal val connectionManager: SmbConnectionManager, // Internal for SmbDataSource access
    private val fileOperations: SmbFileOperations
) {
    
    companion object {
        // Retry configuration for connection attempts
        private const val MAX_RETRY_ATTEMPTS = 3 // Try 3 times before giving up
        private const val RETRY_DELAY_MS = 1000L // Initial delay between retries (increases exponentially)
    }
    
    // Dedicated dispatcher for blocking SMB I/O operations
    private val smbDispatcher = Dispatchers.IO
    
    // Directory scanner helper
    private val directoryScanner = SmbDirectoryScanner(smbDispatcher)
    


    /**
     * Test connection to SMB server with retry logic
     * - If shareName is empty: tests server accessibility and lists available shares
     * - If shareName is provided: tests share accessibility and provides folder/file statistics
     * - If path is provided: tests specific folder within the share
     * 
     * Automatically retries up to MAX_RETRY_ATTEMPTS times with exponential backoff on timeout errors.
     */
    suspend fun testConnection(connectionInfo: SmbConnectionInfo, path: String = ""): SmbResult<String> {
        var lastException: Exception? = null
        var attemptNumber = 1
        
        while (attemptNumber <= MAX_RETRY_ATTEMPTS) {
            try {
                if (attemptNumber == 1) {
                    Timber.d("SMB testConnection to ${connectionInfo.server}/${connectionInfo.shareName} (hasUser=${connectionInfo.username.isNotBlank()})")
                } else {
                    Timber.d("SMB testConnection retry attempt $attemptNumber/$MAX_RETRY_ATTEMPTS")
                }
                return performTestConnection(connectionInfo, path)
            } catch (e: Exception) {
                lastException = e
                
                // Check if this is a retriable error (timeout or connection reset)
                val isTimeout = e is java.util.concurrent.TimeoutException || 
                                e.cause is java.util.concurrent.TimeoutException ||
                                e.cause?.cause is java.util.concurrent.TimeoutException ||
                                e.message?.contains("Timeout", ignoreCase = true) == true ||
                                e.cause?.message?.contains("Timeout", ignoreCase = true) == true
                
                val isConnectionReset = e.cause?.message?.contains("Connection reset", ignoreCase = true) == true ||
                                        e.cause?.cause?.message?.contains("Connection reset", ignoreCase = true) == true
                
                val isRetriable = isTimeout || isConnectionReset
                
                if (isRetriable && attemptNumber < MAX_RETRY_ATTEMPTS) {
                    val delay = RETRY_DELAY_MS * (1 shl (attemptNumber - 1)) // Exponential: 1s, 2s, 4s
                    val errorType = if (isTimeout) "timeout" else "connection reset"
                    Timber.w("SMB $errorType on attempt $attemptNumber, retrying after ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                    attemptNumber++
                } else {
                    // Non-retriable error or last attempt - fail immediately
                    if (!isRetriable) {
                        Timber.d("SMB connection failed with non-retriable error: ${e.javaClass.simpleName}")
                    }
                    break
                }
            }
        }
        
        // All attempts failed
        val finalMessage = if (attemptNumber > 1) {
            "SMB testConnection failed after $attemptNumber attempts"
        } else {
            "SMB testConnection failed"
        }
        Timber.e(lastException, finalMessage)
        return SmbResult.Error(
            getUserFriendlyMessage(lastException ?: Exception("Unknown error")),
            lastException
        )
    }
    
    /**
     * Internal method that performs actual connection test (without retry logic)
     */
    private suspend fun performTestConnection(connectionInfo: SmbConnectionInfo, path: String = ""): SmbResult<String> {
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
                connectionManager.withConnection(connectionInfo) { share ->
                    val targetPath = path.trim('/', '\\')
                    val fullPathDisplay = if (targetPath.isEmpty()) {
                        "${connectionInfo.server}\\${connectionInfo.shareName}"
                    } else {
                        "${connectionInfo.server}\\${connectionInfo.shareName}\\$targetPath"
                    }

                    // Check if path exists (if specified)
                    var pathWarning = ""
                    if (targetPath.isNotEmpty()) {
                        try {
                            // Use folderExists for directories (fileExists() only checks files, not folders in SMBJ)
                            val pathExists = share.folderExists(targetPath) || share.fileExists(targetPath)
                            if (!pathExists) {
                                // Fail if specific subfolder is requested but does not exist
                                // This prevents users from creating resources pointing to non-existent folders (typos)
                                return@withConnection SmbResult.Error("Subfolder '$targetPath' does not exist on share '${connectionInfo.shareName}'")
                            }
                        } catch (e: Exception) {
                            pathWarning = "\n⚠ Warning: Could not verify path '$targetPath' (${e.message})"
                        }
                    }

                    // Count folders and media files in target path (or root if path doesn't exist)
                    val scanPath = if (pathWarning.isEmpty()) targetPath else ""
                    val files = share.list(scanPath).filter { !it.fileName.startsWith(".") }
                    val folders = files.count { (it.fileAttributes and 0x10L) != 0L }
                    val mediaFiles = files.filter { file ->
                        val ext = file.fileName.substringAfterLast('.', "").lowercase()
                        com.sza.fastmediasorter.domain.model.MediaExtensions.isImage(ext) ||
                        com.sza.fastmediasorter.domain.model.MediaExtensions.isVideo(ext) ||
                        com.sza.fastmediasorter.domain.model.MediaExtensions.isAudio(ext)
                    }
                    
                    val message = """
                        |✓ Resource accessible: $fullPathDisplay$pathWarning
                        |
                        |Statistics:
                        |• Subfolders: $folders
                        |• Media files: ${mediaFiles.size}
                        |• Total items: ${files.size}
                    """.trimMargin()
                    
                    SmbResult.Success(message)
                }
            }
        } catch (e: Exception) {
            // Re-throw to be caught by outer retry logic in testConnection()
            throw e
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
            connectionManager.withConnection(connectionInfo) { share ->
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
                            size = fileInfo.endOfFile,
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
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        scanSubdirectories: Boolean = true,
        progressCallback: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback? = null,
        includeDirectories: Boolean = false
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            val startTime = System.currentTimeMillis()
            val mediaFiles = mutableListOf<SmbFileInfo>()
            
            connectionManager.withConnection(connectionInfo) { share ->
                val scannerResults = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
                if (scanSubdirectories) {
                    directoryScanner.scanDirectoryRecursive(share, remotePath, extensions, scannerResults, progressCallback)
                } else {
                    directoryScanner.scanDirectoryNonRecursive(share, remotePath, extensions, scannerResults, Int.MAX_VALUE, progressCallback, includeDirectories)
                }
                // Convert SmbDirectoryScanner.SmbFileInfo to SmbClient.SmbFileInfo
                val convertedFiles = scannerResults.map { scannerFile ->
                    SmbFileInfo(
                        name = scannerFile.name,
                        path = scannerFile.path,
                        isDirectory = scannerFile.isDirectory,
                        size = scannerFile.size,
                        lastModified = scannerFile.lastModified
                    )
                }
                mediaFiles.addAll(convertedFiles)
                SmbResult.Success(mediaFiles)
            }.also {
                if (it is SmbResult.Success) {
                    val durationMs = System.currentTimeMillis() - startTime
                    progressCallback?.onComplete(it.data.size, durationMs)
                }
            }
        } catch (e: CancellationException) {
            throw e
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
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        maxFiles: Int = 100,
        scanSubdirectories: Boolean = true
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            Timber.d("SmbClient.scanMediaFilesChunked: START - share=${connectionInfo.shareName}, remotePath=$remotePath, maxFiles=$maxFiles, scanSubdirectories=$scanSubdirectories")
            
            val mediaFiles = mutableListOf<SmbFileInfo>()
            
            connectionManager.withConnection(connectionInfo) { share ->
                Timber.d("SmbClient.scanMediaFilesChunked: Connection established, starting scan")
                val scannerResults = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
                if (scanSubdirectories) {
                    directoryScanner.scanDirectoryRecursiveWithLimit(share, remotePath, extensions, scannerResults, maxFiles)
                } else {
                    // Only scan root folder, no recursion
                    directoryScanner.scanDirectoryNonRecursive(share, remotePath, extensions, scannerResults, maxFiles, null)
                }
                val convertedFiles = scannerResults.map { scannerFile ->
                    SmbFileInfo(
                        name = scannerFile.name,
                        path = scannerFile.path,
                        isDirectory = scannerFile.isDirectory,
                        size = scannerFile.size,
                        lastModified = scannerFile.lastModified
                    )
                }
                mediaFiles.addAll(convertedFiles)
                Timber.d("SmbClient.scanMediaFilesChunked: Scan completed, found ${mediaFiles.size} files")
                SmbResult.Success(mediaFiles)
            }
        } catch (e: CancellationException) {
            throw e
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
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        offset: Int = 0,
        limit: Int = 50,
        scanSubdirectories: Boolean = true
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            val startTime = System.currentTimeMillis()
            val mediaFiles = mutableListOf<SmbFileInfo>()
            var skippedCount = 0
            
            connectionManager.withConnection(connectionInfo) { share ->
                val scannerResults = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
                if (scanSubdirectories) {
                    directoryScanner.scanDirectoryWithOffsetLimit(share, remotePath, extensions, scannerResults, offset, limit, skippedCount)
                } else {
                    directoryScanner.scanDirectoryNonRecursiveWithOffset(share, remotePath, extensions, scannerResults, offset, limit)
                }
                val convertedFiles = scannerResults.map { scannerFile ->
                    SmbFileInfo(
                        name = scannerFile.name,
                        path = scannerFile.path,
                        isDirectory = scannerFile.isDirectory,
                        size = scannerFile.size,
                        lastModified = scannerFile.lastModified
                    )
                }
                mediaFiles.addAll(convertedFiles)
                Timber.d("SmbClient.scanMediaFilesPaged: offset=$offset, limit=$limit, scanSubdirs=$scanSubdirectories, returned=${mediaFiles.size}, took ${System.currentTimeMillis() - startTime}ms")
                SmbResult.Success(mediaFiles)
            }
        } catch (e: CancellationException) {
            throw e
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
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        maxCount: Int = 1000, // Fast initial scan: stop at 1000 to return quickly
        scanSubdirectories: Boolean = true
    ): SmbResult<Int> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val count = if (scanSubdirectories) {
                    directoryScanner.countDirectoryRecursive(share, remotePath, extensions, maxCount)
                } else {
                    directoryScanner.countDirectoryNonRecursive(share, remotePath, extensions, maxCount)
                }
                if (count >= maxCount) {
                    Timber.d("Fast count limit reached: $maxCount+ files")
                }
                SmbResult.Success(count)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to count SMB media files")
            SmbResult.Error("Failed to count media files: ${e.message}", e)
        }
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
            val client = connectionManager.getClient(server, port)
            val connection = client.connect(server, port)
            val finalDomain = domain.trim().ifEmpty { null }
            Timber.d("SMB ListShares Auth: hasUser=${username.isNotBlank()}, hasDomain=${!finalDomain.isNullOrBlank()}, pwdLen=${password.length}")

            val authContext = if (username.isEmpty()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(username, password.toCharArray(), finalDomain)
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
     * Download file from SMB to local output stream.
     * Delegates to SmbFileOperations.
     */
    suspend fun downloadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localOutputStream: OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<Unit> {
        // fileSize is unused by fileOperations but kept for API compatibility
        if (fileSize > 0) Timber.v("SmbClient.downloadFile: hinted size=$fileSize")
        return fileOperations.downloadFile(connectionInfo, remotePath, localOutputStream, progressCallback)
    }

    /**
     * Read file bytes from SMB (useful for thumbnails and image loading).
     * Delegates to SmbFileOperations.
     */
    suspend fun readFileBytes(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): SmbResult<ByteArray> {
        return fileOperations.readFileBytes(connectionInfo, remotePath, maxBytes)
    }

    /**
     * Read partial file bytes from SMB (useful for optimized video thumbnail extraction).
     * Delegates to SmbFileOperations.
     */
    suspend fun readPartialFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        offset: Long,
        length: Int
    ): SmbResult<ByteArray> {
        return fileOperations.readPartialFile(connectionInfo, remotePath, offset, length)
    }
    
    /**
     * Read specific byte range from file.
     * Delegates to SmbFileOperations.
     */
    suspend fun readFileBytesRange(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        offset: Long,
        length: Long
    ): SmbResult<ByteArray> {
        return fileOperations.readFileBytesRange(connectionInfo, remotePath, offset, length)
    }

    /**
     * Upload file from local input stream to SMB.
     * Delegates to SmbFileOperations.
     */
    suspend fun uploadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localInputStream: InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<Unit> {
        return fileOperations.uploadFile(connectionInfo, remotePath, localInputStream, fileSize, progressCallback)
    }

    /**
     * Delete file on SMB share
     */
    suspend fun deleteFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        Timber.d("SmbClient.deleteFile: START - remotePath='$remotePath'")
        Timber.d("SmbClient.deleteFile: Connection - server=${connectionInfo.server}, share=${connectionInfo.shareName}, port=${connectionInfo.port}")
        Timber.d("SmbClient.deleteFile: Credentials - username=${connectionInfo.username}, domain=${connectionInfo.domain}")
        
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                Timber.d("SmbClient.deleteFile: Share connected, checking if file exists...")
                
                // Check if file exists before deleting
                val exists = try {
                    share.fileExists(remotePath.trim('/', '\\'))
                } catch (e: Exception) {
                    Timber.w(e, "SmbClient.deleteFile: Failed to check file existence")
                    false
                }
                
                if (!exists) {
                    Timber.e("SmbClient.deleteFile: File does not exist: $remotePath")
                    return@withConnection SmbResult.Error("File not found: $remotePath", Exception("File does not exist"))
                }
                
                Timber.d("SmbClient.deleteFile: File exists, attempting to delete...")
                
                try {
                    share.rm(remotePath.trim('/', '\\'))
                    Timber.i("SmbClient.deleteFile: SUCCESS - File deleted: $remotePath")
                    SmbResult.Success(Unit)
                } catch (deleteEx: Exception) {
                    Timber.e(deleteEx, "SmbClient.deleteFile: FAILED - Exception during rm() call")
                    Timber.e("SmbClient.deleteFile: Delete error type: ${deleteEx.javaClass.name}")
                    Timber.e("SmbClient.deleteFile: Delete error message: ${deleteEx.message}")
                    deleteEx.cause?.let { cause ->
                        Timber.e("SmbClient.deleteFile: Cause: ${cause.javaClass.name} - ${cause.message}")
                    }
                    SmbResult.Error("Delete operation failed: ${deleteEx.message}", deleteEx)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbClient.deleteFile: EXCEPTION - Failed to establish connection or execute delete")
            Timber.e("SmbClient.deleteFile: Exception type: ${e.javaClass.name}")
            Timber.e("SmbClient.deleteFile: Exception message: ${e.message}")
            e.cause?.let { cause ->
                Timber.e("SmbClient.deleteFile: Cause: ${cause.javaClass.name} - ${cause.message}")
            }
            SmbResult.Error("Failed to delete file: ${e.message}", e)
        }
    }

    /**
     * Delete directory recursively on SMB share
     */
    suspend fun deleteDirectory(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        Timber.d("SmbClient.deleteDirectory: START - remotePath='$remotePath'")
        
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                if (!share.fileExists(remotePath.trim('/', '\\'))) {
                    Timber.w("SmbClient.deleteDirectory: Directory does not exist: $remotePath")
                    return@withConnection SmbResult.Success(Unit)
                }
                
                try {
                    share.rmdir(remotePath.trim('/', '\\'), true)
                    Timber.i("SmbClient.deleteDirectory: SUCCESS - Directory deleted: $remotePath")
                    SmbResult.Success(Unit)
                } catch (deleteEx: Exception) {
                    Timber.e(deleteEx, "SmbClient.deleteDirectory: FAILED - ${deleteEx.message}")
                    SmbResult.Error("Delete directory failed: ${deleteEx.message}", deleteEx)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbClient.deleteDirectory: EXCEPTION - ${e.message}")
            SmbResult.Error("Failed to delete directory: ${e.message}", e)
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
            connectionManager.withConnection(connectionInfo) { share ->
                val fixedOldPath = oldPath.trim('/', '\\')
                // Parse newName: if contains '/', treat it as full path from share root
                // Otherwise, keep in same directory
                val newPath = if (newName.contains('/')) {
                    newName.trim('/', '\\')
                } else {
                    val directory = fixedOldPath.substringBeforeLast('/', "")
                    if (directory.isEmpty()) newName else "$directory/$newName"
                }
                
                Timber.d("Renaming SMB file: oldPath='$fixedOldPath' → newPath='$newPath'")
                
                // Validate new name (no invalid SMB characters: \ / : * ? " < > |)
                val invalidChars = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
                val newFileName = newPath.substringAfterLast('/')
                if (newFileName.any { it in invalidChars }) {
                    Timber.e("SMB rename: Invalid characters in new name: $newFileName")
                    return@withConnection SmbResult.Error("New name contains invalid characters: ${invalidChars.filter { it in newFileName }}")
                }
                
                // Check if target exists
                val targetExists = try {
                    share.fileExists(newPath)
                } catch (e: Exception) {
                    Timber.w(e, "SMB rename: Error checking target existence, assuming not exists")
                    false
                }
                
                if (targetExists) {
                    Timber.e("SMB rename: Target file already exists: $newPath")
                    return@withConnection SmbResult.Error("File already exists at target location")
                }
                
                // Open source file for rename
                val file = try {
                    share.openFile(
                        fixedOldPath,
                        EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                } catch (e: Exception) {
                    Timber.e(e, "SMB rename: Failed to open source file: $fixedOldPath")
                    return@withConnection SmbResult.Error("Failed to open source file: ${e.message}")
                }
                
                file.use {
                    try {
                        // SMBJ rename() accepts full path relative to share root
                        it.rename(newPath, false)
                        Timber.i("Successfully renamed SMB file to: $newPath")
                    } catch (e: Exception) {
                        Timber.e(e, "SMB rename: rename() call failed for $fixedOldPath → $newPath")
                        throw e
                    }
                }
                
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to rename file on SMB")
            SmbResult.Error("Failed to rename file: ${e.message}", e)
        }
    }

    /**
     * Move file to different location on SMB share (copy + delete)
     * Use this instead of renameFile when moving to subdirectories
     */
    suspend fun moveFile(
        connectionInfo: SmbConnectionInfo,
        sourcePath: String,
        destinationPath: String
    ): SmbResult<Unit> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val fixedSource = sourcePath.trim('/', '\\')
                val fixedDest = destinationPath.trim('/', '\\')
                Timber.d("Moving SMB file: sourcePath='$fixedSource' → destinationPath='$fixedDest'")
                
                // Check if source exists
                if (!share.fileExists(fixedSource)) {
                    return@withConnection SmbResult.Error("Source file does not exist: $fixedSource")
                }
                
                // Check if destination exists
                if (share.fileExists(fixedDest)) {
                    return@withConnection SmbResult.Error("Destination file already exists: $fixedDest")
                }
                
                // Ensure parent directory exists for destination
                // Handle both forward and back slashes by replacing backslashes with forward slashes
                val normalizedDest = fixedDest.replace('\\', '/')
                val destParent = normalizedDest.substringBeforeLast('/', "")
                Timber.d("MoveFile: fixedDest='$fixedDest', destParent='$destParent', isEmpty=${destParent.isEmpty()}")
                if (destParent.isNotEmpty()) {
                    Timber.d("Ensuring parent directory exists for move: $destParent")
                    ensureSmbDirectoryExists(share, destParent)
                    // Verify directory was created
                    if (!share.folderExists(destParent)) {
                        return@withConnection SmbResult.Error("Failed to create destination directory: $destParent")
                    }
                    Timber.d("Verified parent directory exists: $destParent")
                }
                
                // Open source file for reading
                val sourceFile = share.openFile(
                    fixedSource,
                    EnumSet.of(AccessMask.GENERIC_READ, AccessMask.DELETE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                try {
                    // Open destination file for writing
                    val destFile = share.openFile(
                        fixedDest,
                        EnumSet.of(AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_CREATE,
                        null
                    )
                    
                    try {
                        // Copy data
                        sourceFile.inputStream.use { input ->
                            destFile.outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Delete source file after successful copy
                        sourceFile.deleteOnClose()
                        
                        Timber.i("Successfully moved SMB file to: $fixedDest")
                        SmbResult.Success(Unit)
                    } finally {
                        destFile.close()
                    }
                } finally {
                    sourceFile.close()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to move file on SMB")
            SmbResult.Error("Failed to move file: ${e.message}", e)
        }
    }
    
    /**
     * Recursively create directory structure on SMB share.
     * Handles race conditions where directory might be created by another process.
     * @param share The connected DiskShare
     * @param path The directory path to create (relative to share root)
     */
    private fun ensureSmbDirectoryExists(share: DiskShare, path: String) {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        var currentPath = ""
        for (part in parts) {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            if (!share.folderExists(currentPath)) {
                Timber.d("ensureSmbDirectoryExists: Creating $currentPath")
                try {
                    share.mkdir(currentPath)
                    Timber.d("ensureSmbDirectoryExists: Successfully created $currentPath")
                } catch (e: Exception) {
                    // Check if directory was created despite exception (race condition)
                    if (!share.folderExists(currentPath)) {
                        Timber.e(e, "ensureSmbDirectoryExists: Failed to create $currentPath")
                        throw e
                    } else {
                        Timber.d("ensureSmbDirectoryExists: Directory $currentPath already exists")
                    }
                }
            }
        }
    }

    /**
     * Create directory on SMB share (recursively)
     */
    suspend fun createDirectory(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                ensureSmbDirectoryExists(share, remotePath.trim('/', '\\'))
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
            connectionManager.withConnection(connectionInfo) { share ->
                val exists = share.fileExists(remotePath.trim('/', '\\'))
                SmbResult.Success(exists)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check if path exists on SMB")
            SmbResult.Error("Failed to check path: ${e.message}", e)
        }
    }

    /**
     * Retrieve file metadata for a single SMB path without listing entire directories.
     * Used as a fallback when cached lists are out of sync with remote storage.
     */
    suspend fun getFileInfo(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<SmbFileInfo> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val fixedPath = remotePath.trim('/', '\\')
                if (!share.fileExists(fixedPath)) {
                    Timber.w("SmbClient.getFileInfo: File not found: $fixedPath")
                    return@withConnection SmbResult.Error("File not found: $fixedPath")
                }

                val info = try {
                    share.getFileInformation(fixedPath)
                } catch (infoError: Exception) {
                    Timber.e(infoError, "SmbClient.getFileInfo: Failed to read metadata for $remotePath")
                    return@withConnection SmbResult.Error(
                        "Failed to read file info: ${infoError.message}",
                        infoError
                    )
                }

                val name = remotePath.substringAfterLast('/').ifEmpty { remotePath }
                val size = info.standardInformation?.endOfFile ?: 0L
                val lastModified = info.basicInformation?.lastWriteTime?.toEpochMillis()
                    ?: System.currentTimeMillis()

                SmbResult.Success(
                    SmbFileInfo(
                        name = name,
                        path = remotePath,
                        isDirectory = false,
                        size = size,
                        lastModified = lastModified
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get SMB file info for $remotePath")
            SmbResult.Error("Failed to get file info: ${e.message}", e)
        }
    }

    /**
     * Get user-friendly error message based on exception type
     */
    private fun getUserFriendlyMessage(exception: Exception): String {
        val message = exception.message ?: ""
        val causeMessage = exception.cause?.message ?: ""
        val rootCauseMessage = exception.cause?.cause?.message ?: ""
        
        return when {
            // Connection reset errors
            message.contains("Connection reset", ignoreCase = true) ||
            causeMessage.contains("Connection reset", ignoreCase = true) ||
            rootCauseMessage.contains("Connection reset", ignoreCase = true) ->
                """Connection interrupted by server. This is usually temporary.
                |
                |Possible causes:
                |• Server restarted or network equipment reset
                |• Firewall dropped the connection
                |• Too many simultaneous connections
                |• SMB protocol version mismatch
                |
                |Try:
                |• Wait a moment and try again
                |• Check if server is accessible from other devices
                |• Verify SMB settings on server""".trimMargin()
            
            // Timeout errors
            message.contains("TimeoutException", ignoreCase = true) ||
            message.contains("Timeout expired", ignoreCase = true) ||
            causeMessage.contains("TimeoutException", ignoreCase = true) ||
            causeMessage.contains("Timeout expired", ignoreCase = true) ->
                """Connection timeout. Server not responding or network is very slow.
                |
                |This can happen with:
                |• Slow network connection
                |• Server under heavy load
                |• Firewall blocking traffic
                |• Wrong server address
                |
                |Try:
                |• Check network connection
                |• Verify server is online
                |• Wait a moment and try again""".trimMargin()
            
            // Network errors
            message.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ->
                """Share not found on server.
                |
                |Possible reasons:
                |• Share name is incorrect or doesn't exist
                |• Share was renamed or removed
                |• Share is hidden (hidden$ shares need exact name)
                |
                |Try:
                |• Use 'Discover SMB Resources' to see available shares
                |• Check share name on the server
                |• Verify share is enabled and visible""".trimMargin()
            message.contains("STATUS_LOGON_FAILURE", ignoreCase = true) ->
                "Authentication failed. Check username and password."
            message.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ->
                "Access denied. Check share permissions."
            message.contains("ConnectException", ignoreCase = true) || 
            message.contains("NoRouteToHostException", ignoreCase = true) ->
                "Cannot reach server. Check network connection."
            message.contains("SocketTimeoutException", ignoreCase = true) ->
                "Connection timeout. Server not responding."
            message.contains("UnknownHostException", ignoreCase = true) ->
                "Server address not found. Check server name/IP."
            
            else -> "Resource unavailable. Check connection settings."
        }
    }
    
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
            connectionManager.withConnection(connectionInfo) { share ->
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
        connectionManager.close()
    }
    
    /**
     * Clear connection pool (used when refreshing resources or on connection issues)
     */
    fun clearConnectionPool() {
        connectionManager.clearConnectionPool()
    }
    
    /**
     * Force full reset: close all connections and reset clients
     * Used when user manually refreshes or encounters persistent issues
     */
    fun forceFullReset() {
        connectionManager.forceFullReset()
    }

    /**
     * Open InputStream for reading file from SMB.
     * Caller is responsible for closing the stream.
     * The stream wrapper ensures the underlying SMB file handle is closed.
     */
    suspend fun openInputStream(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<InputStream> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                // Return wrapper that closes the file when stream is closed
                val inputStream = object : java.io.FilterInputStream(file.inputStream) {
                    override fun close() {
                        try {
                            super.close()
                        } catch (e: Exception) {
                            Timber.w(e, "Error closing SMB input stream")
                        } finally {
                            try {
                                // CRITICAL FIX: Clear interruption status before closing file handle.
                                // If the thread is interrupted (e.g. Coil cancellation), smbj will fail to send
                                // the Close packet and might tear down the connection.
                                // We save the status to restore it later if needed, but for now we want the Close to succeed.
                                val interrupted = Thread.interrupted()
                                file.close()
                                if (interrupted) {
                                    Thread.currentThread().interrupt() // Restore status
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Error closing SMB file handle")
                            }
                        }
                    }
                }
                
                SmbResult.Success(inputStream)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open SMB input stream")
            SmbResult.Error("Failed to open stream: ${e.message}", e)
        }
    }
}
