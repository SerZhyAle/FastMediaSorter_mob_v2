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
                // Show SMB section and pre-fill fields
                activity.showSmbFolderOptions()

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

                // Pre-fill comment
                smbForm.etSmbComment.setText(resource.comment ?: "")

                // Pre-fill scan subdirectories
                smbForm.cbSmbScanSubdirectories.isChecked = resource.scanSubdirectories

                // Pre-fill all files mode
                smbForm.cbSmbAllFiles.isChecked = resource.allFiles

                // Pre-fill remember file list
                smbForm.cbSmbRememberFileList.isChecked = resource.rememberFileList

                // Pre-fill show subfolders as items and disable thumbnails - were added to create
                // form as part of option-parity fix; must be restored from source resource on copy
                smbForm.cbSmbShowSubfoldersAsItems.isChecked = resource.showSubfoldersAsItems
                smbForm.cbSmbDisableThumbnails.isChecked = resource.disableThumbnails

                // Pre-fill supported media types
                smbForm.cbSmbSupportImage.isChecked = MediaType.IMAGE in resource.supportedMediaTypes
                smbForm.cbSmbSupportVideo.isChecked = MediaType.VIDEO in resource.supportedMediaTypes
                smbForm.cbSmbSupportAudio.isChecked = MediaType.AUDIO in resource.supportedMediaTypes
                smbForm.cbSmbSupportGif.isChecked = MediaType.GIF in resource.supportedMediaTypes
                smbForm.cbSmbSupportText.isChecked = MediaType.TEXT in resource.supportedMediaTypes
                smbForm.cbSmbSupportPdf.isChecked = MediaType.PDF in resource.supportedMediaTypes
                smbForm.cbSmbSupportEpub.isChecked = MediaType.EPUB in resource.supportedMediaTypes

                Toast.makeText(
                    activity,
                    activity.getString(R.string.review_smb_details),
                    Toast.LENGTH_SHORT
                ).show()
            }

            ResourceType.SFTP -> {
                // Show SFTP section and pre-fill fields
                activity.showSftpFolderOptions()

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

                // Pre-fill comment
                sftpForm.etSftpComment.setText(resource.comment ?: "")

                // Pre-fill scan subdirectories
                sftpForm.cbSftpScanSubdirectories.isChecked = resource.scanSubdirectories

                // Pre-fill all files mode
                sftpForm.cbSftpAllFiles.isChecked = resource.allFiles

                // Pre-fill remember file list
                sftpForm.cbSftpRememberFileList.isChecked = resource.rememberFileList

                // Pre-fill show subfolders as items and disable thumbnails - option-parity fields
                sftpForm.cbSftpShowSubfoldersAsItems.isChecked = resource.showSubfoldersAsItems
                sftpForm.cbSftpDisableThumbnails.isChecked = resource.disableThumbnails

                // Pre-fill supported media types
                sftpForm.cbSftpSupportImage.isChecked = MediaType.IMAGE in resource.supportedMediaTypes
                sftpForm.cbSftpSupportVideo.isChecked = MediaType.VIDEO in resource.supportedMediaTypes
                sftpForm.cbSftpSupportAudio.isChecked = MediaType.AUDIO in resource.supportedMediaTypes
                sftpForm.cbSftpSupportGif.isChecked = MediaType.GIF in resource.supportedMediaTypes
                sftpForm.cbSftpSupportText.isChecked = MediaType.TEXT in resource.supportedMediaTypes
                sftpForm.cbSftpSupportPdf.isChecked = MediaType.PDF in resource.supportedMediaTypes
                sftpForm.cbSftpSupportEpub.isChecked = MediaType.EPUB in resource.supportedMediaTypes

                Toast.makeText(
                    activity,
                    activity.getString(R.string.review_sftp_details),
                    Toast.LENGTH_SHORT
                ).show()
            }

            ResourceType.FTP -> {
                // Show FTP section (same UI as SFTP)
                activity.showSftpFolderOptions()

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

                // Pre-fill comment
                sftpForm.etSftpComment.setText(resource.comment ?: "")

                // Pre-fill scan subdirectories
                sftpForm.cbSftpScanSubdirectories.isChecked = resource.scanSubdirectories

                // Pre-fill all files mode
                sftpForm.cbSftpAllFiles.isChecked = resource.allFiles

                // Pre-fill remember file list
                sftpForm.cbSftpRememberFileList.isChecked = resource.rememberFileList

                // Pre-fill show subfolders as items and disable thumbnails - option-parity fields
                sftpForm.cbSftpShowSubfoldersAsItems.isChecked = resource.showSubfoldersAsItems
                sftpForm.cbSftpDisableThumbnails.isChecked = resource.disableThumbnails

                // Pre-fill supported media types
                sftpForm.cbSftpSupportImage.isChecked = MediaType.IMAGE in resource.supportedMediaTypes
                sftpForm.cbSftpSupportVideo.isChecked = MediaType.VIDEO in resource.supportedMediaTypes
                sftpForm.cbSftpSupportAudio.isChecked = MediaType.AUDIO in resource.supportedMediaTypes
                sftpForm.cbSftpSupportGif.isChecked = MediaType.GIF in resource.supportedMediaTypes
                sftpForm.cbSftpSupportText.isChecked = MediaType.TEXT in resource.supportedMediaTypes
                sftpForm.cbSftpSupportPdf.isChecked = MediaType.PDF in resource.supportedMediaTypes
                sftpForm.cbSftpSupportEpub.isChecked = MediaType.EPUB in resource.supportedMediaTypes

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
}
