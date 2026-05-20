package com.sza.fastmediasorter.data.network

import com.hierynomus.smbj.auth.AuthenticationContext
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.domain.model.MediaExtensions
import timber.log.Timber

/**
 * SMB share discovery for SmbClient.
 *
 * SMBJ does not expose RAP/SRVSVC, so we cannot enumerate shares directly. As a workaround we
 * try IPC$ for permission probing, then trial-connect a curated list of common share names
 * (Windows defaults, NAS conventions, media-server names, common custom names). Successful
 * non-administrative connects are returned, deduplicated case-insensitively.
 *
 * Extracted to keep SmbClient below the 1000-line cap.
 */
class SmbShareDiscoveryHelper(private val connectionManager: SmbConnectionManager) {

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
            // LinkedHashSet preserves insertion order; we additionally dedup case-insensitively below
            val shares = mutableSetOf<String>()

            try {
                // Attempt 1: Probe IPC$ - successful connect indicates user has admin rights
                try {
                    val ipcShare = session.connectShare("IPC$")
                    ipcShare.close()
                    Timber.d("IPC$ connection successful - user may have admin rights")
                    // SMBJ does not expose RAP or SRVSVC enumeration directly - no actual list to read.
                    Timber.d("Share enumeration via IPC$ not directly supported by SMBJ")
                } catch (e: Exception) {
                    Timber.d("IPC$ access denied or not available: ${e.message}")
                }

                // Attempt 2: trial-connect common share names (main fallback)
                Timber.d("Scanning for shares using trial connection method (${COMMON_SHARE_NAMES.size} attempts)..")

                for (shareName in COMMON_SHARE_NAMES) {
                    try {
                        val share = session.connectShare(shareName)

                        val isAdminShare = shareName.endsWith("$") ||
                            shareName.equals("IPC$", ignoreCase = true) ||
                            shareName.equals("ADMIN$", ignoreCase = true) ||
                            shareName.matches(Regex("[A-Za-z]\\$")) // C$, D$, ..

                        if (!isAdminShare) {
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
                    } catch (_: Exception) {
                        // Share doesn't exist, not accessible, or hidden - skip silently (expected)
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

            val sharesList = shares.toList().sorted()
            if (sharesList.size < 3) {
                // Informational only - thin results are normal on NAS/custom configurations;
                // the UI now offers manual entry as fallback (S0064).
                Timber.i("Only ${sharesList.size} share(s) found. There may be more shares with custom names.")
            }
            SmbResult.Success(sharesList)
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to SMB server for share enumeration")
            SmbResult.Error("Connection failed: ${e.message}. Please verify server address and credentials.", e)
        }
    }

    /**
     * Run a single connection-test attempt without retry logic. When [connectionInfo].shareName
     * is empty, the test enumerates shares; otherwise it verifies share access and reports
     * folder/media-file/total-item counts at the optional sub-path.
     */
    suspend fun performTestConnection(connectionInfo: SmbConnectionInfo, path: String = ""): SmbResult<String> {
        return if (connectionInfo.shareName.isEmpty()) {
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
            connectionManager.withConnection(connectionInfo) { share ->
                val targetPath = path.trim('/', '\\')
                val fullPathDisplay = if (targetPath.isEmpty()) {
                    "${connectionInfo.server}\\${connectionInfo.shareName}"
                } else {
                    "${connectionInfo.server}\\${connectionInfo.shareName}\\$targetPath"
                }

                var pathWarning = ""
                if (targetPath.isNotEmpty()) {
                    try {
                        // SMBJ's fileExists() only checks files, not folders - fall back to folderExists
                        val pathExists = share.folderExists(targetPath) || share.fileExists(targetPath)
                        if (!pathExists) {
                            // Hard-fail when a specific subfolder is requested but missing - prevents the
                            // user creating a resource pointing to a non-existent folder (typo guard).
                            return@withConnection SmbResult.Error(
                                "Subfolder '$targetPath' does not exist on share '${connectionInfo.shareName}'"
                            )
                        }
                    } catch (e: Exception) {
                        pathWarning = "\n⚠ Warning: Could not verify path '$targetPath' (${e.message})"
                    }
                }

                val scanPath = if (pathWarning.isEmpty()) targetPath else ""
                val files = share.list(scanPath).filter { !it.fileName.startsWith(".") }
                val folders = files.count { (it.fileAttributes and 0x10L) != 0L }
                val mediaFiles = files.filter { file ->
                    val ext = file.fileName.substringAfterLast('.', "").lowercase()
                    MediaExtensions.isImage(ext) ||
                        MediaExtensions.isVideo(ext) ||
                        MediaExtensions.isAudio(ext)
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
    }

    companion object {
        private val COMMON_SHARE_NAMES = listOf(
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
            // Lowercase variations
            "shared", "public", "users", "documents",
            "photos", "videos", "music", "data"
        )
    }
}
