package com.sza.fastmediasorter.domain.usecase.companion

import android.content.Context
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.companion.CompanionAccessPathDto
import com.sza.fastmediasorter.data.companion.CompanionConfigDto
import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.data.companion.CompanionConfigSerializer
import com.sza.fastmediasorter.data.companion.CompanionResourceTokens
import com.sza.fastmediasorter.data.companion.CompanionRootDto
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.utils.SftpPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * S0984: builds a `.fmscfg` config file from a live SFTP [MediaResource] so its access can be
 * shared as a file (Telegram / email). The inverse of [ImportCompanionConfigUseCase]; emits the
 * frozen S0421 contract 1:1 (single resource, single LAN access path, single root).
 *
 * A resource with no stored password (key-auth-only) degrades to a passwordless config - the
 * recipient types the password at import; no private key material is ever shared.
 */
class ExportCompanionConfigUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val serializer: CompanionConfigSerializer,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    // Broad catch is an intentional export-boundary guard: any path/credential/IO failure surfaces as
    // Result.failure (logged), never a crash. Mirrors the import-side guard.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(resource: MediaResource, includePassword: Boolean): Result<File> =
        withContext(ioDispatcher) {
            try {
                val pathInfo = SftpPathUtils.parseSftpPath(resource.path)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Resource path is not a valid SFTP path: ${resource.path}")
                    )
                val credentialsId = resource.credentialsId
                    ?: return@withContext Result.failure(
                        IllegalStateException("SFTP resource has no stored credentials")
                    )
                val credentials = smbOperationsUseCase.getSftpCredentials(credentialsId).getOrElse { e ->
                    return@withContext Result.failure(e)
                }

                val dto = CompanionConfigDto(
                    schemaVersion = CompanionConfigParser.SUPPORTED_SCHEMA_VERSION,
                    resourceName = resource.name,
                    protocol = CompanionConfigParser.PROTOCOL_SFTP,
                    accessPaths = listOf(
                        CompanionAccessPathDto(
                            kind = CompanionAccessPathDto.KIND_LAN,
                            host = pathInfo.host,
                            port = pathInfo.port
                        )
                    ),
                    username = credentials.username,
                    password = if (includePassword) credentials.password else "",
                    hostKeyFingerprintSha256 = resource.hostKeyFingerprint.orEmpty(),
                    roots = listOf(buildRoot(resource, pathInfo.remotePath)),
                    createdAt = isoTimestamp()
                )

                val file = writeConfig(resource.name, serializer.serialize(dto))
                Result.success(file)
            } catch (e: Exception) {
                Timber.e(e, "Companion config export failed for '${resource.name}'")
                Result.failure(e)
            }
        }

    /**
     * S1002: emits the resource's real params into a v2 root. Fields at their v1-import default are
     * left null to keep the payload compact for the QR path; `mediaTypes` is emitted explicitly (unless
     * `allFiles`) so the receiver reconstructs the exact media-type set regardless of the profile.
     */
    private fun buildRoot(resource: MediaResource, remotePath: String): CompanionRootDto = CompanionRootDto(
        virtualPath = remotePath,
        label = resource.name,
        // S1016: emit readOnly:false for a writable share so an Android->Android round-trip keeps it
        // writable; a read-only resource omits the field (absent == read-only, compact payload).
        readOnly = if (resource.isReadOnly) null else false,
        profile = resource.profile
            .takeIf { it != ResourceProfile.NONE }
            ?.let { CompanionResourceTokens.profileToToken(it) },
        mediaTypes = if (resource.allFiles) {
            null
        } else {
            CompanionResourceTokens.mediaTypesToTokens(resource.supportedMediaTypes).ifEmpty { null }
        },
        scanSubdirectories = resource.scanSubdirectories,
        showSubfoldersAsItems = resource.showSubfoldersAsItems.takeIf { it },
        showHiddenFiles = resource.showHiddenFiles.takeIf { it },
        allFiles = resource.allFiles.takeIf { it },
        isDestination = resource.isDestination.takeIf { it },
        destinationColor = if (resource.isDestination) resource.destinationColor else null,
        comment = resource.comment,
        accessPin = resource.accessPin,
        slideshowInterval = resource.slideshowInterval
    )

    private fun writeConfig(resourceName: String, payload: String): File {
        val dir = File(context.cacheDir, SHARE_TEMP_DIR).apply { mkdirs() }
        // The payload may carry a plaintext password - drop any prior exports so credentials do not
        // linger in the cache beyond the share that produced them.
        dir.listFiles { f -> f.name.endsWith(CompanionConfigParser.FILE_EXTENSION) }?.forEach { it.delete() }
        val safeName = resourceName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "resource" }
        val file = File(dir, "$safeName${CompanionConfigParser.FILE_EXTENSION}")
        file.writeText(payload, Charsets.UTF_8)
        return file
    }

    private fun isoTimestamp(): String {
        val format = SimpleDateFormat(ISO_PATTERN, Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    private companion object {
        const val SHARE_TEMP_DIR = "share_temp"
        const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }
}
