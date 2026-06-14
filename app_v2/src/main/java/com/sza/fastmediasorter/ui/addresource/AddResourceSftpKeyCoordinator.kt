package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.data.remote.sftp.HostKeyMismatchException
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SFTP SSH-private-key authentication flows. Kept separate from
 * [AddResourceSftpFtpCoordinator] because credential save reuses the password
 * field for the key passphrase - mixing the two paths inside one function
 * produces overly branchy code that's hard to reason about.
 */
internal class AddResourceSftpKeyCoordinator(
    private val context: Context,
    private val addResourceUseCase: AddResourceUseCase,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val settingsRepository: SettingsRepository,
    private val finalizer: AddResourceFinalizer,
    private val bridge: AddResourceBridge
) {

    /**
     * S0046: blank input -> null (permissive). Non-blank but unparseable -> emit a typed validation
     * error and return null with `ok=false` so the caller aborts. Otherwise the canonical fingerprint.
     */
    private fun normalizeFingerprintOrEmitError(raw: String?): Pair<Boolean, String?> {
        if (raw.isNullOrBlank()) return true to null
        val canonical = SshFingerprintNormalizer.canonical(raw)
        if (canonical == null) {
            bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.sftp_host_key_fingerprint_invalid)))
            return false to null
        }
        return true to canonical
    }

    /** S0046: route a pinned host-key rejection to its own message, distinct from auth failures. */
    private fun emitSftpTestFailure(e: Throwable) {
        if (e is HostKeyMismatchException) {
            val message = context.getString(R.string.sftp_host_key_mismatch_title) + "\n" +
                context.getString(
                    R.string.sftp_host_key_mismatch_body_format,
                    SshFingerprintNormalizer.shortForList(e.expected),
                    e.actual
                )
            bridge.emit(AddResourceEvent.ShowTestResult(message, false))
        } else {
            bridge.emit(AddResourceEvent.ShowTestResult(
                context.getString(R.string.addresource_connection_failed),
                false
            ))
        }
    }

    fun testSftpConnectionWithKey(
        host: String,
        port: Int,
        username: String,
        privateKey: String,
        keyPassphrase: String?,
        expectedFingerprint: String? = null
    ) {
        val (fpOk, canonicalFingerprint) = normalizeFingerprintOrEmitError(expectedFingerprint)
        if (!fpOk) return

        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)

            smbOperationsUseCase.testSftpConnection(
                host = host,
                port = port,
                username = username,
                // password is unused for key auth; the client routes on privateKey presence
                password = "",
                privateKey = privateKey,
                keyPassphrase = keyPassphrase,
                expectedFingerprint = canonicalFingerprint
            ).onSuccess { message ->
                Timber.d("SFTP SSH key connection test successful: $message")
                bridge.emit(AddResourceEvent.ShowTestResult(message, true))
            }.onFailure { e ->
                Timber.e(e, "SFTP SSH key connection test failed")
                emitSftpTestFailure(e)
            }

            bridge.markLoading(false)
        }
    }

    fun addSftpResourceWithKey(
        host: String,
        port: Int,
        username: String,
        privateKey: String,
        keyPassphrase: String?,
        remotePath: String,
        resourceName: String? = null,
        comment: String? = null,
        supportedTypes: Set<MediaType> = emptySet(),
        allFiles: Boolean = false,
        isReadOnly: Boolean = false,
        scanSubdirectories: Boolean = false,
        addToDestinations: Boolean = false,
        rememberFileList: Boolean = false,
        disableThumbnails: Boolean = false,
        showSubfoldersAsItems: Boolean = false,
        accessPin: String? = null,
        profile: ResourceProfile = ResourceProfile.NONE,
        hostKeyFingerprint: String? = null
    ) {
        if (host.isBlank()) {
            bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_host_required)))
            return
        }
        if (privateKey.isBlank()) {
            bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_private_key_required)))
            return
        }
        val (fpOk, canonicalFingerprint) = normalizeFingerprintOrEmitError(hostKeyFingerprint)
        if (!fpOk) return

        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)

            // Credential store reuses the password column for the key passphrase -
            // the actual secret is the private key body kept in privateKey.
            smbOperationsUseCase.saveSftpCredentials(
                host = host,
                port = port,
                username = username,
                password = keyPassphrase ?: "",
                privateKey = privateKey
            ).onSuccess { credentialsId ->
                Timber.d("Saved SFTP SSH key credentials with ID: $credentialsId")

                val destSlot = finalizer.allocateDestinationSlot(addToDestinations, isReadOnly)
                    ?: return@onSuccess
                val (isDestination, destinationOrder, destinationColor) = destSlot

                val formattedRemotePath = if (remotePath.startsWith("/") || remotePath.isEmpty()) remotePath else "/$remotePath"
                val path = "sftp://$host:$port$formattedRemotePath"

                val autoGeneratedName = if (formattedRemotePath == "/" || formattedRemotePath.isBlank()) {
                    "$username@$host"
                } else {
                    formattedRemotePath.substringAfterLast('/')
                }
                val finalName = if (!resourceName.isNullOrBlank()) resourceName else autoGeneratedName

                val settings = settingsRepository.getSettings().first()
                val displayMode = if (settings.defaultGridMode) DisplayMode.GRID else DisplayMode.LIST
                val finalSupportedTypes = if (supportedTypes.isEmpty()) bridge.supportedMediaTypes() else supportedTypes

                val resource = MediaResource(
                    id = 0,
                    name = finalName,
                    path = path,
                    type = ResourceType.SFTP,
                    isDestination = isDestination,
                    destinationOrder = destinationOrder,
                    destinationColor = destinationColor,
                    credentialsId = credentialsId,
                    comment = comment,
                    displayMode = displayMode,
                    sortMode = settings.defaultSortMode,
                    slideshowInterval = settings.slideshowInterval,
                    supportedMediaTypes = finalSupportedTypes,
                    isReadOnly = isReadOnly,
                    allFiles = allFiles,
                    scanSubdirectories = scanSubdirectories,
                    rememberFileList = rememberFileList,
                    disableThumbnails = disableThumbnails,
                    showSubfoldersAsItems = showSubfoldersAsItems,
                    accessPin = accessPin?.ifBlank { null },
                    profile = profile,
                    hostKeyFingerprint = canonicalFingerprint
                )

                addResourceUseCase.addMultiple(listOf(resource)).onSuccess { _ ->
                    Timber.d("Added SFTP resource with SSH key")

                    val scanSuccessful = finalizer.scanInsertedResource(
                        resource = resource,
                        credentialsId = credentialsId
                    )

                    if (scanSuccessful) {
                        bridge.emit(AddResourceEvent.ShowMessage(context.getString(R.string.addresource_resource_added)))
                    } else {
                        bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_resource_unavailable_after_add)))
                    }
                    bridge.emit(AddResourceEvent.ResourcesAdded)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add SFTP resource")
                    bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_add_failed)))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save SFTP SSH key credentials")
                bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_save_credentials_failed)))
            }

            bridge.markLoading(false)
        }
    }
}
