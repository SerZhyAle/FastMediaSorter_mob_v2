package com.sza.fastmediasorter.ui.addresource

import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import timber.log.Timber

@android.annotation.SuppressLint("SetTextI18n")
class AddResourceHelper(
    private val activity: AddResourceActivity,
    private val binding: ActivityAddResourceBinding
) {

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
                binding.etLocalPinCode.setText(resource.accessPin.orEmpty())
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
                    binding.etSmbServer.setText(parts[0])
                }
                if (parts.size > 1) {
                    // Keep entire share path including subfolders (e.g., "photos/2025")
                    binding.etSmbShareName.setText(parts[1])
                }

                // Pre-fill credentials
                if (username != null) binding.etSmbUsername.setText(username)
                if (password != null) binding.etSmbPassword.setText(password)
                if (domain != null) binding.etSmbDomain.setText(domain)
                binding.etSmbPinCode.setText(resource.accessPin.orEmpty())

                binding.etSmbPort.setText(R.string.default_smb_port)

                // Pre-fill comment
                binding.etSmbComment.setText(resource.comment ?: "")

                // Pre-fill scan subdirectories
                binding.cbSmbScanSubdirectories.isChecked = resource.scanSubdirectories

                // Pre-fill all files mode
                binding.cbSmbAllFiles.isChecked = resource.allFiles

                // Pre-fill remember file list
                binding.cbSmbRememberFileList.isChecked = resource.rememberFileList

                // Pre-fill show subfolders as items and disable thumbnails - were added to create
                // form as part of option-parity fix; must be restored from source resource on copy
                binding.cbSmbShowSubfoldersAsItems.isChecked = resource.showSubfoldersAsItems
                binding.cbSmbDisableThumbnails.isChecked = resource.disableThumbnails

                // Pre-fill supported media types
                binding.cbSmbSupportImage.isChecked = com.sza.fastmediasorter.domain.model.MediaType.IMAGE in resource.supportedMediaTypes
                binding.cbSmbSupportVideo.isChecked = com.sza.fastmediasorter.domain.model.MediaType.VIDEO in resource.supportedMediaTypes
                binding.cbSmbSupportAudio.isChecked = com.sza.fastmediasorter.domain.model.MediaType.AUDIO in resource.supportedMediaTypes
                binding.cbSmbSupportGif.isChecked = com.sza.fastmediasorter.domain.model.MediaType.GIF in resource.supportedMediaTypes
                binding.cbSmbSupportText.isChecked = com.sza.fastmediasorter.domain.model.MediaType.TEXT in resource.supportedMediaTypes
                binding.cbSmbSupportPdf.isChecked = com.sza.fastmediasorter.domain.model.MediaType.PDF in resource.supportedMediaTypes
                binding.cbSmbSupportEpub.isChecked = com.sza.fastmediasorter.domain.model.MediaType.EPUB in resource.supportedMediaTypes

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
                    binding.etSftpHost.setText(hostPort[0])
                    if (hostPort.size > 1) {
                        binding.etSftpPort.setText(hostPort[1])
                    } else {
                        binding.etSftpPort.setText(R.string.default_sftp_port)
                    }
                }

                if (hostAndPath.size > 1) {
                    binding.etSftpPath.setText(activity.getString(R.string.path_format, hostAndPath[1]))
                }

                binding.rbSftp.isChecked = true

                // Pre-fill credentials
                if (username != null) binding.etSftpUsername.setText(username)
                binding.etSftpPinCode.setText(resource.accessPin.orEmpty())
                // S0046: prefill the pinned host-key fingerprint; clearing it on save reverts to permissive mode.
                binding.etSftpHostKeyFingerprint.setText(resource.hostKeyFingerprint.orEmpty())
                // Reveal the optional security block when a fingerprint is already pinned so the saved value is visible on edit.
                // notify=false keeps this transient and avoids persisting the expanded state for future new resources.
                if (!resource.hostKeyFingerprint.isNullOrBlank()) {
                    binding.headerSftpServerVerification.setExpanded(true, notify = false)
                    binding.contentSftpServerVerification.visibility = android.view.View.VISIBLE
                }

                if (sshKey != null) {
                    binding.rbSftpSshKey.isChecked = true
                    binding.etSftpPrivateKey.setText(sshKey)
                    if (sshPassphrase != null) binding.etSftpKeyPassphrase.setText(sshPassphrase)
                } else {
                    binding.rbSftpPassword.isChecked = true
                    if (password != null) binding.etSftpPassword.setText(password)
                }

                // Pre-fill comment
                binding.etSftpComment.setText(resource.comment ?: "")

                // Pre-fill scan subdirectories
                binding.cbSftpScanSubdirectories.isChecked = resource.scanSubdirectories

                // Pre-fill all files mode
                binding.cbSftpAllFiles.isChecked = resource.allFiles

                // Pre-fill remember file list
                binding.cbSftpRememberFileList.isChecked = resource.rememberFileList

                // Pre-fill show subfolders as items and disable thumbnails - option-parity fields
                binding.cbSftpShowSubfoldersAsItems.isChecked = resource.showSubfoldersAsItems
                binding.cbSftpDisableThumbnails.isChecked = resource.disableThumbnails

                // Pre-fill supported media types
                binding.cbSftpSupportImage.isChecked = com.sza.fastmediasorter.domain.model.MediaType.IMAGE in resource.supportedMediaTypes
                binding.cbSftpSupportVideo.isChecked = com.sza.fastmediasorter.domain.model.MediaType.VIDEO in resource.supportedMediaTypes
                binding.cbSftpSupportAudio.isChecked = com.sza.fastmediasorter.domain.model.MediaType.AUDIO in resource.supportedMediaTypes
                binding.cbSftpSupportGif.isChecked = com.sza.fastmediasorter.domain.model.MediaType.GIF in resource.supportedMediaTypes
                binding.cbSftpSupportText.isChecked = com.sza.fastmediasorter.domain.model.MediaType.TEXT in resource.supportedMediaTypes
                binding.cbSftpSupportPdf.isChecked = com.sza.fastmediasorter.domain.model.MediaType.PDF in resource.supportedMediaTypes
                binding.cbSftpSupportEpub.isChecked = com.sza.fastmediasorter.domain.model.MediaType.EPUB in resource.supportedMediaTypes

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
                    binding.etSftpHost.setText(hostPort[0])
                    if (hostPort.size > 1) {
                        binding.etSftpPort.setText(hostPort[1])
                    } else {
                        binding.etSftpPort.setText(R.string.default_ftp_port)
                    }
                }

                if (hostAndPath.size > 1) {
                    binding.etSftpPath.setText(activity.getString(R.string.path_format, hostAndPath[1]))
                }

                binding.rbFtp.isChecked = true

                // Pre-fill credentials
                if (username != null) binding.etSftpUsername.setText(username)
                if (password != null) binding.etSftpPassword.setText(password)
                binding.etSftpPinCode.setText(resource.accessPin.orEmpty())

                // Pre-fill comment
                binding.etSftpComment.setText(resource.comment ?: "")

                // Pre-fill scan subdirectories
                binding.cbSftpScanSubdirectories.isChecked = resource.scanSubdirectories

                // Pre-fill all files mode
                binding.cbSftpAllFiles.isChecked = resource.allFiles

                // Pre-fill remember file list
                binding.cbSftpRememberFileList.isChecked = resource.rememberFileList

                // Pre-fill show subfolders as items and disable thumbnails - option-parity fields
                binding.cbSftpShowSubfoldersAsItems.isChecked = resource.showSubfoldersAsItems
                binding.cbSftpDisableThumbnails.isChecked = resource.disableThumbnails

                // Pre-fill supported media types
                binding.cbSftpSupportImage.isChecked = com.sza.fastmediasorter.domain.model.MediaType.IMAGE in resource.supportedMediaTypes
                binding.cbSftpSupportVideo.isChecked = com.sza.fastmediasorter.domain.model.MediaType.VIDEO in resource.supportedMediaTypes
                binding.cbSftpSupportAudio.isChecked = com.sza.fastmediasorter.domain.model.MediaType.AUDIO in resource.supportedMediaTypes
                binding.cbSftpSupportGif.isChecked = com.sza.fastmediasorter.domain.model.MediaType.GIF in resource.supportedMediaTypes
                binding.cbSftpSupportText.isChecked = com.sza.fastmediasorter.domain.model.MediaType.TEXT in resource.supportedMediaTypes
                binding.cbSftpSupportPdf.isChecked = com.sza.fastmediasorter.domain.model.MediaType.PDF in resource.supportedMediaTypes
                binding.cbSftpSupportEpub.isChecked = com.sza.fastmediasorter.domain.model.MediaType.EPUB in resource.supportedMediaTypes

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
