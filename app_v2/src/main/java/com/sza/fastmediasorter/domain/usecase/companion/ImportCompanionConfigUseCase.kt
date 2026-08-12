package com.sza.fastmediasorter.domain.usecase.companion

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
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

/**
 * Outcome summary for the UI toast.
 *
 * [resourceNames] lists every resource named in the config (used by the external attachment dialog,
 * unchanged by S1012). [addedNames]/[updatedNames] carry the in-app add-or-update split so the
 * coordinator can render counts and name the single-affected case; both are empty on the insert-only
 * path (external attachment) where every resource is counted as added via [resourceNames].
 */
data class CompanionImportResult(
    val resourceNames: List<String>,
    val host: String,
    val port: Int,
    val addedNames: List<String> = emptyList(),
    val updatedNames: List<String> = emptyList()
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
            // S1012: in-app file import add-or-updates matching resources (no duplicates on re-import).
            import(parser.parse(bytes), matchExistingByPath = true)
        } catch (e: CompanionConfigException) {
            Timber.w(e, "Companion config rejected: ${e.reason}")
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
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
        // S1012: in-app QR import add-or-updates matching resources (no duplicates on re-import).
        import(dto, matchExistingByPath = true)
    }

    /**
     * Imports an already-parsed config (QR payload path reuses this directly).
     *
     * S1012: [matchExistingByPath] enables the companion add-or-update path (in-app QR/file entries).
     * The external attachment activity calls this with the default (false), keeping its insert-only
     * behaviour and result dialog unchanged.
     */
    suspend fun import(
        config: CompanionConfigDto,
        matchExistingByPath: Boolean = false
    ): Result<CompanionImportResult> = withContext(ioDispatcher) {
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

        val primaryEndpoint = HostPort(host, port)
        val resources = config.roots.orEmpty().map { root ->
            buildResource(
                root = root,
                primary = primaryEndpoint,
                credentialsId = credentialsId,
                canonicalFingerprint = canonicalFingerprint,
                configName = config.resourceName,
                altEndpoints = altEndpoints,
                configAccessNote = config.accessNote
            )
        }

        addResourceUseCase.addMultiple(resources, matchExistingByPath = matchExistingByPath).fold(
            onSuccess = { addResult ->
                Timber.i(
                    "Companion import for $host:$port: added ${addResult.addedCount}, " +
                        "updated ${addResult.updatedCount}"
                )
                Result.success(
                    CompanionImportResult(
                        resourceNames = resources.map { it.name },
                        host = host,
                        port = port,
                        addedNames = addResult.addedNames,
                        updatedNames = addResult.updatedNames
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
     * S1016: read-only is the [CompanionRootDto.resolveReadOnly] policy - a root is writable when it
     * carries `readOnly:false` or is a destination; absent `readOnly` stays read-only (back-compat).
     * Destination color for actual destination slots is reassigned by [AddResourceUseCase.addMultiple].
     */
    private fun buildResource(
        root: CompanionRootDto,
        primary: HostPort,
        credentialsId: String,
        canonicalFingerprint: String?,
        configName: String?,
        altEndpoints: List<HostPort>,
        configAccessNote: String?
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
            path = SftpPathUtils.buildSftpPath(host = primary.host, path = virtualPath, port = primary.port),
            type = ResourceType.SFTP,
            credentialsId = credentialsId,
            supportedMediaTypes = mediaTypes,
            allFiles = root.allFiles ?: preset?.allFiles ?: false,
            // Companion shares are whole folder trees; v1 default scans subdirectories.
            scanSubdirectories = root.scanSubdirectories ?: true,
            showSubfoldersAsItems = root.showSubfoldersAsItems ?: false,
            showHiddenFiles = root.showHiddenFiles ?: false,
            // S1016: read-only policy per the frozen contract rule (readOnly==false OR isDestination
            // == writable); absent readOnly stays read-only. Physical write capability is probed
            // separately into MediaResource.isWritable, so only the policy flag is set here.
            isReadOnly = root.resolveReadOnly(),
            isDestination = isDestination,
            comment = root.comment?.ifBlank { null } ?: configName?.let { "Companion: $it" },
            accessPin = root.accessPin?.ifBlank { null },
            profile = profile ?: ResourceProfile.NONE,
            rememberFileList = preset?.rememberFileList ?: false,
            hostKeyFingerprint = canonicalFingerprint,
            altAccessPaths = altEndpoints, // S1006: reachable-endpoint fallback candidates
            accessNote = configAccessNote // S1014: companion connectivity guidance shown on connect failure
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
