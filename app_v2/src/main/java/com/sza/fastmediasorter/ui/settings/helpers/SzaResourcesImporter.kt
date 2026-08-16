package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceShareFormat
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0046 - imports resources from a share file the user picks. Extracted from `SettingsViewModel` so the
 * parsing/credentials/fingerprint logic lives outside the UI layer.
 *
 * S1666: the bundled `res/xml/sza_resources.xml` and the private key under `assets/sftp_keys/` are gone -
 * they carried real credentials into every APK, where unpacking the archive was the only step needed to
 * read them. A file that declares `auth="key"` is now skipped with a named reason: there is no bundled key
 * to pair it with, and inventing a password fallback would create an auth method the user never chose.
 * Credentials that arrive in a user-picked file are still stored encrypted; key bytes and passphrases are
 * never logged.
 */
@Singleton
class SzaResourcesImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val mediaCapabilities: MediaCapabilities,
) {

    sealed interface ImportResult {
        data class Success(val imported: Int, val updated: Int, val skipped: Int) : ImportResult
        data class Failure(val error: Throwable) : ImportResult
    }

    /** Outcome of a non-destructive preview parse of a user-supplied share file (S0422). */
    sealed interface PreviewResult {
        data class Valid(val toCreate: Int, val toUpdate: Int, val containsCredentials: Boolean) : PreviewResult
        data class Invalid(val reason: String) : PreviewResult
    }

    // S1666: the bundled entry point is gone. It read `R.xml.sza_resources`, a file that carried real
    // passwords, PINs and host fingerprints into every APK - the owner ruled on 2026-08-16 that the
    // resource and the code reading it go, and that the user's own file import is the way in. Only that
    // way in remains below.

    /** Imports a user-supplied share file (S0422). */
    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri).use { stream ->
                stream ?: return@withContext ImportResult.Failure(
                    IllegalStateException("Cannot open input stream for $uri")
                )
                val parser = Xml.newPullParser()
                parser.setInput(stream, null)
                importFromParser(parser)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error importing resources from file")
            ImportResult.Failure(e)
        }
    }

    /**
     * Parses a file without writing anything: reports how many resources would be created vs
     * overwritten (match by path, the same key the apply path uses) and whether any credential
     * travels in the file. Used to drive the import confirmation dialog (S0422).
     */
    suspend fun preview(uri: Uri): PreviewResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri).use { stream ->
                stream ?: return@withContext PreviewResult.Invalid("Cannot open file")
                val parser = Xml.newPullParser()
                parser.setInput(stream, null)
                val existingPaths = resourceRepository.getAllResourcesSync().map { it.path }.toSet()
                var toCreate = 0
                var toUpdate = 0
                var containsCredentials = false
                var rootValidated = false
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if (!rootValidated && parser.depth == 1) {
                            if (parser.name != ResourceShareFormat.ROOT_TAG) {
                                return@withContext PreviewResult.Invalid("Unexpected root <${parser.name}>")
                            }
                            rootValidated = true
                        }
                        if (parser.name == "resource") {
                            val path = parser.getAttributeValue(null, "path") ?: ""
                            if (path in existingPaths) toUpdate++ else toCreate++
                            val hasPassword = !parser.getAttributeValue(null, "password").isNullOrEmpty()
                            val hasKey = parser.getAttributeValue(null, "auth") == "key"
                            if (hasPassword || hasKey) containsCredentials = true
                        }
                    }
                    eventType = parser.next()
                }
                if (!rootValidated) PreviewResult.Invalid("Not a resources file")
                else PreviewResult.Valid(toCreate, toUpdate, containsCredentials)
            }
        } catch (e: Exception) {
            Timber.w(e, "Resource import preview failed")
            PreviewResult.Invalid(e.message ?: "Invalid file")
        }
    }

    /**
     * Shared parse-and-apply loop. Rejects a file whose root tag is not [ResourceShareFormat.ROOT_TAG]
     * before applying anything, so a foreign file never partially mutates existing data.
     */
    private suspend fun importFromParser(parser: XmlPullParser): ImportResult {
        val existingResources = resourceRepository.getAllResourcesSync()
        val existingCredentials = credentialsRepository.getAllCredentials().first()

        var imported = 0
        var updated = 0
        var skipped = 0
        var rootValidated = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (!rootValidated && parser.depth == 1) {
                    if (parser.name != ResourceShareFormat.ROOT_TAG) {
                        return ImportResult.Failure(
                            IllegalArgumentException("Unexpected root <${parser.name}>")
                        )
                    }
                    rootValidated = true
                }
                if (parser.name == "resource") {
                    when (importOne(parser, existingResources, existingCredentials)) {
                        Outcome.IMPORTED -> imported++
                        Outcome.UPDATED -> updated++
                        Outcome.SKIPPED -> skipped++
                    }
                }
            }
            eventType = parser.next()
        }
        return ImportResult.Success(imported = imported, updated = updated, skipped = skipped)
    }

    private enum class Outcome { IMPORTED, UPDATED, SKIPPED }

    /**
     * True when the current flavor supports this media family. Drops types a flavor cannot use
     * (e.g. AUDIO in the photos flavor) so an imported resource is usable, not dead (S0422).
     * Uses the injected capability layer, not `IS_<flavor>` guards.
     */
    private fun isMediaTypeSupportedByFlavor(type: MediaType): Boolean = when (type) {
        MediaType.AUDIO -> mediaCapabilities.supportsAudio
        MediaType.VIDEO -> mediaCapabilities.supportsVideo
        MediaType.IMAGE, MediaType.GIF -> mediaCapabilities.supportsImages
        MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT -> mediaCapabilities.supportsDocuments
        else -> true
    }

    private suspend fun importOne(
        parser: XmlPullParser,
        existingResources: List<MediaResource>,
        existingCredentials: List<NetworkCredentialsEntity>,
    ): Outcome {
        try {
            val name = parser.getAttributeValue(null, "name") ?: "Unknown"
            val path = parser.getAttributeValue(null, "path") ?: ""
            val typeStr = parser.getAttributeValue(null, "type") ?: "LOCAL"
            val username = parser.getAttributeValue(null, "username")
            val password = parser.getAttributeValue(null, "password")
            val pin = parser.getAttributeValue(null, "pin")
            val addToDestinations = parser.getAttributeValue(null, "addToDestinations")?.toBoolean() ?: false
            val allFiles = parser.getAttributeValue(null, "allFiles")?.toBoolean() ?: false
            val supportedTypesStr = parser.getAttributeValue(null, "supportedMediaTypes")
            val sortModeStr = parser.getAttributeValue(null, "sortMode")
            val displayModeStr = parser.getAttributeValue(null, "displayMode")
            val scanSubdirs = parser.getAttributeValue(null, "scanSubdirectories")?.toBoolean() ?: false
            val disableThumbnails = parser.getAttributeValue(null, "disableThumbnails")?.toBoolean() ?: false
            val showHidden = parser.getAttributeValue(null, "showHiddenFiles")?.toBoolean() ?: false
            val rememberFileList = parser.getAttributeValue(null, "rememberTheFileList")?.toBoolean() ?: false

            // S0046 SFTP key-auth + fingerprint attributes.
            val auth = parser.getAttributeValue(null, "auth") ?: "password"
            val hostKeyFingerprintRaw = parser.getAttributeValue(null, "hostKeyFingerprint")

            val type = try {
                ResourceType.valueOf(typeStr)
            } catch (e: Exception) {
                ResourceType.LOCAL
            }

            // S1666: no key ships with the app any more. A file that declares key-auth is skipped with
            // a named reason rather than silently imported without its key - the private key that used to
            // live in assets/sftp_keys was itself part of the leak this ticket closes.
            val bundledPrivateKey: String? = null
            if (type == ResourceType.SFTP && auth == "key") {
                Timber.w("Resource '$name' declares auth=key; bundled keys are no longer shipped, skipping")
                return Outcome.SKIPPED
            }

            val canonicalFingerprint = hostKeyFingerprintRaw?.let { raw ->
                SshFingerprintNormalizer.canonical(raw).also {
                    if (it == null) {
                        Timber.w("Predefined resource '$name' has an unparseable hostKeyFingerprint; pinning disabled")
                    }
                }
            }

            // Credentials - UPDATE existing or CREATE new.
            var credId: String? = null
            if (!username.isNullOrEmpty() && (type == ResourceType.SMB || type == ResourceType.SFTP || type == ResourceType.FTP)) {
                val server = when (type) {
                    ResourceType.SMB -> com.sza.fastmediasorter.utils.SmbPathUtils.extractServer(path) ?: ""
                    ResourceType.FTP -> com.sza.fastmediasorter.utils.FtpPathUtils.parseFtpPath(path)?.host ?: ""
                    ResourceType.SFTP -> com.sza.fastmediasorter.utils.SftpPathUtils.parseSftpPath(path)?.host ?: ""
                    else -> try { java.net.URI(path).host ?: "" } catch (e: Exception) { "" }
                }

                val smbShareName = if (type == ResourceType.SMB) {
                    com.sza.fastmediasorter.utils.SmbPathUtils.extractShare(path)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }

                val existingCred = existingCredentials.find {
                    it.server == server &&
                        it.username == username &&
                        it.type == typeStr &&
                        (
                            type != ResourceType.SMB ||
                                smbShareName == null ||
                                it.shareName == smbShareName ||
                                it.shareName.isNullOrBlank()
                            )
                }

                if (existingCred != null) {
                    credId = existingCred.credentialId
                    val withKey = if (bundledPrivateKey != null) {
                        existingCred.copy(sshPrivateKey = CryptoHelper.encrypt(bundledPrivateKey))
                    } else {
                        existingCred
                    }
                    if (!password.isNullOrEmpty()) {
                        credentialsRepository.update(
                            withKey.copy(
                                encryptedPassword = CryptoHelper.encrypt(password) ?: "",
                                shareName = smbShareName ?: existingCred.shareName,
                            )
                        )
                    } else if (bundledPrivateKey != null) {
                        credentialsRepository.update(withKey)
                    } else if (type == ResourceType.SMB && existingCred.shareName.isNullOrBlank() && !smbShareName.isNullOrBlank()) {
                        credentialsRepository.update(existingCred.copy(shareName = smbShareName))
                    }
                } else {
                    val newCredId = UUID.randomUUID().toString()
                    val port = when (type) {
                        ResourceType.SFTP -> 22
                        ResourceType.FTP -> 21
                        else -> 445
                    }
                    credentialsRepository.insert(
                        NetworkCredentialsEntity.create(
                            credentialId = newCredId,
                            type = typeStr,
                            server = server,
                            port = port,
                            username = username,
                            plaintextPassword = password ?: "",
                            shareName = smbShareName,
                            sshPrivateKey = bundledPrivateKey,
                        )
                    )
                    credId = newCredId
                }
            }

            val mediaTypes = (
                if (supportedTypesStr != null) {
                    supportedTypesStr.split(",").mapNotNull {
                        try { MediaType.valueOf(it.trim()) } catch (e: Exception) { null }
                    }.toSet()
                } else {
                    setOf(MediaType.IMAGE, MediaType.VIDEO)
                }
            ).filter { isMediaTypeSupportedByFlavor(it) }.toSet()

            val sortMode = try { SortMode.valueOf(sortModeStr ?: "NAME_ASC") } catch (e: Exception) { SortMode.NAME_ASC }
            val displayMode = try { DisplayMode.valueOf(displayModeStr ?: "LIST") } catch (e: Exception) { DisplayMode.LIST }

            val existingResource = existingResources.find { it.path == path }
            if (existingResource != null) {
                resourceRepository.updateResource(
                    existingResource.copy(
                        name = name,
                        credentialsId = credId ?: existingResource.credentialsId,
                        accessPin = pin ?: existingResource.accessPin,
                        hostKeyFingerprint = canonicalFingerprint ?: existingResource.hostKeyFingerprint,
                    )
                )
                return Outcome.UPDATED
            }

            var isDest = false
            var destOrder: Int? = null
            var destColor = 0
            if (addToDestinations) {
                val nextOrder = getDestinationsUseCase.getNextAvailableOrder()
                if (nextOrder != -1) {
                    isDest = true
                    destOrder = nextOrder
                    destColor = DestinationColors.getColorForDestination(nextOrder)
                }
            }

            resourceRepository.addResource(
                MediaResource(
                    name = name,
                    path = path,
                    type = type,
                    credentialsId = credId,
                    supportedMediaTypes = mediaTypes,
                    sortMode = sortMode,
                    displayMode = displayMode,
                    scanSubdirectories = scanSubdirs,
                    disableThumbnails = disableThumbnails,
                    allFiles = allFiles,
                    showHiddenFiles = showHidden,
                    rememberFileList = rememberFileList,
                    accessPin = pin,
                    isDestination = isDest,
                    destinationOrder = destOrder,
                    destinationColor = destColor,
                    hostKeyFingerprint = canonicalFingerprint,
                    isWritable = true,
                    isAvailable = true,
                )
            )
            return Outcome.IMPORTED
        } catch (e: Exception) {
            Timber.e(e, "Error parsing predefined resource entry")
            return Outcome.SKIPPED
        }
    }
}
