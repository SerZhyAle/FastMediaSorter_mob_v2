package com.sza.fastmediasorter.network

import com.sza.fastmediasorter.utils.Logger
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * SMB/CIFS client for network file operations.
 * Provides connection management, authentication, file listing, and data transfer
 * capabilities for accessing remote SMB shares. Uses jcifs-ng library for SMB protocol implementation.
 */
class SmbClient {
    private var context: CIFSContext? = null

    fun getContext(): CIFSContext? = context

    private fun buildFullDiagnostic(
        e: Exception?,
        serverAddress: String,
        folderPath: String,
    ): String {
        val diagnostic = StringBuilder()

        // Header
        diagnostic.append("=== SMB CONNECTION TEST DIAGNOSTIC ===\n")
        diagnostic.append(
            "Date: ${java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US,
            ).format(java.util.Date())}\n",
        )
        diagnostic.append("Server: $serverAddress\n")
        diagnostic.append("Folder: $folderPath\n\n")

        // Error information with specific SMB error analysis
        if (e != null) {
            diagnostic.append("=== ERROR DETAILS ===\n")
            diagnostic.append("Exception: ${e.javaClass.simpleName}\n")
            diagnostic.append("Message: ${e.message ?: "No message"}\n")
            diagnostic.append("Cause: ${e.cause?.javaClass?.simpleName ?: "None"}\n")
            if (e.cause != null) {
                diagnostic.append("Cause Message: ${e.cause?.message ?: "No message"}\n")
            }

            // Specific SMB error analysis
            if (e is jcifs.smb.SmbException) {
                diagnostic.append("\n=== SMB ERROR ANALYSIS ===\n")

                val errorMessage = e.message ?: ""
                val ntStatus = e.ntStatus // NT status code if available

                // Analyze NT status codes
                when (ntStatus) {
                    -1073741790 -> { // STATUS_ACCESS_DENIED (0xC0000022)
                        diagnostic.append("NT Status: 0xC0000022 (STATUS_ACCESS_DENIED)\n")
                        diagnostic.append("Diagnosis: Access denied - insufficient permissions\n")
                        diagnostic.append("Solutions:\n")
                        diagnostic.append("• Check username/password\n")
                        diagnostic.append("• Verify folder permissions on server\n")
                        diagnostic.append("• Ensure share permissions allow access\n")
                        diagnostic.append("• Try different credentials\n")
                    }
                    -1073741788 -> { // STATUS_OBJECT_NAME_NOT_FOUND (0xC0000034)
                        diagnostic.append("NT Status: 0xC0000034 (STATUS_OBJECT_NAME_NOT_FOUND)\n")
                        diagnostic.append("Diagnosis: Path or share not found\n")
                        diagnostic.append("Solutions:\n")
                        diagnostic.append("• Verify server address is correct\n")
                        diagnostic.append("• Check share name spelling\n")
                        diagnostic.append("• Ensure share is enabled on server\n")
                        diagnostic.append("• Try accessing share from another device\n")
                    }
                    -1073741782 -> { // STATUS_OBJECT_PATH_NOT_FOUND (0xC000003A)
                        diagnostic.append("NT Status: 0xC000003A (STATUS_OBJECT_PATH_NOT_FOUND)\n")
                        diagnostic.append("Diagnosis: Folder path within share not found\n")
                        diagnostic.append("Solutions:\n")
                        diagnostic.append("• Check folder path spelling\n")
                        diagnostic.append("• Verify folder exists on server\n")
                        diagnostic.append("• Ensure path separators are correct (/ not \\)\n")
                    }
                    -1073741724 -> { // STATUS_ACCOUNT_LOCKED_OUT (0xC000023C)
                        diagnostic.append("NT Status: 0xC000023C (STATUS_ACCOUNT_LOCKED_OUT)\n")
                        diagnostic.append("Diagnosis: User account is locked\n")
                        diagnostic.append("Solutions:\n")
                        diagnostic.append("• Unlock account on server\n")
                        diagnostic.append("• Wait for automatic unlock or contact administrator\n")
                        diagnostic.append("• Try different user account\n")
                    }
                    -1073741715 -> { // STATUS_LOGON_FAILURE (0xC000006D)
                        diagnostic.append("NT Status: 0xC000006D (STATUS_LOGON_FAILURE)\n")
                        diagnostic.append("Diagnosis: Invalid username or password\n")
                        diagnostic.append("Solutions:\n")
                        diagnostic.append("• Verify username and password\n")
                        diagnostic.append("• Check if Caps Lock is on\n")
                        diagnostic.append("• Try domain\\username format if using domain\n")
                        diagnostic.append("• Ensure account is not disabled\n")
                    }
                    -1073741714 -> { // STATUS_ACCOUNT_RESTRICTION (0xC000006E)
                        diagnostic.append("NT Status: 0xC000006E (STATUS_ACCOUNT_RESTRICTION)\n")
                        diagnostic.append("Diagnosis: Account restrictions prevent login\n")
                        diagnostic.append("Solutions:\n")
                        diagnostic.append("• Check account login hours\n")
                        diagnostic.append("• Verify account is allowed to login from this location\n")
                        diagnostic.append("• Contact server administrator\n")
                    }
                    -1073741711 -> { // STATUS_PASSWORD_EXPIRED (0xC0000071)
                        diagnostic.append("NT Status: 0xC0000071 (STATUS_PASSWORD_EXPIRED)\n")
                        diagnostic.append("Diagnosis: Password has expired\n")
                        diagnostic.append("Solutions:\n")
                        diagnostic.append("• Change password on server\n")
                        diagnostic.append("• Contact server administrator\n")
                    }
                    -1073741485 -> { // STATUS_TIME_DIFFERENCE_AT_DC (0xC0000133)
                        diagnostic.append("NT Status: 0xC0000133 (STATUS_TIME_DIFFERENCE_AT_DC)\n")
                        diagnostic.append("Diagnosis: Time difference between devices too large\n")
                        diagnostic.append("Solutions:\n")
                        diagnostic.append("• Sync time on Android device\n")
                        diagnostic.append("• Sync time on server\n")
                        diagnostic.append("• Check timezone settings\n")
                    }
                    else -> {
                        if (ntStatus != 0) {
                            diagnostic.append("NT Status: 0x${ntStatus.toString(16).uppercase()} (Unknown)\n")
                        }
                        diagnostic.append("Diagnosis: Unspecified SMB error\n")
                    }
                }

                // Additional analysis based on error message content
                when {
                    errorMessage.contains("Access is denied", ignoreCase = true) -> {
                        diagnostic.append("\nAdditional Analysis: Access denied detected\n")
                        diagnostic.append("Common causes:\n")
                        diagnostic.append("• Insufficient share permissions\n")
                        diagnostic.append("• NTFS permissions too restrictive\n")
                        diagnostic.append("• Guest access disabled\n")
                    }
                    errorMessage.contains("network name cannot be found", ignoreCase = true) ||
                        errorMessage.contains("network path was not found", ignoreCase = true) -> {
                        diagnostic.append("\nAdditional Analysis: Network path not found\n")
                        diagnostic.append("Common causes:\n")
                        diagnostic.append("• Server name/IP incorrect\n")
                        diagnostic.append("• Server not running\n")
                        diagnostic.append("• Firewall blocking SMB\n")
                        diagnostic.append("• DNS resolution failure\n")
                    }
                    errorMessage.contains("timed out", ignoreCase = true) -> {
                        diagnostic.append("\nAdditional Analysis: Connection timeout\n")
                        diagnostic.append("Common causes:\n")
                        diagnostic.append("• Network connectivity issues\n")
                        diagnostic.append("• Server overloaded or unresponsive\n")
                        diagnostic.append("• Firewall blocking connection\n")
                        diagnostic.append("• VPN interfering with connection\n")
                    }
                    errorMessage.contains("Connection refused", ignoreCase = true) -> {
                        diagnostic.append("\nAdditional Analysis: Connection refused\n")
                        diagnostic.append("Common causes:\n")
                        diagnostic.append("• SMB service not running on server\n")
                        diagnostic.append("• Port 445 blocked by firewall\n")
                        diagnostic.append("• Server rejecting connections\n")
                    }
                    errorMessage.contains("algorithm", ignoreCase = true) ||
                        errorMessage.contains("MD4", ignoreCase = true) ||
                        errorMessage.contains("provider", ignoreCase = true) -> {
                        diagnostic.append("\nAdditional Analysis: Security/Algorithm error\n")
                        diagnostic.append("This indicates MD4 or cryptographic provider issues\n")
                    }
                }
            }

            diagnostic.append("\n")
        }

        // Security providers diagnostic
        diagnostic.append("=== SECURITY PROVIDERS ===\n")
        try {
            val providers = java.security.Security.getProviders()
            diagnostic.append("Total Providers: ${providers.size}\n\n")

            providers.forEachIndexed { index, provider ->
                diagnostic.append("${index + 1}. ${provider.name}\n")
                diagnostic.append("   Version: ${provider.version}\n")
                diagnostic.append("   Info: ${provider.info}\n")
                diagnostic.append("   Class: ${provider.javaClass.name}\n\n")
            }

            // BC Provider specific check
            val bcProvider = java.security.Security.getProvider("BC")
            diagnostic.append("=== BOUNCYCASTLE PROVIDER CHECK ===\n")
            if (bcProvider != null) {
                diagnostic.append("✓ BouncyCastle Provider: FOUND\n")
                diagnostic.append("  Name: ${bcProvider.name}\n")
                diagnostic.append("  Version: ${bcProvider.version}\n")
                diagnostic.append("  Class: ${bcProvider.javaClass.name}\n\n")

                // Test MD4 algorithm
                diagnostic.append("=== MD4 ALGORITHM TEST ===\n")
                try {
                    val md4 = java.security.MessageDigest.getInstance("MD4", "BC")
                    diagnostic.append("✓ MD4 Algorithm: AVAILABLE\n")
                    diagnostic.append("  Provider: ${md4.provider.name}\n")
                    diagnostic.append("  Algorithm: ${md4.algorithm}\n")
                } catch (md4e: Exception) {
                    diagnostic.append("✗ MD4 Algorithm: FAILED\n")
                    diagnostic.append("  Error: ${md4e.javaClass.simpleName}\n")
                    diagnostic.append("  Message: ${md4e.message}\n")
                    diagnostic.append("  This is likely the cause of authentication errors!\n")
                }
            } else {
                diagnostic.append("✗ BouncyCastle Provider: NOT FOUND\n")
                diagnostic.append("  This is likely the cause of MD4 errors!\n")
                diagnostic.append("  Solutions:\n")
                diagnostic.append("  • Ensure BouncyCastle library is included\n")
                diagnostic.append("  • Check proguard rules don't remove BC provider\n")
                diagnostic.append("  • Verify library version compatibility\n")
            }
            diagnostic.append("\n")
        } catch (provEx: Exception) {
            diagnostic.append("Error checking security providers: ${provEx.message}\n\n")
        }

        // System information
        diagnostic.append("=== SYSTEM INFORMATION ===\n")
        diagnostic.append(
            "Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n",
        )
        diagnostic.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
        diagnostic.append("JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}\n")
        diagnostic.append("Java Version: ${System.getProperty("java.version")}\n\n")

        // Network information
        diagnostic.append("=== NETWORK CONFIGURATION ===\n")
        try {
            val connectivityManager = android.content.Context.CONNECTIVITY_SERVICE
            diagnostic.append("WiFi Status: Available\n") // Basic check
            diagnostic.append("Network Recommendations:\n")
            diagnostic.append("• Ensure same WiFi network as server\n")
            diagnostic.append("• Check firewall settings (ports 445, 139)\n")
            diagnostic.append("• Verify VPN is not interfering\n")
            diagnostic.append("• Test ping to server IP\n")
        } catch (netEx: Exception) {
            diagnostic.append("Network check failed: ${netEx.message}\n")
        }
        diagnostic.append("\n")

        // Stack trace for errors
        if (e != null) {
            diagnostic.append("=== FULL STACK TRACE ===\n")
            diagnostic.append(android.util.Log.getStackTraceString(e))
        }

        return diagnostic.toString()
    }

    suspend fun connect(
        @Suppress("UNUSED_PARAMETER") serverAddress: String,
        username: String,
        password: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure BouncyCastle provider is available
                try {
                    java.security.Security.getProvider("BC") ?: run {
                        val bcProvider =
                            Class
                                .forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                                .getDeclaredConstructor()
                                .newInstance() as java.security.Provider
                        java.security.Security.addProvider(bcProvider)
                        Logger.d("SmbClient", "BouncyCastle provider added")
                    }
                } catch (e: Exception) {
                    Logger.e("SmbClient", "Failed to ensure BC provider", e)
                }

                val props =
                    Properties().apply {
                        setProperty("jcifs.smb.client.minVersion", "SMB202")
                        setProperty("jcifs.smb.client.maxVersion", "SMB311")
                        setProperty("jcifs.resolveOrder", "DNS")
                        setProperty("jcifs.smb.client.responseTimeout", "3000")
                        setProperty("jcifs.smb.client.connTimeout", "3000")
                    }

                val config = PropertyConfiguration(props)
                val baseContext = BaseContext(config)

                // Clear existing context before creating new one to prevent resource leaks
                context?.let {
                    try {
                        (it as? AutoCloseable)?.close()
                    } catch (e: Exception) {
                        Logger.w("SmbClient", "Failed to close previous context", e)
                    }
                }
                context = null

                context =
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        val auth = NtlmPasswordAuthenticator(null, username, password)
                        baseContext.withCredentials(auth)
                    } else {
                        baseContext.withAnonymousCredentials()
                    }

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun listShares(serverAddress: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (context == null) {
                    Logger.e("SmbClient", "Not connected to server")
                    return@withContext emptyList()
                }

                val serverUrl = "smb://$serverAddress/"
                Logger.d("SmbClient", "Listing shares from: $serverUrl")

                val serverFile = SmbFile(serverUrl, context)
                val shares = mutableListOf<String>()

                serverFile.listFiles()?.forEach { share ->
                    if (share.isDirectory) {
                        val shareName = share.name.trimEnd('/')
                        // Filter out administrative shares
                        if (!shareName.endsWith("$") && shareName.isNotEmpty()) {
                            // Test if readable
                            try {
                                share.listFiles() // Try to list contents
                                shares.add(shareName)
                                Logger.d("SmbClient", "Found accessible share: $shareName")
                            } catch (e: Exception) {
                                Logger.d("SmbClient", "Share not accessible: $shareName - ${e.message}")
                            }
                        }
                    }
                }

                Logger.d("SmbClient", "Total accessible shares found: ${shares.size}")
                shares
            } catch (e: Exception) {
                Logger.e("SmbClient", "Failed to list shares: ${e.message}", e)
                emptyList()
            }
        }
    }

    data class ImageFilesResult(
        val files: List<String>,
        val errorMessage: String? = null,
        val warningMessage: String? = null,
    )

    suspend fun getImageFiles(
        serverAddress: String,
        folderPath: String,
        isVideoEnabled: Boolean = false,
        maxVideoSizeMb: Int = 100,
    ): ImageFilesResult {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d(
                    "SmbClient",
                    "getImageFiles - server: $serverAddress, folder: $folderPath, videoEnabled: $isVideoEnabled",
                )

                // Diagnostic check for BC provider and MD4 availability BEFORE SMB operations
                val bcDiagnostic = StringBuilder()
                try {
                    val providers = java.security.Security.getProviders()
                    bcDiagnostic.append("Security Providers: ${providers.size}\n")
                    providers.forEach { p -> bcDiagnostic.append("${p.name} v${p.version}; ") }
                    bcDiagnostic.append("\n")

                    val bcProvider = java.security.Security.getProvider("BC")
                    if (bcProvider != null) {
                        bcDiagnostic.append("BC Provider: FOUND\n")
                        try {
                            java.security.MessageDigest.getInstance("MD4", "BC")
                            bcDiagnostic.append("MD4 Test: SUCCESS\n")
                        } catch (md4e: Exception) {
                            bcDiagnostic.append("MD4 Test: FAILED - ${md4e.message}\n")
                        }
                    } else {
                        bcDiagnostic.append("BC Provider: NOT FOUND\n")
                    }
                } catch (diagEx: Exception) {
                    bcDiagnostic.append("Diagnostic error: ${diagEx.message}\n")
                }
                Logger.d("SmbClient", "Pre-SMB diagnostic:\n$bcDiagnostic")

                if (context == null) {
                    val msg = "Not connected to server. Please check connection settings.\n\n$bcDiagnostic"
                    Logger.e("SmbClient", msg)
                    return@withContext ImageFilesResult(emptyList(), msg)
                }

                val cleanServer =
                    serverAddress
                        .trim()
                        .removePrefix("smb://")
                        .removePrefix("\\\\")
                        .replace("\\", "/")

                val cleanFolder =
                    folderPath
                        .trim()
                        .removePrefix("/")
                        .removePrefix("\\")
                        .replace("\\", "/")

                val smbUrl = "smb://$cleanServer/$cleanFolder/"
                Logger.d("SmbClient", "Final SMB URL: $smbUrl")

                val smbFile = SmbFile(smbUrl, context)

                Logger.d("SmbClient", "Checking SMB path...")

                if (!smbFile.exists()) {
                    val msg = "Path not found: smb://$cleanServer/$cleanFolder/\n\nCheck:\n• Server IP correct?\n• Folder name correct?\n• Network connection?"
                    Logger.e("SmbClient", msg)
                    return@withContext ImageFilesResult(emptyList(), msg)
                }

                if (!smbFile.isDirectory()) {
                    val msg = "Path is not a folder: smb://$cleanServer/$cleanFolder/"
                    Logger.e("SmbClient", msg)
                    return@withContext ImageFilesResult(emptyList(), msg)
                }

                val mediaFiles = mutableListOf<String>()
                val maxVideoSizeBytes = maxVideoSizeMb * 1024L * 1024L

                val startListTime = System.currentTimeMillis()
                Logger.d("SmbClient", "⏱️ START: Listing directory contents...")
                val files = smbFile.listFiles()
                val listDuration = System.currentTimeMillis() - startListTime
                Logger.d(
                    "SmbClient",
                    "⏱️ DONE: Directory listing took ${listDuration}ms. Found ${files?.size ?: 0} files",
                )

                val startProcessTime = System.currentTimeMillis()
                var imageCount = 0
                var videoCount = 0
                var skippedAvi = 0
                var skippedLargeVideo = 0
                var skippedOther = 0

                files?.forEachIndexed { index, file ->
                    if (file.isFile()) {
                        val filename = file.name

                        // Log progress every 100 files
                        if (index % 100 == 0 && index > 0) {
                            val elapsed = System.currentTimeMillis() - startProcessTime
                            val rate = index.toFloat() / elapsed * 1000
                            Logger.d("SmbClient", "⏱️ Progress: $index/${files.size} files (${rate.toInt()} files/sec)")
                        }

                        // Check if it's an image
                        if (com
                                .sza
                                .fastmediasorter
                                .utils
                                .MediaUtils
                                .isImage(filename)
                        ) {
                            mediaFiles.add(file.url.toString())
                            imageCount++
                            if (imageCount <= 10) {
                                Logger.d("SmbClient", "Added image: $filename")
                            }
                            // Check if it's a video and video is enabled
                        } else if (isVideoEnabled &&
                            com
                                .sza
                                .fastmediasorter
                                .utils
                                .MediaUtils
                                .isVideo(filename)
                        ) {
                            // Skip AVI format - incompatible with jCIFS-ng SMB streaming
                            val extension = filename.substringAfterLast('.', "").lowercase()
                            if (extension == "avi") {
                                skippedAvi++
                                if (skippedAvi <= 5) {
                                    Logger.d("SmbClient", "Skipped video (AVI format not supported): $filename")
                                }
                            } else {
                                val fileSize = file.length()
                                val fileSizeMb = fileSize / (1024 * 1024)

                                if (fileSize <= maxVideoSizeBytes) {
                                    mediaFiles.add(file.url.toString())
                                    videoCount++
                                    if (videoCount <= 10) {
                                        Logger.d("SmbClient", "Added video: $filename (${fileSizeMb}MB)")
                                    }
                                } else {
                                    skippedLargeVideo++
                                    if (skippedLargeVideo <= 5) {
                                        Logger.d(
                                            "SmbClient",
                                            "Skipped video (too large): $filename (${fileSizeMb}MB > ${maxVideoSizeMb}MB)",
                                        )
                                    }
                                }
                            }
                        } else {
                            skippedOther++
                        }
                    }
                }

                val processDuration = System.currentTimeMillis() - startProcessTime
                val totalDuration = listDuration + processDuration

                Logger.d("SmbClient", "⏱️ SUMMARY:")
                Logger.d("SmbClient", "  - Directory listing: ${listDuration}ms")
                Logger.d("SmbClient", "  - File processing: ${processDuration}ms")
                Logger.d("SmbClient", "  - Total time: ${totalDuration}ms (${totalDuration / 1000.0}s)")
                Logger.d("SmbClient", "  - Total files scanned: ${files?.size ?: 0}")
                Logger.d("SmbClient", "  - Images found: $imageCount")
                Logger.d("SmbClient", "  - Videos found: $videoCount")
                Logger.d("SmbClient", "  - Skipped (AVI): $skippedAvi")
                Logger.d("SmbClient", "  - Skipped (too large): $skippedLargeVideo")
                Logger.d("SmbClient", "  - Skipped (other): $skippedOther")
                Logger.d(
                    "SmbClient",
                    "  - Processing rate: ${if (processDuration > 0) (files?.size ?: 0).toFloat() / processDuration * 1000 else 0} files/sec",
                )

                if (mediaFiles.isEmpty()) {
                    val formats =
                        if (isVideoEnabled) {
                            "JPG, PNG, GIF, BMP, WEBP, MP4, MKV, MOV, WEBM, 3GP"
                        } else {
                            "JPG, PNG, GIF, BMP, WEBP"
                        }
                    val msg = "No media files found in: smb://$cleanServer/$cleanFolder/\n\nSupported formats: $formats"
                    return@withContext ImageFilesResult(emptyList(), null, msg)
                }

                ImageFilesResult(mediaFiles.sorted())
            } catch (e: java.net.UnknownHostException) {
                val msg = "Cannot reach server: $serverAddress\n\nCheck:\n• Server IP correct?\n• Same WiFi network?\n• Server running?"
                Logger.e("SmbClient", msg, e)
                ImageFilesResult(emptyList(), msg)
            } catch (e: jcifs.smb.SmbAuthException) {
                val diagnostic = buildFullDiagnostic(e, serverAddress, folderPath)
                Logger.e("SmbClient", "SmbAuthException", e)
                ImageFilesResult(emptyList(), diagnostic)
            } catch (e: jcifs.smb.SmbException) {
                val errorMessage = e.message ?: "Unknown SMB error"
                val ntStatus = e.ntStatus

                val msg =
                    when (ntStatus) {
                        -1073741790 -> // STATUS_ACCESS_DENIED (0xC0000022)
                            "Access denied to: smb://$serverAddress/$folderPath/\n\nThis usually means:\n• Username/password is incorrect\n• Account has insufficient permissions\n• Share permissions are too restrictive\n\nTry:\n• Double-check credentials\n• Use different user account\n• Contact server administrator"
                        -1073741788 -> // STATUS_OBJECT_NAME_NOT_FOUND (0xC0000034)
                            "Share not found: smb://$serverAddress/$folderPath/\n\nThis usually means:\n• Share name is misspelled\n• Share is not enabled on server\n• Server address is incorrect\n\nTry:\n• Check share name in server settings\n• Access share from another device first\n• Verify server IP/name"
                        -1073741782 -> // STATUS_OBJECT_PATH_NOT_FOUND (0xC000003A)
                            "Folder not found: smb://$serverAddress/$folderPath/\n\nThis usually means:\n• Folder path is incorrect\n• Folder doesn't exist on server\n• Path separators are wrong\n\nTry:\n• Check folder exists on server\n• Use forward slashes (/) not backslashes (\\)\n• Verify path from share root"
                        -1073741724 -> // STATUS_ACCOUNT_LOCKED_OUT (0xC000023C)
                            "Account locked: The user account is locked out\n\nThis usually means:\n• Too many failed login attempts\n• Account policy locked the account\n\nTry:\n• Wait for automatic unlock\n• Contact server administrator\n• Use different account"
                        -1073741715 -> // STATUS_LOGON_FAILURE (0xC000006D)
                            "Authentication failed: Invalid username or password\n\nThis usually means:\n• Username/password is wrong\n• Caps Lock might be on\n• Domain name missing (try DOMAIN\\username)\n\nTry:\n• Verify credentials carefully\n• Try domain\\username format\n• Reset password if expired"
                        -1073741714 -> // STATUS_ACCOUNT_RESTRICTION (0xC000006E)
                            "Account restrictions: Login not allowed\n\nThis usually means:\n• Account has login time restrictions\n• Account disabled for remote access\n• Account restricted to certain computers\n\nTry:\n• Check account login hours\n• Contact server administrator\n• Use different account"
                        -1073741711 -> // STATUS_PASSWORD_EXPIRED (0xC0000071)
                            "Password expired: Password needs to be changed\n\nThis usually means:\n• Password expired per policy\n• Account requires password change\n\nTry:\n• Change password on server\n• Contact server administrator"
                        -1073741485 -> // STATUS_TIME_DIFFERENCE_AT_DC (0xC0000133)
                            "Time synchronization error: Device time differs too much\n\nThis usually means:\n• Android device time is wrong\n• Server time is wrong\n• Timezone difference too large\n\nTry:\n• Sync Android time automatically\n• Check server time settings\n• Adjust timezone if needed"
                        else ->
                            when {
                                errorMessage.contains("Access is denied", ignoreCase = true) ->
                                    "Access denied to: smb://$serverAddress/$folderPath/\n\nCheck:\n• Username/password correct?\n• Folder permissions?\n• Share enabled?\n• Try different credentials"
                                errorMessage.contains("does not exist", ignoreCase = true) ->
                                    "Path not found: smb://$serverAddress/$folderPath/\n\nCheck:\n• Folder path correct?\n• Share name correct?\n• Server running?"
                                errorMessage.contains("timed out", ignoreCase = true) ->
                                    "Connection timeout\n\nCheck:\n• Server reachable?\n• Firewall blocking SMB?\n• Same network?\n• Try different network"
                                errorMessage.contains("Connection refused", ignoreCase = true) ->
                                    "Connection refused\n\nCheck:\n• SMB enabled on server?\n• Firewall settings?\n• Port 445 open?\n• Server accepting connections?"
                                errorMessage.contains("algorithm", ignoreCase = true) ||
                                    errorMessage.contains("MD4", ignoreCase = true) ||
                                    errorMessage.contains("provider", ignoreCase = true) -> {
                                    val diagnostic = buildFullDiagnostic(e, serverAddress, folderPath)
                                    "SECURITY ERROR (MD4/Algorithm issue):\n\n$diagnostic"
                                }
                                errorMessage.contains("network name cannot be found", ignoreCase = true) ||
                                    errorMessage.contains("network path was not found", ignoreCase = true) ->
                                    "Network path not found\n\nCheck:\n• Server name/IP correct?\n• Server powered on?\n• Same WiFi network?\n• Firewall blocking SMB?"
                                errorMessage.contains("bad network name", ignoreCase = true) ->
                                    "Bad network name\n\nCheck:\n• Server address format?\n• DNS resolution working?\n• Try IP address instead of name?"
                                errorMessage.contains("logon failure", ignoreCase = true) ->
                                    "Logon failure\n\nCheck:\n• Username format (DOMAIN\\user)?\n• Password correct?\n• Account active?\n• Try different credentials"
                                else -> {
                                    val diagnostic = buildFullDiagnostic(e, serverAddress, folderPath)
                                    "SMB Error: ${errorMessage}\n\nDetailed diagnostic:\n$diagnostic"
                                }
                            }
                    }
                Logger.e(
                    "SmbClient",
                    "SmbException details: ${e.message} (NT Status: 0x${ntStatus.toString(16).uppercase()})",
                    e,
                )
                e.printStackTrace()
                ImageFilesResult(emptyList(), msg)
            } catch (e: java.net.SocketTimeoutException) {
                val msg = "Connection timeout\n\nCheck:\n• Server running?\n• Network stable?\n• VPN not blocking?"
                Logger.e("SmbClient", msg, e)
                ImageFilesResult(emptyList(), msg)
            } catch (e: Exception) {
                // For any exception, check if it's security-related and provide diagnostic
                val msg =
                    if (e is java.security.NoSuchAlgorithmException ||
                        e.message?.contains("algorithm", ignoreCase = true) == true ||
                        e.message?.contains("provider", ignoreCase = true) == true ||
                        e.message?.contains("MD4", ignoreCase = true) == true
                    ) {
                        buildFullDiagnostic(e, serverAddress, folderPath)
                    } else {
                        "Error: ${e.javaClass.simpleName}\n${e.message ?: "Unknown error"}\n\nFull details logged"
                    }

                Logger.e("SmbClient", "Error in getImageFiles: ${e.message}", e)
                e.printStackTrace()
                ImageFilesResult(emptyList(), msg)
            }
        }
    }

    suspend fun downloadImage(imageUrl: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val smbFile = SmbFile(imageUrl, context)

                if (!smbFile.exists() || !smbFile.isFile()) {
                    return@withContext null
                }

                smbFile.inputStream.use { it.readBytes() }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    data class FileInfo(
        val name: String,
        val sizeKB: Long,
        val modifiedDate: Long,
    )

    suspend fun getFileInfo(imageUrl: String): FileInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val smbFile = SmbFile(imageUrl, context)

                if (!smbFile.exists() || !smbFile.isFile()) {
                    return@withContext null
                }

                FileInfo(
                    name = smbFile.name,
                    sizeKB = smbFile.length() / 1024,
                    modifiedDate = smbFile.lastModified(),
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    sealed class CopyResult {
        object Success : CopyResult()

        object AlreadyExists : CopyResult()

        object SameFolder : CopyResult()

        data class NetworkError(
            val message: String,
        ) : CopyResult()

        data class SecurityError(
            val message: String,
        ) : CopyResult()

        data class UnknownError(
            val message: String,
        ) : CopyResult()
    }

    sealed class MoveResult {
        object Success : MoveResult()

        object PendingUserConfirmation : MoveResult()

        object AlreadyExists : MoveResult()

        object SameFolder : MoveResult()

        data class NetworkError(
            val message: String,
        ) : MoveResult()

        data class SecurityError(
            val message: String,
        ) : MoveResult()

        data class DeleteError(
            val message: String,
        ) : MoveResult()

        data class UnknownError(
            val message: String,
        ) : MoveResult()
    }

    sealed class DeleteResult {
        object Success : DeleteResult()

        object FileNotFound : DeleteResult()

        data class SecurityError(
            val message: String,
        ) : DeleteResult()

        data class NetworkError(
            val message: String,
        ) : DeleteResult()

        data class UnknownError(
            val message: String,
        ) : DeleteResult()
    }

    sealed class RenameResult {
        object Success : RenameResult()

        object SourceNotFound : RenameResult()

        object TargetExists : RenameResult()

        data class SecurityError(
            val message: String,
        ) : RenameResult()

        data class NetworkError(
            val message: String,
        ) : RenameResult()

        data class UnknownError(
            val message: String,
        ) : RenameResult()
    }

    suspend fun writeFile(
        targetServer: String,
        targetFolder: String,
        fileName: String,
        data: ByteArray,
    ): CopyResult {
        return withContext(Dispatchers.IO) {
            try {
                val cleanTargetServer =
                    targetServer
                        .trim()
                        .removePrefix("smb://")
                        .removePrefix("\\\\")
                        .replace("\\", "/")

                val cleanTargetFolder =
                    targetFolder
                        .trim()
                        .removePrefix("/")
                        .removePrefix("\\")
                        .replace("\\", "/")

                val targetUrl = "smb://$cleanTargetServer/$cleanTargetFolder/"
                val targetDir = SmbFile(targetUrl, context)

                if (!targetDir.exists()) {
                    try {
                        targetDir.mkdirs()
                    } catch (e: jcifs.smb.SmbAuthException) {
                        return@withContext CopyResult.SecurityError("Access denied to target folder: ${e.message}")
                    } catch (e: java.net.UnknownHostException) {
                        return@withContext CopyResult.NetworkError("Server not found: ${e.message}")
                    } catch (e: Exception) {
                        return@withContext CopyResult.SecurityError("Cannot create target folder: ${e.message}")
                    }
                }

                val targetFile = SmbFile(targetDir, fileName)

                if (targetFile.exists()) {
                    return@withContext CopyResult.AlreadyExists
                }

                targetFile.openOutputStream().use { output ->
                    output.write(data)
                }

                CopyResult.Success
            } catch (e: jcifs.smb.SmbAuthException) {
                CopyResult.SecurityError("Authentication failed: ${e.message}")
            } catch (e: java.net.UnknownHostException) {
                CopyResult.NetworkError("Server not found: ${e.message}")
            } catch (e: java.net.SocketTimeoutException) {
                CopyResult.NetworkError("Connection timeout")
            } catch (e: java.io.IOException) {
                CopyResult.NetworkError("Network error: ${e.message}")
            } catch (e: jcifs.smb.SmbException) {
                CopyResult.SecurityError("SMB error: ${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
                CopyResult.UnknownError(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun copyFile(
        sourceUrl: String,
        targetServer: String,
        targetFolder: String,
    ): CopyResult {
        return withContext(Dispatchers.IO) {
            try {
                val sourceFile = SmbFile(sourceUrl, context)

                if (!sourceFile.exists() || !sourceFile.isFile()) {
                    return@withContext CopyResult.NetworkError("Source file not found")
                }

                // Build target URL
                val cleanTargetServer =
                    targetServer
                        .trim()
                        .removePrefix("smb://")
                        .removePrefix("\\\\")
                        .replace("\\", "/")

                val cleanTargetFolder =
                    targetFolder
                        .trim()
                        .removePrefix("/")
                        .removePrefix("\\")
                        .replace("\\", "/")

                val targetUrl = "smb://$cleanTargetServer/$cleanTargetFolder/"
                val targetDir = SmbFile(targetUrl, context)

                // Check if same folder
                if (sourceFile.parent == targetDir.path) {
                    return@withContext CopyResult.SameFolder
                }

                // Create target directory if not exists
                if (!targetDir.exists()) {
                    try {
                        targetDir.mkdirs()
                    } catch (e: jcifs.smb.SmbAuthException) {
                        return@withContext CopyResult.SecurityError("Access denied to target folder")
                    } catch (e: Exception) {
                        return@withContext CopyResult.SecurityError("Cannot create target folder: ${e.message}")
                    }
                }

                val targetFile = SmbFile(targetUrl + sourceFile.name, context)

                // Check if file already exists
                if (targetFile.exists()) {
                    return@withContext CopyResult.AlreadyExists
                }

                // Copy file
                try {
                    sourceFile.inputStream.use { input ->
                        targetFile.outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    CopyResult.Success
                } catch (e: jcifs.smb.SmbAuthException) {
                    CopyResult.SecurityError("Write permission denied")
                } catch (e: java.net.UnknownHostException) {
                    CopyResult.NetworkError("Target server not found: ${e.message}")
                } catch (e: java.net.SocketTimeoutException) {
                    CopyResult.NetworkError("Connection timeout")
                } catch (e: java.io.IOException) {
                    CopyResult.NetworkError("Network error: ${e.message}")
                } catch (e: Exception) {
                    CopyResult.UnknownError(e.message ?: "Unknown error occurred")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CopyResult.UnknownError(e.message ?: "Unexpected error")
            }
        }
    }

    suspend fun moveFile(
        sourceUrl: String,
        targetServer: String,
        targetFolder: String,
    ): MoveResult {
        return withContext(Dispatchers.IO) {
            try {
                val sourceFile = SmbFile(sourceUrl, context)

                if (!sourceFile.exists() || !sourceFile.isFile()) {
                    return@withContext MoveResult.NetworkError("Source file not found")
                }

                // Build target URL
                val cleanTargetServer =
                    targetServer
                        .trim()
                        .removePrefix("smb://")
                        .removePrefix("\\\\")
                        .replace("\\", "/")

                val cleanTargetFolder =
                    targetFolder
                        .trim()
                        .removePrefix("/")
                        .removePrefix("\\")
                        .replace("\\", "/")

                val targetUrl = "smb://$cleanTargetServer/$cleanTargetFolder/"
                val targetDir = SmbFile(targetUrl, context)

                // Check if same folder
                if (sourceFile.parent == targetDir.path) {
                    return@withContext MoveResult.SameFolder
                }

                // Create target directory if not exists
                if (!targetDir.exists()) {
                    try {
                        targetDir.mkdirs()
                    } catch (e: jcifs.smb.SmbAuthException) {
                        return@withContext MoveResult.SecurityError("Access denied to target folder")
                    } catch (e: Exception) {
                        return@withContext MoveResult.SecurityError("Cannot create target folder: ${e.message}")
                    }
                }

                val targetFile = SmbFile(targetUrl + sourceFile.name, context)

                // Check if file already exists
                if (targetFile.exists()) {
                    return@withContext MoveResult.AlreadyExists
                }

                // Copy file first
                try {
                    sourceFile.inputStream.use { input ->
                        targetFile.outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: jcifs.smb.SmbAuthException) {
                    return@withContext MoveResult.SecurityError("Write permission denied")
                } catch (e: java.net.UnknownHostException) {
                    return@withContext MoveResult.NetworkError("Target server not found: ${e.message}")
                } catch (e: java.net.SocketTimeoutException) {
                    return@withContext MoveResult.NetworkError("Connection timeout")
                } catch (e: java.io.IOException) {
                    return@withContext MoveResult.NetworkError("Network error: ${e.message}")
                } catch (e: Exception) {
                    return@withContext MoveResult.UnknownError(e.message ?: "Copy failed")
                }

                // Delete source file after successful copy
                try {
                    sourceFile.delete()
                    MoveResult.Success
                } catch (e: jcifs.smb.SmbAuthException) {
                    MoveResult.DeleteError("Delete permission denied on source file")
                } catch (e: Exception) {
                    MoveResult.DeleteError("Cannot delete source file: ${e.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                MoveResult.UnknownError(e.message ?: "Unexpected error")
            }
        }
    }

    suspend fun deleteFile(fileUrl: String): DeleteResult {
        return withContext(Dispatchers.IO) {
            try {
                val currentContext =
                    context ?: return@withContext DeleteResult.UnknownError("SMB context not initialized")

                val smbFile = SmbFile(fileUrl, currentContext)

                if (!smbFile.exists()) {
                    return@withContext DeleteResult.FileNotFound
                }

                smbFile.delete()
                DeleteResult.Success
            } catch (e: jcifs.smb.SmbAuthException) {
                DeleteResult.SecurityError("Delete permission denied: ${e.message}")
            } catch (e: java.net.UnknownHostException) {
                DeleteResult.NetworkError("Server not found: ${e.message}")
            } catch (e: java.net.SocketTimeoutException) {
                DeleteResult.NetworkError("Connection timeout")
            } catch (e: java.io.IOException) {
                DeleteResult.NetworkError("Network error: ${e.message}")
            } catch (e: jcifs.smb.SmbException) {
                DeleteResult.SecurityError("SMB error: ${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
                DeleteResult.UnknownError(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun renameFile(
        oldUrl: String,
        newUrl: String,
    ): RenameResult {
        return withContext(Dispatchers.IO) {
            try {
                val currentContext =
                    context ?: return@withContext RenameResult.UnknownError("SMB context not initialized")

                val oldFile = SmbFile(oldUrl, currentContext)
                val newFile = SmbFile(newUrl, currentContext)

                if (!oldFile.exists()) {
                    return@withContext RenameResult.SourceNotFound
                }

                if (newFile.exists()) {
                    return@withContext RenameResult.TargetExists
                }

                oldFile.renameTo(newFile)
                RenameResult.Success
            } catch (e: jcifs.smb.SmbAuthException) {
                RenameResult.SecurityError("Rename permission denied: ${e.message}")
            } catch (e: java.net.UnknownHostException) {
                RenameResult.NetworkError("Server not found: ${e.message}")
            } catch (e: java.net.SocketTimeoutException) {
                RenameResult.NetworkError("Connection timeout")
            } catch (e: java.io.IOException) {
                RenameResult.NetworkError("Network error: ${e.message}")
            } catch (e: jcifs.smb.SmbException) {
                RenameResult.SecurityError("SMB error: ${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
                RenameResult.UnknownError(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun fileExists(fileUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val currentContext = context ?: return@withContext false
                val smbFile = SmbFile(fileUrl, currentContext)
                smbFile.exists()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Checks if the folder has write permissions by attempting to create and delete a test file.
     * Returns true if folder allows write operations, false otherwise.
     */
    suspend fun checkWritePermission(
        serverAddress: String,
        folderPath: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (context == null) {
                    Logger.w("SmbClient", "Context is null during write permission check")
                    return@withContext false
                }

                val baseUrl = "smb://$serverAddress"
                val folderUrl =
                    if (folderPath.startsWith("/")) {
                        "$baseUrl$folderPath"
                    } else {
                        "$baseUrl/$folderPath"
                    }

                // Ensure folder URL ends with /
                val normalizedFolderUrl = if (!folderUrl.endsWith("/")) "$folderUrl/" else folderUrl

                // Create test file name with timestamp to avoid conflicts
                val testFileName = ".fms_write_test_${System.currentTimeMillis()}.tmp"
                val testFileUrl = "$normalizedFolderUrl$testFileName"

                Logger.d("SmbClient", "Testing write permission: $testFileUrl")

                val testFile = SmbFile(testFileUrl, context)

                // Test 1: Try to create the test file
                try {
                    testFile.createNewFile()
                    Logger.d("SmbClient", "Test file created successfully")
                } catch (e: Exception) {
                    Logger.w("SmbClient", "Failed to create test file: ${e.message}")
                    return@withContext false
                }

                // Test 2: Try to write some data to verify write access
                try {
                    testFile.outputStream.use { output ->
                        output.write("test".toByteArray())
                        output.flush()
                    }
                    Logger.d("SmbClient", "Test file written successfully")
                } catch (e: Exception) {
                    Logger.w("SmbClient", "Failed to write to test file: ${e.message}")
                    // Try to cleanup the created file
                    try {
                        testFile.delete()
                    } catch (deleteException: Exception) {
                        Logger.w("SmbClient", "Failed to cleanup test file after write failure")
                    }
                    return@withContext false
                }

                // Test 3: Try to delete the test file
                try {
                    testFile.delete()
                    Logger.d("SmbClient", "Test file deleted successfully - write permission confirmed")
                    return@withContext true
                } catch (e: Exception) {
                    Logger.w("SmbClient", "Failed to delete test file: ${e.message}")
                    // File exists but cannot be deleted - limited write permission
                    return@withContext false
                }
            } catch (e: Exception) {
                Logger.w("SmbClient", "Write permission check failed", e)
                return@withContext false
            }
        }
    }

    /**
     * Clears the SMB context and credentials from memory.
     * Should be called after completing operations to minimize the time
     * sensitive credentials remain in memory, improving security.
     * Also attempts to properly close the context to free resources.
     */
    fun disconnect() {
        context?.let {
            try {
                (it as? AutoCloseable)?.close()
            } catch (e: Exception) {
                Logger.w("SmbClient", "Failed to close context during disconnect", e)
            }
        }
        context = null
    }
}
