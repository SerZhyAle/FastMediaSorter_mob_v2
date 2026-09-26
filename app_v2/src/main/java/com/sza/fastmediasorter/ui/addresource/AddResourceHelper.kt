package com.sza.fastmediasorter.ui.addresource

import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import timber.log.Timber

@android.annotation.SuppressLint("SetTextI18n")
class AddResourceHelper(
    private val activity: AddResourceActivity
) {

    // S1519: lazy ViewStub-backed form bindings owned by the activity (inflate on first access).
    private val localForm get() = activity.forms.local
    private val smbForm get() = activity.forms.smb
    private val sftpForm get() = activity.forms.sftp

    /**
     * Pre-fill form fields with data from resource being copied
     */
    fun preFillResourceData(
        resource: MediaResource,
        username: String? = null,
        password: String? = null,
        domain: String? = null,
        sshKey: String? = null,
        sshPassphrase: String? = null
    ) {
        Timber.d("Pre-filling data from resource: ${resource.name} (type: ${resource.type})")

        when (resource.type) {
            ResourceType.LOCAL -> {
                activity.showLocalFolderOptions()
                localForm.etLocalPinCode.setText(resource.accessPin.orEmpty())
                // For local, path is already selected by user via folder picker
                // We can't pre-select it, but show message
                Toast.makeText(
                    activity,
                    activity.getString(R.string.select_folder_copy_location),
                    Toast.LENGTH_LONG
                ).show()
            }

            ResourceType.SMB -> {
                activity.showSmbFolderOptions(afterDefaults = { prefillSmbOptions(resource) })

                // Parse SMB path: smb://server/share/subfolder1/subfolder2
                val smbPath = resource.path.removePrefix("smb://")
                val parts = smbPath.split("/", limit = 2)

                if (parts.isNotEmpty()) {
                    smbForm.etSmbServer.setText(parts[0])
                }
                if (parts.size > 1) {
                    // Keep entire share path including subfolders (e.g., "photos/2025")
                    smbForm.etSmbShareName.setText(parts[1])
                }

                // Pre-fill credentials
                if (username != null) smbForm.etSmbUsername.setText(username)
                if (password != null) smbForm.etSmbPassword.setText(password)
                if (domain != null) smbForm.etSmbDomain.setText(domain)
                smbForm.etSmbPinCode.setText(resource.accessPin.orEmpty())

                smbForm.etSmbPort.setText(R.string.default_smb_port)

                smbForm.etSmbComment.setText(resource.comment ?: "")

                Toast.makeText(
                    activity,
                    activity.getString(R.string.review_smb_details),
                    Toast.LENGTH_SHORT
                ).show()
            }

            ResourceType.SFTP -> {
                activity.showSftpFolderOptions(afterDefaults = { prefillSftpOptions(resource) })

                // Parse SFTP path: sftp://host:port/path
                val sftpPath = resource.path.removePrefix("sftp://")
                val hostAndPath = sftpPath.split("/", limit = 2)

                if (hostAndPath.isNotEmpty()) {
                    val hostPort = hostAndPath[0].split(":")
                    sftpForm.etSftpHost.setText(hostPort[0])
                    if (hostPort.size > 1) {
                        sftpForm.etSftpPort.setText(hostPort[1])
                    } else {
                        sftpForm.etSftpPort.setText(R.string.default_sftp_port)
                    }
                }

                if (hostAndPath.size > 1) {
                    sftpForm.etSftpPath.setText(activity.getString(R.string.path_format, hostAndPath[1]))
                }

                sftpForm.rbSftp.isChecked = true

                // Pre-fill credentials
                if (username != null) sftpForm.etSftpUsername.setText(username)
                sftpForm.etSftpPinCode.setText(resource.accessPin.orEmpty())
                // S0046: prefill the pinned host-key fingerprint; clearing it on save reverts to permissive mode.
                sftpForm.etSftpHostKeyFingerprint.setText(resource.hostKeyFingerprint.orEmpty())
                // Reveal the optional security block when a fingerprint is already pinned so the saved value is visible on edit.
                // notify=false keeps this transient and avoids persisting the expanded state for future new resources.
                if (!resource.hostKeyFingerprint.isNullOrBlank()) {
                    sftpForm.headerSftpServerVerification.setExpanded(true, notify = false)
                    sftpForm.contentSftpServerVerification.visibility = android.view.View.VISIBLE
                }

                if (sshKey != null) {
                    sftpForm.rbSftpSshKey.isChecked = true
                    sftpForm.etSftpPrivateKey.setText(sshKey)
                    if (sshPassphrase != null) sftpForm.etSftpKeyPassphrase.setText(sshPassphrase)
                } else {
                    sftpForm.rbSftpPassword.isChecked = true
                    if (password != null) sftpForm.etSftpPassword.setText(password)
                }

                sftpForm.etSftpComment.setText(resource.comment ?: "")

                Toast.makeText(
                    activity,
                    activity.getString(R.string.review_sftp_details),
                    Toast.LENGTH_SHORT
                ).show()
            }

            ResourceType.FTP -> {
                // FTP shares the SFTP form.
                activity.showSftpFolderOptions(afterDefaults = { prefillSftpOptions(resource) })

                // Parse FTP path: ftp://host:port/path
                val ftpPath = resource.path.removePrefix("ftp://")
                val hostAndPath = ftpPath.split("/", limit = 2)

                if (hostAndPath.isNotEmpty()) {
                    val hostPort = hostAndPath[0].split(":")
                    sftpForm.etSftpHost.setText(hostPort[0])
                    if (hostPort.size > 1) {
                        sftpForm.etSftpPort.setText(hostPort[1])
                    } else {
                        sftpForm.etSftpPort.setText(R.string.default_ftp_port)
                    }
                }

                if (hostAndPath.size > 1) {
                    sftpForm.etSftpPath.setText(activity.getString(R.string.path_format, hostAndPath[1]))
                }

                sftpForm.rbFtp.isChecked = true

                // Pre-fill credentials
                if (username != null) sftpForm.etSftpUsername.setText(username)
                if (password != null) sftpForm.etSftpPassword.setText(password)
                sftpForm.etSftpPinCode.setText(resource.accessPin.orEmpty())

                sftpForm.etSftpComment.setText(resource.comment ?: "")

                Toast.makeText(
                    activity,
                    activity.getString(R.string.review_ftp_details),
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                // CLOUD or other future types
                activity.showCloudStorageOptions()

                Toast.makeText(
                    activity,
                    activity.getString(R.string.select_cloud_folder_copy),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun prefillSmbOptions(resource: MediaResource) {
        Timber.d("S3736: SMB copy prefill after defaults types=${resource.supportedMediaTypes}")
        val types = resource.supportedMediaTypes
        smbForm.cbSmbScanSubdirectories.isChecked = resource.scanSubdirectories
        smbForm.cbSmbAllFiles.isChecked = resource.allFiles
        smbForm.cbSmbRememberFileList.isChecked = resource.rememberFileList
        smbForm.cbSmbShowSubfoldersAsItems.isChecked = resource.showSubfoldersAsItems
        smbForm.cbSmbDisableThumbnails.isChecked = resource.disableThumbnails
        smbForm.cbSmbSupportImage.isChecked = MediaType.IMAGE in types
        smbForm.cbSmbSupportVideo.isChecked = MediaType.VIDEO in types
        smbForm.cbSmbSupportAudio.isChecked = MediaType.AUDIO in types
        smbForm.cbSmbSupportGif.isChecked = MediaType.GIF in types
        smbForm.cbSmbSupportText.isChecked = MediaType.TEXT in types
        smbForm.cbSmbSupportPdf.isChecked = MediaType.PDF in types
        smbForm.cbSmbSupportEpub.isChecked = MediaType.EPUB in types
        smbForm.cbSmbSupportOffice.isChecked = MediaType.OFFICE_DOCUMENT in types
    }

    private fun prefillSftpOptions(resource: MediaResource) {
        Timber.d("S3736: SFTP/FTP copy prefill after defaults types=${resource.supportedMediaTypes}")
        val types = resource.supportedMediaTypes
        sftpForm.cbSftpScanSubdirectories.isChecked = resource.scanSubdirectories
        sftpForm.cbSftpAllFiles.isChecked = resource.allFiles
        sftpForm.cbSftpRememberFileList.isChecked = resource.rememberFileList
        sftpForm.cbSftpShowSubfoldersAsItems.isChecked = resource.showSubfoldersAsItems
        sftpForm.cbSftpDisableThumbnails.isChecked = resource.disableThumbnails
        sftpForm.cbSftpSupportImage.isChecked = MediaType.IMAGE in types
        sftpForm.cbSftpSupportVideo.isChecked = MediaType.VIDEO in types
        sftpForm.cbSftpSupportAudio.isChecked = MediaType.AUDIO in types
        sftpForm.cbSftpSupportGif.isChecked = MediaType.GIF in types
        sftpForm.cbSftpSupportText.isChecked = MediaType.TEXT in types
        sftpForm.cbSftpSupportPdf.isChecked = MediaType.PDF in types
        sftpForm.cbSftpSupportEpub.isChecked = MediaType.EPUB in types
        sftpForm.cbSftpSupportOffice.isChecked = MediaType.OFFICE_DOCUMENT in types
    }
}
