package com.sza.fastmediasorter.domain.usecase.companion

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.companion.CompanionConfigDto
import com.sza.fastmediasorter.data.companion.CompanionConfigException
import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** Outcome summary for the UI toast. */
data class CompanionImportResult(
    val resourceNames: List<String>,
    val host: String,
    val port: Int
)

/**
 * S0421: one-action import of a Windows-companion `.fmscfg` config.
 *
 * Maps the parsed [CompanionConfigDto] onto the existing SFTP stack:
 * - credential -> [SmbOperationsUseCase.saveSftpCredentials] (existing encrypted store);
 * - host key -> [MediaResource.hostKeyFingerprint] (S0046 TOFU pinning - no new pinning impl);
 * - one read-only SFTP resource per shared root, path built by [SftpPathUtils].
 *
 * Access path selection: the list is contract-ordered LAN first, then port-forward;
 * MVP persists the FIRST entry because [MediaResource] stores a single path.
 * Multi-path resources with automatic fallback are the documented follow-up
 * (strategic S0421 §5.3 - requires a resource-model extension).
 */
class ImportCompanionConfigUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parser: CompanionConfigParser,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** Reads and imports a `.fmscfg` document picked via SAF. */
    // The broad catch is an intentional import-boundary guard: any stream/IO/security/parse failure
    // must surface as Result.failure, not crash the picker. (S0988: surfaced by the diff-scoped gate.)
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(uri: Uri): Result<CompanionImportResult> = withContext(ioDispatcher) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return@withContext Result.failure(
                CompanionConfigException(CompanionConfigException.Reason.MALFORMED, "Cannot open $uri")
            )
            if (bytes.size > MAX_CONFIG_BYTES) {
                return@withContext Result.failure(
                    CompanionConfigException(
                        CompanionConfigException.Reason.MALFORMED,
                        "Config file too large: ${bytes.size} bytes"
                    )
                )
            }
            import(parser.parse(bytes))
        } catch (e: CompanionConfigException) {
            Timber.w(e, "Companion config rejected: ${e.reason}")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Companion config import failed")
            Result.failure(e)
        }
    }

    /**
     * S0988: imports a raw payload string decoded from a companion QR. Same parser/validation as the
     * file path - the QR carries the identical schemaVersion-1 JSON (plain or `FMSCFG1:` compressed).
     */
    suspend fun importFromPayload(payload: String): Result<CompanionImportResult> = withContext(ioDispatcher) {
        // parse() throws only CompanionConfigException (it wraps JSON/base64/gzip failures itself);
        // import() returns a Result and never throws - so a typed catch is all that is needed here.
        val dto = try {
            parser.parse(payload)
        } catch (e: CompanionConfigException) {
            Timber.w(e, "Companion QR rejected: ${e.reason}")
            return@withContext Result.failure(e)
        }
        import(dto)
    }

    /** Imports an already-parsed config (QR payload path reuses this directly). */
    suspend fun import(config: CompanionConfigDto): Result<CompanionImportResult> = withContext(ioDispatcher) {
        // Contract order is LAN first - the first entry is the preferred path.
        val accessPath = config.accessPaths.orEmpty().first()
        val host = requireNotNull(accessPath.host) { "validated by parser" }
        val port = requireNotNull(accessPath.port) { "validated by parser" }

        // S0984: a blank fingerprint is an intentional no-pin share (recipient trusts on first
        // connect, like manual entry) -> null. Only a non-blank, non-canonical value is malformed.
        val rawFingerprint = config.hostKeyFingerprintSha256.orEmpty()
        val canonicalFingerprint: String? = if (rawFingerprint.isBlank()) {
            null
        } else {
            SshFingerprintNormalizer.canonical(rawFingerprint)
                ?: return@withContext Result.failure(
                    CompanionConfigException(
                        CompanionConfigException.Reason.INVALID_CONTENT,
                        "Host-key fingerprint not in canonical SHA256 form"
                    )
                )
        }

        val credentialsResult = smbOperationsUseCase.saveSftpCredentials(
            host = host,
            port = port,
            username = config.username.orEmpty(),
            password = config.password.orEmpty()
        )
        val credentialsId = credentialsResult.getOrElse { e ->
            Timber.e(e, "Companion import: credential save failed")
            return@withContext Result.failure(e)
        }

        val resources = config.roots.orEmpty().map { root ->
            val virtualPath = root.virtualPath.orEmpty()
            val label = root.label?.ifBlank { null } ?: virtualPath.trimStart('/')
            MediaResource(
                id = 0,
                name = label,
                path = SftpPathUtils.buildSftpPath(host = host, path = virtualPath, port = port),
                type = ResourceType.SFTP,
                credentialsId = credentialsId,
                supportedMediaTypes = DEFAULT_MEDIA_TYPES,
                // Companion shares are whole folder trees; the server serves them read-only in MVP.
                scanSubdirectories = true,
                isReadOnly = true,
                comment = config.resourceName?.let { "Companion: $it" },
                hostKeyFingerprint = canonicalFingerprint
            )
        }

        addResourceUseCase.addMultiple(resources).fold(
            onSuccess = {
                Timber.i("Companion import: added ${resources.size} resource(s) for $host:$port")
                Result.success(
                    CompanionImportResult(
                        resourceNames = resources.map { it.name },
                        host = host,
                        port = port
                    )
                )
            },
            onFailure = { e ->
                Timber.e(e, "Companion import: resource add failed")
                Result.failure(e)
            }
        )
    }

    companion object {
        private const val MAX_CONFIG_BYTES = 64 * 1024
        private val DEFAULT_MEDIA_TYPES = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.GIF)
    }
}
