package com.sza.fastmediasorter.ui.addresource

import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.capability.RemoteSourceId
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.mediaPreset
import com.sza.fastmediasorter.ui.common.ResourceProfileDialog
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.ui.common.widget.FormCheckboxRow
import com.sza.fastmediasorter.ui.common.installTextInputTapFocusBridge
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import com.sza.fastmediasorter.utils.NetworkUtils
import kotlinx.coroutines.launch
import timber.log.Timber

internal class AddResourceFormManager(
    private val activity: AddResourceActivity,
    private val binding: ActivityAddResourceBinding,
    private val viewModel: AddResourceViewModel,
    private val remoteSourceGate: RemoteSourceAvailabilityGate,
    private val mediaCapabilities: MediaCapabilities
) {

    // S0535: section state goes through the unified orchestrator + consolidated store (StrictMode-wrapped).
    private val sectionsManager by lazy { CollapsibleSectionsManager(activity) }

    var smbProfilePreset: ResourceProfile = ResourceProfile.NONE
        private set
    var sftpProfilePreset: ResourceProfile = ResourceProfile.NONE
        private set

    // ========== UI Setup ==========

    fun applyEdgeToEdgeInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val safeStatusBarHeight = insets.getStatusBarHeightSafe(activity.resources)
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            // Apply the inset to the outer container so the toolbar row keeps its full height in landscape.
            binding.toolbarContainer.setPadding(
                binding.toolbarContainer.paddingLeft, safeStatusBarHeight,
                binding.toolbarContainer.paddingRight, binding.toolbarContainer.paddingBottom
            )
            binding.root.setPadding(0, 0, 0, navBar.bottom)
            insets
        }
        // Force re-dispatch since listener was registered after initial insets
        androidx.core.view.ViewCompat.requestApplyInsets(binding.root)
    }

    fun updateResourceTypeGridColumns() {
        binding.layoutResourceTypes.columnCount =
            if (activity.resources.configuration.isWideLayout()) 2 else 1
    }

    fun applyFlavorRestrictions() {
        // S0391: remote type cards are gated by the availability node (compile support AND user toggle).
        binding.cardNetworkFolder.isVisible = remoteSourceGate.isEnabled(RemoteSourceId.SMB)
        binding.cardSftpFolder.isVisible =
            remoteSourceGate.isEnabled(RemoteSourceId.SFTP) || remoteSourceGate.isEnabled(RemoteSourceId.FTP)
        binding.cardCloudStorage.isVisible = remoteSourceGate.anyCloudEnabled()
        val showEpub = mediaCapabilities.supportsEpub
        val showOfficeDocuments = supportsOfficeDocuments()
        binding.cbSmbSupportEpub.isVisible = showEpub
        binding.cbSftpSupportEpub.isVisible = showEpub
        binding.cbSmbSupportOffice.isVisible = showOfficeDocuments
        binding.cbSftpSupportOffice.isVisible = showOfficeDocuments
        binding.cbSmbSupportVideo.isVisible = mediaCapabilities.supportsVideo
        binding.cbSftpSupportVideo.isVisible = mediaCapabilities.supportsVideo
        binding.cbSmbSupportAudio.isVisible = mediaCapabilities.supportsAudio
        binding.cbSftpSupportAudio.isVisible = mediaCapabilities.supportsAudio
        binding.cbSmbSupportPdf.isVisible = mediaCapabilities.supportsDocuments
        binding.cbSftpSupportPdf.isVisible = mediaCapabilities.supportsDocuments
        binding.cbSmbSupportText.isVisible = mediaCapabilities.supportsDocuments
        binding.cbSftpSupportText.isVisible = mediaCapabilities.supportsDocuments
    }

    fun setupCheckboxInteractions() {
        binding.cbLocalReadOnlyMode.setOnCheckedChangeListener { isChecked ->
            binding.cbLocalAddToDestinations.isChecked = if (isChecked) false else binding.cbLocalAddToDestinations.isChecked
            binding.cbLocalAddToDestinations.isEnabled = !isChecked
        }
        binding.cbSmbReadOnlyMode.setOnCheckedChangeListener { isChecked ->
            binding.cbSmbAddToDestinations.isChecked = if (isChecked) false else binding.cbSmbAddToDestinations.isChecked
            binding.cbSmbAddToDestinations.isEnabled = !isChecked
        }
        binding.cbSftpReadOnlyMode.setOnCheckedChangeListener { isChecked ->
            binding.cbSftpAddToDestinations.isChecked = if (isChecked) false else binding.cbSftpAddToDestinations.isChecked
            binding.cbSftpAddToDestinations.isEnabled = !isChecked
        }
        binding.cbSmbAllFiles.setOnCheckedChangeListener { isChecked ->
            updateMediaTypeCheckboxes(isChecked,
                binding.cbSmbSupportImage, binding.cbSmbSupportVideo, binding.cbSmbSupportAudio,
                binding.cbSmbSupportGif, binding.cbSmbSupportText, binding.cbSmbSupportPdf,
                binding.cbSmbSupportEpub, binding.cbSmbSupportOffice)
        }
        binding.cbSftpAllFiles.setOnCheckedChangeListener { isChecked ->
            updateMediaTypeCheckboxes(isChecked,
                binding.cbSftpSupportImage, binding.cbSftpSupportVideo, binding.cbSftpSupportAudio,
                binding.cbSftpSupportGif, binding.cbSftpSupportText, binding.cbSftpSupportPdf,
                binding.cbSftpSupportEpub, binding.cbSftpSupportOffice)
        }
    }

    fun setupTextInputTapBridges() {
        installTextInputTapFocusBridge(activity, binding.tilSmbServer, binding.etSmbServer)
        installTextInputTapFocusBridge(activity, binding.tilSmbUsername, binding.etSmbUsername)
        installTextInputTapFocusBridge(activity, binding.tilSmbPassword, binding.etSmbPassword)
        installTextInputTapFocusBridge(activity, binding.tilSmbShareName, binding.etSmbShareName)
        installTextInputTapFocusBridge(activity, binding.tilSmbResourceName, binding.etSmbResourceName)
        installTextInputTapFocusBridge(activity, binding.tilSmbPinCode, binding.etSmbPinCode)

        installTextInputTapFocusBridge(activity, binding.tilSftpHost, binding.etSftpHost)
        installTextInputTapFocusBridge(activity, binding.tilSftpPort, binding.etSftpPort)
        installTextInputTapFocusBridge(activity, binding.tilSftpUsername, binding.etSftpUsername)
        installTextInputTapFocusBridge(activity, binding.tilSftpPassword, binding.etSftpPassword)
        installTextInputTapFocusBridge(activity, binding.tilSftpPath, binding.etSftpPath)
        installTextInputTapFocusBridge(activity, binding.tilSftpResourceName, binding.etSftpResourceName)
        installTextInputTapFocusBridge(activity, binding.tilSftpPinCode, binding.etSftpPinCode)
        installTextInputTapFocusBridge(activity, binding.tilSftpHostKeyFingerprint, binding.etSftpHostKeyFingerprint)
    }

    private fun updateMediaTypeCheckboxes(
        allFilesEnabled: Boolean,
        vararg checkboxes: com.google.android.material.checkbox.MaterialCheckBox
    ) {
        checkboxes.forEach { cb ->
            cb.isChecked = allFilesEnabled || cb.isChecked
            cb.isEnabled = !allFilesEnabled
        }
    }

    fun setupIpAddressField() {
        val deviceIp = NetworkUtils.getLocalIpAddress(activity)
        if (deviceIp != null) {
            val subnet = deviceIp.substringBeforeLast(".") + "."
            binding.etSmbServer.setText(subnet)
            binding.etSmbServer.setSelection(subnet.length)
        }
    }

    // ========== Collapsible Sections ==========

    fun setupCollapsibleSections() {
        // Keys keep the type discriminator; orientation is dropped (the consolidated store is orientation-agnostic).
        sectionsManager.register(binding.headerSmbConditions, binding.contentSmbConditions, "add_resource__smb__conditions")
        sectionsManager.register(binding.headerSmbMediaTypes, binding.contentSmbMediaTypes, "add_resource__smb__media_types")
        sectionsManager.register(binding.headerSmbAdditional, binding.contentSmbAdditional, "add_resource__smb__additional")
        sectionsManager.register(binding.headerSftpServerVerification, binding.contentSftpServerVerification, "add_resource__sftp__server_verification")
        sectionsManager.register(binding.headerSftpConditions, binding.contentSftpConditions, "add_resource__sftp__conditions")
        sectionsManager.register(binding.headerSftpMediaTypes, binding.contentSftpMediaTypes, "add_resource__sftp__media_types")
        sectionsManager.register(binding.headerSftpAdditional, binding.contentSftpAdditional, "add_resource__sftp__additional")
    }

    // ========== Media Type Init (called from showSmbFolderOptions / showSftpFolderOptions) ==========

    fun initSmbMediaTypes() {
        activity.lifecycleScope.launch {
            val supportedTypes = viewModel.getSupportedMediaTypes()
            binding.cbSmbAllFiles.isChecked = false
            applyMediaTypeCheckboxes(supportedTypes, smb = true)
            binding.cbSmbRememberFileList.isChecked = viewModel.getSettings().defaultRememberFileList
        }
    }

    fun initSftpMediaTypes() {
        activity.lifecycleScope.launch {
            val supportedTypes = viewModel.getSupportedMediaTypes()
            binding.cbSftpAllFiles.isChecked = false
            applyMediaTypeCheckboxes(supportedTypes, smb = false)
            binding.cbSftpRememberFileList.isChecked = viewModel.getSettings().defaultRememberFileList
        }
    }

    private fun applyMediaTypeCheckboxes(supportedTypes: Set<MediaType>, smb: Boolean) {
        if (smb) {
            binding.cbSmbSupportImage.apply { isChecked = MediaType.IMAGE in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsImages && (MediaType.IMAGE in supportedTypes) }
            binding.cbSmbSupportVideo.apply { isChecked = MediaType.VIDEO in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsVideo && (MediaType.VIDEO in supportedTypes) }
            binding.cbSmbSupportAudio.apply { isChecked = MediaType.AUDIO in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsAudio && (MediaType.AUDIO in supportedTypes) }
            binding.cbSmbSupportGif.apply   { isChecked = MediaType.GIF  in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsImages && (MediaType.GIF  in supportedTypes) }
            binding.cbSmbSupportText.apply  { isChecked = MediaType.TEXT in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsDocuments && (MediaType.TEXT in supportedTypes) }
            binding.cbSmbSupportPdf.apply   { isChecked = MediaType.PDF  in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsDocuments && (MediaType.PDF  in supportedTypes) }
            binding.cbSmbSupportEpub.apply  { isChecked = MediaType.EPUB in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsEpub && (MediaType.EPUB in supportedTypes) }
            binding.cbSmbSupportOffice.apply { isChecked = MediaType.OFFICE_DOCUMENT in supportedTypes; isEnabled = true; isVisible = supportsOfficeDocuments() && (MediaType.OFFICE_DOCUMENT in supportedTypes) }
        } else {
            binding.cbSftpSupportImage.apply { isChecked = MediaType.IMAGE in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsImages && (MediaType.IMAGE in supportedTypes) }
            binding.cbSftpSupportVideo.apply { isChecked = MediaType.VIDEO in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsVideo && (MediaType.VIDEO in supportedTypes) }
            binding.cbSftpSupportAudio.apply { isChecked = MediaType.AUDIO in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsAudio && (MediaType.AUDIO in supportedTypes) }
            binding.cbSftpSupportGif.apply   { isChecked = MediaType.GIF  in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsImages && (MediaType.GIF  in supportedTypes) }
            binding.cbSftpSupportText.apply  { isChecked = MediaType.TEXT in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsDocuments && (MediaType.TEXT in supportedTypes) }
            binding.cbSftpSupportPdf.apply   { isChecked = MediaType.PDF  in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsDocuments && (MediaType.PDF  in supportedTypes) }
            binding.cbSftpSupportEpub.apply  { isChecked = MediaType.EPUB in supportedTypes; isEnabled = true; isVisible = mediaCapabilities.supportsEpub && (MediaType.EPUB in supportedTypes) }
            binding.cbSftpSupportOffice.apply { isChecked = MediaType.OFFICE_DOCUMENT in supportedTypes; isEnabled = true; isVisible = supportsOfficeDocuments() && (MediaType.OFFICE_DOCUMENT in supportedTypes) }
        }
    }

    // ========== Profile Presets ==========

    fun showProfilePresetDialog(isSmb: Boolean) {
        // S1003: one shared dialog + one domain preset for both forms and the resource editor.
        val current = if (isSmb) smbProfilePreset else sftpProfilePreset
        ResourceProfileDialog.show(activity, current) { selected ->
            if (isSmb) {
                smbProfilePreset = selected
                binding.btnSmbProfilePreset.text = activity.getString(ResourceProfileDialog.labelResId(selected))
                applyProfilePreset(selected, binding.cbSmbAllFiles, binding.cbSmbRememberFileList, smbTypeCheckboxes())
            } else {
                sftpProfilePreset = selected
                binding.btnSftpProfilePreset.text = activity.getString(ResourceProfileDialog.labelResId(selected))
                applyProfilePreset(selected, binding.cbSftpAllFiles, binding.cbSftpRememberFileList, sftpTypeCheckboxes())
            }
        }
    }

    private fun smbTypeCheckboxes(): Map<MediaType, MaterialCheckBox> = mapOf(
        MediaType.IMAGE to binding.cbSmbSupportImage,
        MediaType.VIDEO to binding.cbSmbSupportVideo,
        MediaType.AUDIO to binding.cbSmbSupportAudio,
        MediaType.GIF to binding.cbSmbSupportGif,
        MediaType.TEXT to binding.cbSmbSupportText,
        MediaType.PDF to binding.cbSmbSupportPdf,
        MediaType.EPUB to binding.cbSmbSupportEpub,
        MediaType.OFFICE_DOCUMENT to binding.cbSmbSupportOffice
    )

    private fun sftpTypeCheckboxes(): Map<MediaType, MaterialCheckBox> = mapOf(
        MediaType.IMAGE to binding.cbSftpSupportImage,
        MediaType.VIDEO to binding.cbSftpSupportVideo,
        MediaType.AUDIO to binding.cbSftpSupportAudio,
        MediaType.GIF to binding.cbSftpSupportGif,
        MediaType.TEXT to binding.cbSftpSupportText,
        MediaType.PDF to binding.cbSftpSupportPdf,
        MediaType.EPUB to binding.cbSftpSupportEpub,
        MediaType.OFFICE_DOCUMENT to binding.cbSftpSupportOffice
    )

    /**
     * S1003: apply the domain preset ([ResourceProfile.mediaPreset], S1002) to a form's checkbox
     * block - replaces two hand-rolled per-protocol copies that had drifted from the editor
     * (create's VIDEO_LIBRARY checked video only; the domain preset is video + audio).
     * Null preset fields mean "leave unchanged" (NONE/ALL_FILES do not narrow the type set);
     * a checkbox hidden by the flavor never gets checked.
     */
    private fun applyProfilePreset(
        profile: ResourceProfile,
        allFilesBox: FormCheckboxRow,
        rememberListBox: FormCheckboxRow,
        typeBoxes: Map<MediaType, MaterialCheckBox>
    ) {
        val preset = profile.mediaPreset()
        allFilesBox.isChecked = preset.allFiles
        preset.supportedMediaTypes?.let { types ->
            typeBoxes.forEach { (type, box) -> box.isChecked = box.isVisible && type in types }
        }
        preset.rememberFileList?.let { rememberListBox.isChecked = it }
    }

    // ========== SMB / SFTP Resource Builders ==========

    fun addSmbResourceManually(isReadOnly: Boolean = false) {
        viewModel.addSmbResourceManually(
            server = binding.etSmbServer.text.toString().trim().substringBefore(':'),
            shareName = binding.etSmbShareName.text.toString(),
            username = binding.etSmbUsername.text.toString(),
            password = binding.etSmbPassword.text.toString(),
            domain = "",
            port = 445,
            resourceName = binding.etSmbResourceName.text.toString().takeIf { it.isNotBlank() },
            comment = binding.etSmbComment.text.toString().takeIf { it.isNotBlank() },
            addToDestinations = binding.cbSmbAddToDestinations.isChecked,
            supportedTypes = getSmbSupportedTypes(),
            isReadOnly = isReadOnly,
            allFiles = binding.cbSmbAllFiles.isChecked,
            scanSubdirectories = binding.cbSmbScanSubdirectories.isChecked,
            rememberFileList = binding.cbSmbRememberFileList.isChecked,
            disableThumbnails = binding.cbSmbDisableThumbnails.isChecked,
            showSubfoldersAsItems = binding.cbSmbShowSubfoldersAsItems.isChecked,
            accessPin = binding.etSmbPinCode.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
            profile = smbProfilePreset
        )
    }

    fun addSftpResource() {
        val protocolType = getSelectedProtocol()
        val host = binding.etSftpHost.text.toString().trim()

        if (!binding.etSftpHost.isValid()) {
            Toast.makeText(activity, activity.getString(R.string.invalid_host_address), Toast.LENGTH_SHORT).show()
            binding.etSftpHost.requestFocus()
            return
        }
        if (host.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.host_required), Toast.LENGTH_SHORT).show()
            return
        }

        val supportedTypes = getSftpSupportedTypes()
        if (supportedTypes.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.at_least_one_media_type_required), Toast.LENGTH_SHORT).show()
            return
        }

        val defaultPort = if (protocolType == ResourceType.SFTP) 22 else 21
        val port = binding.etSftpPort.text.toString().trim().toIntOrNull() ?: defaultPort
        val username = binding.etSftpUsername.text.toString().trim()
        val remotePath = binding.etSftpPath.getNormalizedPath().ifEmpty { "/" }
        val resourceName = binding.etSftpResourceName.text.toString().trim()
        val comment = binding.etSftpComment.text.toString().trim()
        val accessPin = binding.etSftpPinCode.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
        val hostKeyFingerprint = binding.etSftpHostKeyFingerprint.text.toString().trim().ifEmpty { null }
        val commonParams = Triple(supportedTypes, accessPin, sftpProfilePreset)

        if (protocolType == ResourceType.SFTP && binding.rbSftpSshKey.isChecked) {
            val privateKey = binding.etSftpPrivateKey.text.toString().trim()
            if (privateKey.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.ssh_key_required), Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.addSftpResourceWithKey(
                host = host, port = port, username = username,
                privateKey = privateKey,
                keyPassphrase = binding.etSftpKeyPassphrase.text.toString().trim().ifEmpty { null },
                remotePath = remotePath, resourceName = resourceName, comment = comment,
                supportedTypes = commonParams.first,
                allFiles = binding.cbSftpAllFiles.isChecked,
                scanSubdirectories = binding.cbSftpScanSubdirectories.isChecked,
                addToDestinations = binding.cbSftpAddToDestinations.isChecked,
                isReadOnly = binding.cbSftpReadOnlyMode.isChecked,
                rememberFileList = binding.cbSftpRememberFileList.isChecked,
                disableThumbnails = binding.cbSftpDisableThumbnails.isChecked,
                showSubfoldersAsItems = binding.cbSftpShowSubfoldersAsItems.isChecked,
                accessPin = commonParams.second, profile = commonParams.third,
                hostKeyFingerprint = hostKeyFingerprint
            )
        } else {
            viewModel.addSftpFtpResource(
                protocolType = protocolType, host = host, port = port, username = username,
                password = binding.etSftpPassword.text.toString().trim(),
                remotePath = remotePath, resourceName = resourceName, comment = comment,
                supportedTypes = commonParams.first,
                allFiles = binding.cbSftpAllFiles.isChecked,
                scanSubdirectories = binding.cbSftpScanSubdirectories.isChecked,
                addToDestinations = binding.cbSftpAddToDestinations.isChecked,
                isReadOnly = binding.cbSftpReadOnlyMode.isChecked,
                rememberFileList = binding.cbSftpRememberFileList.isChecked,
                disableThumbnails = binding.cbSftpDisableThumbnails.isChecked,
                showSubfoldersAsItems = binding.cbSftpShowSubfoldersAsItems.isChecked,
                accessPin = commonParams.second, profile = commonParams.third,
                hostKeyFingerprint = hostKeyFingerprint
            )
        }
    }

    fun getSelectedProtocol(): ResourceType = when (binding.rgProtocol.checkedRadioButtonId) {
        binding.rbSftp.id -> ResourceType.SFTP
        binding.rbFtp.id -> ResourceType.FTP
        else -> ResourceType.SFTP
    }

    private fun getSmbSupportedTypes(): Set<MediaType> = buildSet {
        if (binding.cbSmbSupportImage.isChecked) add(MediaType.IMAGE)
        if (binding.cbSmbSupportVideo.isChecked) add(MediaType.VIDEO)
        if (binding.cbSmbSupportAudio.isChecked) add(MediaType.AUDIO)
        if (binding.cbSmbSupportGif.isChecked)   add(MediaType.GIF)
        if (binding.cbSmbSupportText.isChecked)  add(MediaType.TEXT)
        if (binding.cbSmbSupportPdf.isChecked)   add(MediaType.PDF)
        if (binding.cbSmbSupportEpub.isChecked)  add(MediaType.EPUB)
        if (binding.cbSmbSupportOffice.isChecked) add(MediaType.OFFICE_DOCUMENT)
    }

    private fun getSftpSupportedTypes(): Set<MediaType> = buildSet {
        if (binding.cbSftpSupportImage.isChecked) add(MediaType.IMAGE)
        if (binding.cbSftpSupportVideo.isChecked) add(MediaType.VIDEO)
        if (binding.cbSftpSupportAudio.isChecked) add(MediaType.AUDIO)
        if (binding.cbSftpSupportGif.isChecked)   add(MediaType.GIF)
        if (binding.cbSftpSupportText.isChecked)  add(MediaType.TEXT)
        if (binding.cbSftpSupportPdf.isChecked)   add(MediaType.PDF)
        if (binding.cbSftpSupportEpub.isChecked)  add(MediaType.EPUB)
        if (binding.cbSftpSupportOffice.isChecked) add(MediaType.OFFICE_DOCUMENT)
    }

    private fun supportsOfficeDocuments(): Boolean =
        mediaCapabilities.supportsDocuments && MediaTypeUtils.OFFICE_DOCUMENT_EXTENSIONS.isNotEmpty()
}
