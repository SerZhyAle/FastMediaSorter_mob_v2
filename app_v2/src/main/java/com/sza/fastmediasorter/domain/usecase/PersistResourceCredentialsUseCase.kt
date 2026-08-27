package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * S2086: owns everything the resource editor used to know about network credentials - password
 * encryption, the UUID of a stored record, each protocol's default port, and the rule that turns
 * an SMB "share/subfolder" path into a bare share name. None of that is used by the form editor,
 * the persistence-model builder or the post-save verification it used to share a class with.
 */
class PersistResourceCredentialsUseCase @Inject constructor(
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val credentialsRepository: NetworkCredentialsRepository
) {

    /**
     * Persists network credentials (SMB/SFTP/FTP) and returns a copy with credentialsId set.
     * EDIT (credentialsId present): updates in-place by UUID, because the server+share lookup
     * an insert would do can miss after the user edits the very fields it keys on.
     * CREATE: delegates to SmbOperationsUseCase, which inserts or updates by that server key.
     */
    suspend operator fun invoke(formData: ResourceFormData): ResourceFormData {
        Timber.d("S2086: persist entry type=${formData.type} credId=${formData.credentialsId != null}")
        if (formData.type !in NETWORK_TYPES) return formData
        val existingCredentialId = formData.credentialsId
        return if (existingCredentialId == null) {
            insert(formData)
        } else {
            updateInPlace(formData, existingCredentialId)
        }
    }

    private suspend fun insert(formData: ResourceFormData): ResourceFormData {
        val credentialId: String? = when (formData.type) {
            ResourceType.SMB -> {
                Timber.d("persistCredentials: CREATE SMB for host=${formData.host}")
                smbOperationsUseCase.saveCredentials(
                    server = formData.host,
                    shareName = formData.path,
                    username = formData.username,
                    password = formData.password,
                    // ResourceFormData has no domain field; SMB domain is not exposed in the editor.
                    domain = "",
                    port = formData.port ?: SMB_DEFAULT_PORT
                ).onFailure { e -> Timber.e(e, "persistCredentials: SMB insert failed") }
                    .getOrNull()
            }

            ResourceType.SFTP -> {
                Timber.d("persistCredentials: CREATE SFTP for host=${formData.host}")
                smbOperationsUseCase.saveSftpCredentials(
                    host = formData.host,
                    port = formData.port ?: SFTP_DEFAULT_PORT,
                    username = formData.username,
                    password = formData.password
                ).onFailure { e -> Timber.e(e, "persistCredentials: SFTP insert failed") }
                    .getOrNull()
            }

            ResourceType.FTP -> {
                Timber.d("persistCredentials: CREATE FTP for host=${formData.host}")
                smbOperationsUseCase.saveFtpCredentials(
                    host = formData.host,
                    port = formData.port ?: FTP_DEFAULT_PORT,
                    username = formData.username,
                    password = formData.password
                ).onFailure { e -> Timber.e(e, "persistCredentials: FTP insert failed") }
                    .getOrNull()
            }

            else -> null
        }
        return if (credentialId == null) formData else formData.copy(credentialsId = credentialId)
    }

    /** Updates an existing credential in-place by UUID; falls back to insert if not found. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun updateInPlace(formData: ResourceFormData, credentialId: String): ResourceFormData {
        return try {
            val existing = credentialsRepository.getByCredentialId(credentialId)
            if (existing == null) {
                Timber.w("updateInPlace: $credentialId not found, falling back to insert")
                return insert(formData.copy(credentialsId = null))
            }
            credentialsRepository.update(
                existing.copy(
                    server = formData.host,
                    port = formData.port ?: defaultPortFor(formData.type, existing.port),
                    username = formData.username,
                    encryptedPassword = reEncryptedPassword(formData, existing.encryptedPassword),
                    shareName = shareNameFor(formData, existing.shareName),
                    // ResourceFormData has no domain field; preserve whatever was stored.
                    domain = existing.domain
                )
            )
            val pwdChanged = formData.password.isNotEmpty()
            Timber.d("updateInPlace: OK id=$credentialId type=${formData.type} pwd=$pwdChanged")
            formData // credentialsId is already correct
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "updateInPlace: failed for $credentialId")
            formData
        }
    }

    private fun defaultPortFor(type: ResourceType, fallback: Int): Int = when (type) {
        ResourceType.SMB -> SMB_DEFAULT_PORT
        ResourceType.SFTP -> SFTP_DEFAULT_PORT
        ResourceType.FTP -> FTP_DEFAULT_PORT
        else -> fallback
    }

    /** Re-encrypts only when the user actually typed a password; otherwise keeps the stored hash. */
    private fun reEncryptedPassword(formData: ResourceFormData, stored: String): String =
        if (formData.password.isEmpty()) {
            stored
        } else {
            CryptoHelper.encrypt(formData.password) ?: stored
        }

    /**
     * SMB stores the bare share, so "share/subfolder" contributes only its first segment.
     * SFTP and FTP have no share concept at all, so the stored value is preserved as-is.
     */
    private fun shareNameFor(formData: ResourceFormData, stored: String?): String? =
        if (formData.type == ResourceType.SMB) {
            formData.path.replace('\\', '/').split('/').firstOrNull()?.takeIf { it.isNotEmpty() } ?: stored
        } else {
            stored
        }

    private companion object {
        val NETWORK_TYPES = setOf(ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP)
        const val SMB_DEFAULT_PORT = 445
        const val SFTP_DEFAULT_PORT = 22
        const val FTP_DEFAULT_PORT = 21
    }
}
