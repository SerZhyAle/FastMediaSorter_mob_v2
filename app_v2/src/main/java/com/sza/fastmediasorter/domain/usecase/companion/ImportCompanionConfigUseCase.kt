package com.sza.fastmediasorter.domain.usecase.companion

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.companion.CompanionConfigDto
import com.sza.fastmediasorter.data.companion.CompanionConfigException
import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.data.companion.CompanionResourceTokens
import com.sza.fastmediasorter.data.companion.CompanionRootDto
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.mediaPreset
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
        val allAccessPaths = config.accessPaths.orEmpty()
        val accessPath = allAccessPaths.first()
        val host = requireNotNull(accessPath.host) { "validated by parser" }
        val port = requireNotNull(accessPath.port) { "validated by parser" }
        // S1006: every access path after the primary becomes a reachable-endpoint fallback candidate,
        // so one imported resource works on the home LAN and remotely (SftpEndpointResolver picks the live one).
        val altEndpoints = allAccessPaths.drop(1)
            .mapNotNull { ap ->
                val altHost = ap.host
                val altPort = ap.port
                if (altHost.isNullOrBlank() || altPort == null) null else HostPort(altHost, altPort)
            }
            .filterNot { it.host == host && it.port == port }
            .distinct()

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

        // S1006: credentials are looked up by host:port at connect time, so each fallback candidate
        // needs its own row (same username/password - the companion uses one credential for all paths).
        altEndpoints.forEach { endpoint ->
            smbOperationsUseCase.saveSftpCredentials(
                host = endpoint.host,
                port = endpoint.port,
                username = config.username.orEmpty(),
                password = config.password.orEmpty()
            ).onFailure { e ->
                Timber.w(e, "Companion import: alt credential save failed for ${endpoint.host}:${endpoint.port}")
            }
        }

        val resources = config.roots.orEmpty().map { root ->
            buildResource(root, host, port, credentialsId, canonicalFingerprint, config.resourceName, altEndpoints)
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

    /**
     * S1002: maps one shared root onto a [MediaResource], applying the v2 resource params when present
     * and falling back to the frozen v1 defaults (ALL media types, scan subdirectories, read-only,
     * "Companion: <name>" comment) for every field a v1 config omits.
     *
     * Media-type precedence: explicit [CompanionRootDto.mediaTypes] > profile preset > v1 default.
     * A destination must be writable, so `isDestination` clears the read-only default.
     * Destination color for actual destination slots is reassigned by [AddResourceUseCase.addMultiple].
     */
    private fun buildResource(
        root: CompanionRootDto,
        host: String,
        port: Int,
        credentialsId: String,
        canonicalFingerprint: String?,
        configName: String?,
        altEndpoints: List<HostPort>
    ): MediaResource {
        val virtualPath = root.virtualPath.orEmpty()
        val label = root.label?.ifBlank { null } ?: virtualPath.trimStart('/')

        val profile = CompanionResourceTokens.profileFromToken(root.profile)
        val preset = profile?.mediaPreset()
        val explicitTypes = root.mediaTypes
            ?.mapNotNull { CompanionResourceTokens.mediaTypeFromToken(it) }
            ?.toSet()
            ?.ifEmpty { null }
        val mediaTypes = explicitTypes ?: preset?.supportedMediaTypes ?: DEFAULT_MEDIA_TYPES
        val isDestination = root.isDestination ?: false

        val base = MediaResource(
            id = 0,
            name = label,
            path = SftpPathUtils.buildSftpPath(host = host, path = virtualPath, port = port),
            type = ResourceType.SFTP,
            credentialsId = credentialsId,
            supportedMediaTypes = mediaTypes,
            allFiles = root.allFiles ?: preset?.allFiles ?: false,
            // Companion shares are whole folder trees; v1 default scans subdirectories.
            scanSubdirectories = root.scanSubdirectories ?: true,
            showSubfoldersAsItems = root.showSubfoldersAsItems ?: false,
            showHiddenFiles = root.showHiddenFiles ?: false,
            // A destination must accept writes; otherwise keep the v1 read-only default.
            isReadOnly = !isDestination,
            isDestination = isDestination,
            comment = root.comment?.ifBlank { null } ?: configName?.let { "Companion: $it" },
            accessPin = root.accessPin?.ifBlank { null },
            profile = profile ?: ResourceProfile.NONE,
            rememberFileList = preset?.rememberFileList ?: false,
            hostKeyFingerprint = canonicalFingerprint,
            altAccessPaths = altEndpoints // S1006: reachable-endpoint fallback candidates
        )
        // Only override the model defaults (interval 10, green color) when the config actually carries them.
        val withInterval = root.slideshowInterval?.takeIf { it > 0 }
            ?.let { base.copy(slideshowInterval = it) } ?: base
        return root.destinationColor?.let { withInterval.copy(destinationColor = it) } ?: withInterval
    }

    companion object {
        private const val MAX_CONFIG_BYTES = 64 * 1024
        private val DEFAULT_MEDIA_TYPES = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.GIF)
    }
}
