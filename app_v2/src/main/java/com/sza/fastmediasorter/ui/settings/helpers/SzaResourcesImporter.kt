package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
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
 * S0046 - imports predefined resources from the bundled `res/xml/sza_resources.xml` into the
 * database. Extracted from `SettingsViewModel` so the parsing/credentials/fingerprint logic lives
 * outside the UI layer.
 *
 * SFTP rows may declare `auth="key"` with a `privateKeyAsset` (a file under `assets/sftp_keys/`),
 * an optional `keyPassphrase`, and a `hostKeyFingerprint` for pinning. The bundled key is read once
 * here and stored encrypted in credentials storage; the asset is never read again at runtime. A
 * declared-but-unreadable key skips the resource entirely (no silent password fallback). Key bytes
 * and passphrases are never logged.
 */
@Singleton
class SzaResourcesImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val getDestinationsUseCase: GetDestinationsUseCase,
) {

    sealed interface ImportResult {
        data class Success(val imported: Int, val updated: Int, val skipped: Int) : ImportResult
        data class Failure(val error: Throwable) : ImportResult
    }

    suspend fun import(): ImportResult = withContext(Dispatchers.IO) {
        try {
            val parser = context.resources.getXml(com.sza.fastmediasorter.R.xml.sza_resources)
            val existingResources = resourceRepository.getAllResourcesSync()
            val existingCredentials = credentialsRepository.getAllCredentials().first()

            var imported = 0
            var updated = 0
            var skipped = 0

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "resource") {
                    when (importOne(parser, existingResources, existingCredentials)) {
                        Outcome.IMPORTED -> imported++
                        Outcome.UPDATED -> updated++
                        Outcome.SKIPPED -> skipped++
                    }
                }
                eventType = parser.next()
            }
            ImportResult.Success(imported = imported, updated = updated, skipped = skipped)
        } catch (e: Exception) {
            Timber.e(e, "Error importing SZA resources")
            ImportResult.Failure(e)
        }
    }

    private enum class Outcome { IMPORTED, UPDATED, SKIPPED }

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
            val privateKeyAsset = parser.getAttributeValue(null, "privateKeyAsset")
            val keyPassphrase = parser.getAttributeValue(null, "keyPassphrase")
            val hostKeyFingerprintRaw = parser.getAttributeValue(null, "hostKeyFingerprint")

            val type = try {
                ResourceType.valueOf(typeStr)
            } catch (e: Exception) {
                ResourceType.LOCAL
            }

            // Load bundled private key when this resource declares key-auth. A declared-but-unreadable
            // key is a release packaging bug: skip the whole resource rather than fall back to a
            // password and create an inconsistent auth method (strategic spec S0046 §6.2).
            var bundledPrivateKey: String? = null
            if (type == ResourceType.SFTP && auth == "key") {
                if (privateKeyAsset.isNullOrBlank()) {
                    Timber.w("Predefined SFTP resource '$name' declares auth=key without privateKeyAsset; skipping")
                    return Outcome.SKIPPED
                }
                bundledPrivateKey = readBundledKey(privateKeyAsset, name) ?: return Outcome.SKIPPED
                if (!keyPassphrase.isNullOrBlank()) {
                    // The credentials schema has no passphrase column, so a passphrase-protected
                    // bundled key cannot be unlocked at runtime. Surface this instead of silently
                    // shipping a key that will fail to load.
                    Timber.w("Predefined SFTP resource '$name' declares keyPassphrase, which is not persisted; an encrypted key will fail to load")
                }
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

            val mediaTypes = if (supportedTypesStr != null) {
                supportedTypesStr.split(",").mapNotNull {
                    try { MediaType.valueOf(it.trim()) } catch (e: Exception) { null }
                }.toSet()
            } else {
                setOf(MediaType.IMAGE, MediaType.VIDEO)
            }

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

    /**
     * Reads a bundled private key from `assets/sftp_keys/`. Returns null (and logs at warn, without
     * key bytes) when the asset is missing or unreadable so the caller skips the resource.
     */
    private fun readBundledKey(privateKeyAsset: String, resourceName: String): String? = try {
        context.assets.open("sftp_keys/$privateKeyAsset").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        Timber.w("Bundled SFTP key '$privateKeyAsset' for resource '$resourceName' unreadable: ${e.message}")
        null
    }
}
