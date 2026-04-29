package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.utils.SmbPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class LocalToSmbStrategy @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val smbClient: SmbClient,
    // Credentials repository is required to resolve username/password before upload.
    // Without it, smbClient receives empty credentials and auth fails (STATUS_LOGON_FAILURE).
    private val credentialsRepository: NetworkCredentialsRepository
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        val isSourceLocal = sourceScheme == "file" || sourceScheme == "content" || sourceScheme == null
        val isDestSmb = destScheme == "smb"
        return isSourceLocal && isDestSmb
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean = withContext(Dispatchers.IO) {
        // Always dispatch to IO — SmbConnectionManager does not switch dispatchers internally,
        // so calling from Main causes NetworkOnMainThreadException (seen in CAP_ upload flow).
        val sourcePath = source.toString()
        val destPath = destination.toString()
        Timber.d("LocalToSmbStrategy: Copying $sourcePath to $destPath")

        val smbPathInfo = SmbPathUtils.parseSmbPath(destPath)
            ?: return@withContext false

        // Resolve credentials from repository — same logic as SmbOperationStrategy.
        // parseSmbPath returns empty username/password; we must fill them here or auth fails.
        val smbConnectionInfo = resolveConnectionInfo(smbPathInfo)

        // Check existence if not overwriting
        if (!overwrite) {
            val existsResult = smbClient.exists(smbConnectionInfo, smbPathInfo.remotePath)
            if (existsResult is SmbResult.Success && existsResult.data) {
                Timber.w("Destination file already exists: $destPath")
                return@withContext false
            }
        }

        // Prepare source stream
        val inputStreamInfo = try {
            if (source.scheme == "content") {
                val stream = context.contentResolver.openInputStream(source) 
                    ?: throw Exception("Failed to open content URI")
                val size = context.contentResolver.openAssetFileDescriptor(source, "r")?.use { it.length } 
                    ?: stream.available().toLong()
                Pair(stream, size)
            } else {
                val file = File(source.path ?: sourcePath)
                if (!file.exists()) throw Exception("Local file not found")
                Pair(file.inputStream(), file.length())
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open source")
            return@withContext false
        }

        // Upload to SMB
        return@withContext inputStreamInfo.first.use { stream ->
            val size = inputStreamInfo.second
            when (val result = smbClient.uploadFile(
                smbConnectionInfo,
                smbPathInfo.remotePath,
                stream,
                size,
                progressCallback
            )) {
                is SmbResult.Success -> true
                is SmbResult.Error -> {
                    Timber.e("SMB upload failed: ${result.message}")
                    false
                }
            }
        }
    }

    /**
     * Resolves credentials for an SMB path from the credentials repository,
     * then returns a copy of the connection info with username/password filled in.
     * Falls back to host-level credentials (stored with shareName='') before giving up.
     * Mirrors the logic in SmbOperationStrategy.resolveSmbCredentials.
     */
    private suspend fun resolveConnectionInfo(
        pathInfo: SmbPathUtils.SmbPathInfo
    ): com.sza.fastmediasorter.data.network.model.SmbConnectionInfo {
        val server = pathInfo.connectionInfo.server
        val share = pathInfo.connectionInfo.shareName
        val normalizedShare = share.trim().trim('/', '\\')
        val firstSegment = normalizedShare.substringBefore('/', normalizedShare)

        // Try share-specific candidates first
        val candidates = linkedSetOf(share, normalizedShare, firstSegment).filter { it.isNotEmpty() }
        for (candidate in candidates) {
            val creds = credentialsRepository.getByServerAndShare(server, candidate)
            if (creds != null) {
                Timber.d("LocalToSmbStrategy: Resolved share credentials for $server/$candidate (user=${creds.username})")
                return pathInfo.connectionInfo.copy(
                    username = creds.username,
                    password = creds.password,
                    domain = creds.domain
                )
            }
        }

        // Fallback: host-level credentials (shareName='' — user saved without specifying share).
        // SmbOperationStrategy uses the same fallback; without it auth fails for such configs.
        val hostCreds = credentialsRepository.getCredentialsByHost(server)
        if (hostCreds != null && hostCreds.type.equals("SMB", ignoreCase = true)) {
            Timber.d("LocalToSmbStrategy: Using host-level SMB credentials for $server (user=${hostCreds.username})")
            return pathInfo.connectionInfo.copy(
                username = hostCreds.username,
                password = hostCreds.password,
                domain = hostCreds.domain
            )
        }

        Timber.w("LocalToSmbStrategy: No credentials found for $server/$share — uploading as guest")
        return pathInfo.connectionInfo
    }
}
