package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.data.remote.sftp.HostKeyMismatchException
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SFTP (password auth) and FTP flows: test, unified add, and legacy add wrappers.
 *
 * SSH-key based SFTP lives in [AddResourceSftpKeyCoordinator] - the credential
 * save and validation paths diverge enough that keeping them separate avoids
 * hard-to-follow branching inside a single method.
 */
internal class AddResourceSftpFtpCoordinator(
    private val context: Context,
    private val addResourceUseCase: AddResourceUseCase,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val resourceRepository: ResourceRepository,
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
            bridge.emit(AddResourceEvent.ShowTestResult(message, isSuccess = false))
        } else {
            bridge.emit(AddResourceEvent.ShowTestResult(
                context.getString(R.string.addresource_connection_failed),
                isSuccess = false
            ))
        }
    }

    fun testSftpFtpConnection(
        protocolType: ResourceType,
        host: String,
        port: Int,
        username: String,
        password: String,
        expectedFingerprint: String? = null
    ) {
        if (host.isBlank()) {
            bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_host_required)))
            return
        }
        val (fpOk, canonicalFingerprint) = normalizeFingerprintOrEmitError(expectedFingerprint)
        if (!fpOk) return

        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)

            when (protocolType) {
                ResourceType.SFTP -> {
                    smbOperationsUseCase.testSftpConnection(
                        host = host, port = port, username = username, password = password,
                        expectedFingerprint = canonicalFingerprint
                    ).onSuccess { message ->
                        Timber.d("SFTP test connection successful")
                        bridge.emit(AddResourceEvent.ShowTestResult(message, isSuccess = true))
                    }.onFailure { e ->
                        Timber.e(e, "SFTP test connection failed")
                        emitSftpTestFailure(e)
                    }
                }
                ResourceType.FTP -> {
                    smbOperationsUseCase.testFtpConnection(
                        host = host, port = port, username = username, password = password
                    ).onSuccess { message ->
                        Timber.d("FTP test connection successful")
                        bridge.emit(AddResourceEvent.ShowTestResult(message, isSuccess = true))
                    }.onFailure { e ->
                        Timber.e(e, "FTP test connection failed")
                        bridge.emit(AddResourceEvent.ShowTestResult(
                            context.getString(R.string.addresource_connection_failed),
                            isSuccess = false
                        ))
                    }
                }
                else -> bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_protocol_invalid)))
            }

            bridge.markLoading(false)
        }
    }

    fun testSftpConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        expectedFingerprint: String? = null
    ) {
        if (host.isBlank()) {
            bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_host_required)))
            return
        }
        val (fpOk, canonicalFingerprint) = normalizeFingerprintOrEmitError(expectedFingerprint)
        if (!fpOk) return

        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)
            smbOperationsUseCase.testSftpConnection(
                host = host, port = port, username = username, password = password,
                expectedFingerprint = canonicalFingerprint
            ).onSuccess { message ->
                Timber.d("SFTP test connection successful")
                bridge.emit(AddResourceEvent.ShowTestResult(message, isSuccess = true))
            }.onFailure { e ->
                Timber.e(e, "SFTP test connection failed")
                emitSftpTestFailure(e)
            }
            bridge.markLoading(false)
        }
    }

    fun addSftpFtpResource(
        protocolType: ResourceType,
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        resourceName: String? = null,
        comment: String? = null,
        supportedTypes: Set<MediaType> = emptySet(),
        isReadOnly: Boolean = false,
        allFiles: Boolean = false,
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
        val (fpOk, canonicalFingerprint) = normalizeFingerprintOrEmitError(hostKeyFingerprint)
        if (!fpOk) return

        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)

            val protocolName = if (protocolType == ResourceType.SFTP) "SFTP" else "FTP"
            val protocolLower = protocolName.lowercase()

            val credentialsResult = when (protocolType) {
                ResourceType.SFTP -> smbOperationsUseCase.saveSftpCredentials(
                    host = host, port = port, username = username, password = password
                )
                ResourceType.FTP -> smbOperationsUseCase.saveFtpCredentials(
                    host = host, port = port, username = username, password = password
                )
                else -> Result.failure(Exception("Invalid protocol type"))
            }

            credentialsResult.onSuccess { credentialsId ->
                Timber.d("Saved $protocolName credentials with ID: $credentialsId")

                val destSlot = finalizer.allocateDestinationSlot(addToDestinations, isReadOnly)
                    ?: return@onSuccess
                val (isDestination, destinationOrder, destinationColor) = destSlot

                val formattedRemotePath = if (remotePath.startsWith("/") || remotePath.isEmpty()) remotePath else "/$remotePath"
                val path = "$protocolLower://$host:$port$formattedRemotePath"

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
                    type = protocolType,
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
                    hostKeyFingerprint = if (protocolType == ResourceType.SFTP) canonicalFingerprint else null
                )

                addResourceUseCase.addMultiple(listOf(resource)).onSuccess { addResult ->
                    Timber.d("Added $protocolName resource to DB")

                    val scanSuccessful = finalizer.scanInsertedResource(
                        resource = resource,
                        credentialsId = credentialsId
                    )

                    if (scanSuccessful) {
                        bridge.emit(AddResourceEvent.ShowMessage(context.getString(R.string.addresource_resource_added)))
                    } else {
                        bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_resource_unavailable_after_add)))
                    }
                    bridge.emit(AddResourceEvent.ResourcesAdded(addResult.createdResourceIds))
                }.onFailure { e ->
                    Timber.e(e, "Failed to add $protocolName resource")
                    bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_add_failed)))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save $protocolName credentials")
                bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_save_credentials_failed)))
            }

            bridge.markLoading(false)
        }
    }

    fun addSftpResource(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ) {
        if (host.isBlank()) {
            bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_host_required)))
            return
        }

        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)

            smbOperationsUseCase.saveSftpCredentials(
                host = host, port = port, username = username, password = password
            ).onSuccess { credentialsId ->
                Timber.d("Saved SFTP credentials with ID: $credentialsId")

                val path = "sftp://$host:$port$remotePath"
                val resourceName = if (remotePath == "/" || remotePath.isBlank()) {
                    "$username@$host"
                } else {
                    remotePath.substringAfterLast('/')
                }
                val supportedTypes = bridge.supportedMediaTypes()

                val resource = MediaResource(
                    id = 0,
                    name = resourceName,
                    path = path,
                    type = ResourceType.SFTP,
                    isDestination = false,
                    credentialsId = credentialsId,
                    supportedMediaTypes = supportedTypes
                )

                addResourceUseCase.addMultiple(listOf(resource)).onSuccess { addResult ->
                    Timber.d("Added SFTP resource")

                    val scanSuccessful = finalizer.scanInsertedResource(
                        resource = resource,
                        credentialsId = credentialsId
                    )

                    if (scanSuccessful) {
                        bridge.emit(AddResourceEvent.ShowMessage(context.getString(R.string.addresource_resource_added)))
                    } else {
                        bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_resource_unavailable_after_add)))
                    }
                    bridge.emit(AddResourceEvent.ResourcesAdded(addResult.createdResourceIds))
                }.onFailure { e ->
                    Timber.e(e, "Failed to add SFTP resource")
                    bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_add_failed)))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save SFTP credentials")
                bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_save_credentials_failed)))
            }

            bridge.markLoading(false)
        }
    }
}
