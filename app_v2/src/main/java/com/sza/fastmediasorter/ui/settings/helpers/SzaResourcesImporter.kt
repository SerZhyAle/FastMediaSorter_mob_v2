package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
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
import com.sza.fastmediasorter.utils.FtpPathUtils
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SmbPathUtils
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
            e.rethrowIfCancellation()
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
            e.rethrowIfCancellation()
            Timber.w(e, "Resource import preview failed")
            PreviewResult.Invalid(e.message ?: "Invalid file")
        }
    }

    /**
     * Shared parse-and-apply loop. Rejects a file whose root tag is not [ResourceShareFormat.ROOT_TAG]
     * before applying anything, so a foreign file never partially mutates existing data.
     */
    private suspend fun importFromParser(parser: XmlPullParser): ImportResult {
        // Mutable so each row written by this import is visible to the entries after it: a file that names
        // one server twice, or one path twice, must reuse the row instead of creating a duplicate.
        val existingResources = resourceRepository.getAllResourcesSync().toMutableList()
        val existingCredentials = credentialsRepository.getAllCredentials().first().toMutableList()

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
        existingResources: MutableList<MediaResource>,
        existingCredentials: MutableList<NetworkCredentialsEntity>,
    ): Outcome = try {
        importEntry(parser, existingResources, existingCredentials)
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        Timber.e(e, "Error parsing predefined resource entry")
        Outcome.SKIPPED
    }

    private suspend fun importEntry(
        parser: XmlPullParser,
        existingResources: MutableList<MediaResource>,
        existingCredentials: MutableList<NetworkCredentialsEntity>,
    ): Outcome {
        val name = parser.getAttributeValue(null, "name") ?: "Unknown"
        val path = parser.getAttributeValue(null, "path") ?: ""
        val typeStr = parser.getAttributeValue(null, "type") ?: "LOCAL"
        val type = enumOrDefault(typeStr, ResourceType.LOCAL)

        // S1666: no key ships with the app any more. A file that declares key-auth is skipped with
        // a named reason rather than silently imported without its key - the private key that used to
        // live in assets/sftp_keys was itself part of the leak this ticket closes.
        val auth = parser.getAttributeValue(null, "auth") ?: "password"
        if (type == ResourceType.SFTP && auth == "key") {
            Timber.w("Resource '$name' declares auth=key; bundled keys are no longer shipped, skipping")
            return Outcome.SKIPPED
        }

        val canonicalFingerprint = parser.getAttributeValue(null, "hostKeyFingerprint")?.let { raw ->
            SshFingerprintNormalizer.canonical(raw).also {
                if (it == null) {
                    Timber.w("Predefined resource '$name' has an unparseable hostKeyFingerprint; pinning disabled")
                }
            }
        }

        val credId = resolveCredentialId(
            type = type,
            typeStr = typeStr,
            path = path,
            username = parser.getAttributeValue(null, "username"),
            password = parser.getAttributeValue(null, "password"),
            existingCredentials = existingCredentials,
        )

        val candidate = MediaResource(
            name = name,
            path = path,
            type = type,
            credentialsId = credId,
            supportedMediaTypes = parseMediaTypes(parser.getAttributeValue(null, "supportedMediaTypes")),
            sortMode = enumOrDefault(parser.getAttributeValue(null, "sortMode"), SortMode.NAME_ASC),
            displayMode = enumOrDefault(parser.getAttributeValue(null, "displayMode"), DisplayMode.LIST),
            scanSubdirectories = parser.booleanAttribute("scanSubdirectories"),
            disableThumbnails = parser.booleanAttribute("disableThumbnails"),
            allFiles = parser.booleanAttribute("allFiles"),
            showHiddenFiles = parser.booleanAttribute("showHiddenFiles"),
            rememberFileList = parser.booleanAttribute("rememberTheFileList"),
            accessPin = parser.getAttributeValue(null, "pin"),
            destinationColor = 0,
            hostKeyFingerprint = canonicalFingerprint,
            isWritable = true,
            isAvailable = true,
        )
        return saveResource(candidate, parser.booleanAttribute("addToDestinations"), existingResources)
    }

    private fun XmlPullParser.booleanAttribute(attribute: String): Boolean =
        getAttributeValue(null, attribute)?.toBoolean() ?: false

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } } ?: default

    private fun parseMediaTypes(value: String?): Set<MediaType> {
        val declared = value?.split(",")
            ?.mapNotNull { raw -> MediaType.entries.firstOrNull { it.name == raw.trim() } }
            ?: listOf(MediaType.IMAGE, MediaType.VIDEO)
        return declared.filter { isMediaTypeSupportedByFlavor(it) }.toSet()
    }

    /** Credentials - UPDATE the matching row or CREATE a new one; null when the entry carries no login. */
    @Suppress("LongParameterList")
    private suspend fun resolveCredentialId(
        type: ResourceType,
        typeStr: String,
        path: String,
        username: String?,
        password: String?,
        existingCredentials: MutableList<NetworkCredentialsEntity>,
    ): String? {
        if (username.isNullOrEmpty() || type !in CREDENTIAL_TYPES) return null
        return upsertCredential(type, typeStr, serverOf(type, path), path, username, password, existingCredentials)
    }

    @Suppress("LongParameterList")
    private suspend fun upsertCredential(
        type: ResourceType,
        typeStr: String,
        server: String,
        path: String,
        username: String,
        password: String?,
        existingCredentials: MutableList<NetworkCredentialsEntity>,
    ): String {
        val smbShareName = if (type == ResourceType.SMB) {
            SmbPathUtils.extractShare(path)?.takeIf { it.isNotBlank() }
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
        if (existingCred == null) {
            return insertCredential(type, typeStr, server, username, password, smbShareName, existingCredentials)
        }
        reviseCredential(existingCred, type, password, smbShareName)?.let { revised ->
            credentialsRepository.update(revised)
            existingCredentials[existingCredentials.indexOf(existingCred)] = revised
        }
        return existingCred.credentialId
    }

    private fun serverOf(type: ResourceType, path: String): String = when (type) {
        ResourceType.SMB -> SmbPathUtils.extractServer(path)
        ResourceType.FTP -> FtpPathUtils.parseFtpPath(path)?.host
        ResourceType.SFTP -> SftpPathUtils.parseSftpPath(path)?.host
        else -> null
    } ?: ""

    private fun reviseCredential(
        existing: NetworkCredentialsEntity,
        type: ResourceType,
        password: String?,
        smbShareName: String?,
    ): NetworkCredentialsEntity? = when {
        !password.isNullOrEmpty() -> existing.copy(
            encryptedPassword = CryptoHelper.encrypt(password) ?: "",
            shareName = smbShareName ?: existing.shareName,
        )
        type == ResourceType.SMB && existing.shareName.isNullOrBlank() && !smbShareName.isNullOrBlank() ->
            existing.copy(shareName = smbShareName)
        else -> null
    }

    @Suppress("LongParameterList")
    private suspend fun insertCredential(
        type: ResourceType,
        typeStr: String,
        server: String,
        username: String,
        password: String?,
        smbShareName: String?,
        existingCredentials: MutableList<NetworkCredentialsEntity>,
    ): String {
        val created = NetworkCredentialsEntity.create(
            credentialId = UUID.randomUUID().toString(),
            type = typeStr,
            server = server,
            port = when (type) {
                ResourceType.SFTP -> SFTP_PORT
                ResourceType.FTP -> FTP_PORT
                else -> SMB_PORT
            },
            username = username,
            plaintextPassword = password ?: "",
            shareName = smbShareName,
        )
        val rowId = credentialsRepository.insert(created)
        existingCredentials.add(created.copy(id = rowId))
        return created.credentialId
    }

    /** Updates the resource already at the candidate's path, or adds it - as a destination when a slot is free. */
    private suspend fun saveResource(
        candidate: MediaResource,
        addToDestinations: Boolean,
        existingResources: MutableList<MediaResource>,
    ): Outcome {
        val existingResource = existingResources.find { it.path == candidate.path }
        if (existingResource != null) {
            val revised = existingResource.copy(
                name = candidate.name,
                credentialsId = candidate.credentialsId ?: existingResource.credentialsId,
                accessPin = candidate.accessPin ?: existingResource.accessPin,
                hostKeyFingerprint = candidate.hostKeyFingerprint ?: existingResource.hostKeyFingerprint,
            )
            resourceRepository.updateResource(revised)
            existingResources[existingResources.indexOf(existingResource)] = revised
            return Outcome.UPDATED
        }
        val nextOrder = if (addToDestinations) getDestinationsUseCase.getNextAvailableOrder() else NO_SLOT
        val created = if (nextOrder == NO_SLOT) {
            candidate
        } else {
            candidate.copy(
                isDestination = true,
                destinationOrder = nextOrder,
                destinationColor = DestinationColors.getColorForDestination(nextOrder),
            )
        }
        val newId = resourceRepository.addResource(created)
        existingResources.add(created.copy(id = newId))
        return Outcome.IMPORTED
    }

    private companion object {
        val CREDENTIAL_TYPES = setOf(ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP)
        const val SFTP_PORT = 22
        const val FTP_PORT = 21
        const val SMB_PORT = 445
        const val NO_SLOT = -1
    }
}
