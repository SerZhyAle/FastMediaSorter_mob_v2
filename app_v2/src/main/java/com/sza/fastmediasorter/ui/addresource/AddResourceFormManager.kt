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
import com.sza.fastmediasorter.databinding.ViewAddResourceLocalBinding
import com.sza.fastmediasorter.databinding.ViewAddResourceSftpBinding
import com.sza.fastmediasorter.databinding.ViewAddResourceSmbBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.mediaPreset
import com.sza.fastmediasorter.ui.common.ResourceProfileDialog
import com.sza.fastmediasorter.ui.common.installTextInputTapFocusBridge
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.ui.common.widget.FormCheckboxRow
import com.sza.fastmediasorter.utils.NetworkUtils
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import kotlinx.coroutines.launch

internal class AddResourceFormManager(
    private val activity: AddResourceActivity,
    private val binding: ActivityAddResourceBinding,
    private val viewModel: AddResourceViewModel,
    private val remoteSourceGate: RemoteSourceAvailabilityGate,
    private val mediaCapabilities: MediaCapabilities
) {

    // S1519: lazy ViewStub-backed form bindings owned by the activity (inflate on first access).
    private val smbForm get() = activity.forms.smb
    private val sftpForm get() = activity.forms.sftp

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
                binding.toolbarContainer.paddingLeft,
                safeStatusBarHeight,
                binding.toolbarContainer.paddingRight,
                binding.toolbarContainer.paddingBottom
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
        // S1861: the paired-watch card exists only where the Wear companion is compiled in. The
        // scanner answers that instead of a BuildConfig flag, which Rule 14 forbids in src/main.
        binding.cardPairedWatch.isVisible = viewModel.isPairedWatchAvailable
    }

    // S3735: the three wire* functions run from the form's first inflation, never from the host's
    // view setup - touching a form there inflates its ViewStub on every open.

    fun wireLocalForm(local: ViewAddResourceLocalBinding) {
        local.cbLocalReadOnlyMode.setOnCheckedChangeListener { isChecked ->
            if (isChecked) local.cbLocalAddToDestinations.isChecked = false
            local.cbLocalAddToDestinations.isEnabled = !isChecked
        }
    }

    fun wireSmbForm(smb: ViewAddResourceSmbBinding) {
        val showOfficeDocuments = supportsOfficeDocuments()
        smb.cbSmbSupportEpub.isVisible = mediaCapabilities.supportsEpub
        smb.cbSmbSupportOffice.isVisible = showOfficeDocuments
        smb.cbSmbSupportVideo.isVisible = mediaCapabilities.supportsVideo
        smb.cbSmbSupportAudio.isVisible = mediaCapabilities.supportsAudio
        smb.cbSmbSupportPdf.isVisible = mediaCapabilities.supportsDocuments
        smb.cbSmbSupportText.isVisible = mediaCapabilities.supportsDocuments

        smb.cbSmbReadOnlyMode.setOnCheckedChangeListener { isChecked ->
            if (isChecked) smb.cbSmbAddToDestinations.isChecked = false
            smb.cbSmbAddToDestinations.isEnabled = !isChecked
        }
        smb.cbSmbAllFiles.setOnCheckedChangeListener { isChecked ->
            updateMediaTypeCheckboxes(
                isChecked,
                smb.cbSmbSupportImage, smb.cbSmbSupportVideo, smb.cbSmbSupportAudio,
                smb.cbSmbSupportGif, smb.cbSmbSupportText, smb.cbSmbSupportPdf,
                smb.cbSmbSupportEpub, smb.cbSmbSupportOffice
            )
        }

        installTextInputTapFocusBridge(activity, smb.tilSmbServer, smb.etSmbServer)
        installTextInputTapFocusBridge(activity, smb.tilSmbUsername, smb.etSmbUsername)
        installTextInputTapFocusBridge(activity, smb.tilSmbPassword, smb.etSmbPassword)
        installTextInputTapFocusBridge(activity, smb.tilSmbShareName, smb.etSmbShareName)
        installTextInputTapFocusBridge(activity, smb.tilSmbResourceName, smb.etSmbResourceName)
        installTextInputTapFocusBridge(activity, smb.tilSmbPinCode, smb.etSmbPinCode)

        // Keys keep the type discriminator; orientation is dropped (the consolidated store is orientation-agnostic).
        sectionsManager.register(smb.headerSmbConditions, smb.contentSmbConditions, "add_resource__smb__conditions")
        sectionsManager.register(smb.headerSmbMediaTypes, smb.contentSmbMediaTypes, "add_resource__smb__media_types")
        sectionsManager.register(smb.headerSmbAdditional, smb.contentSmbAdditional, "add_resource__smb__additional")
    }

    fun wireSftpForm(sftp: ViewAddResourceSftpBinding) {
        val showOfficeDocuments = supportsOfficeDocuments()
        sftp.cbSftpSupportEpub.isVisible = mediaCapabilities.supportsEpub
        sftp.cbSftpSupportOffice.isVisible = showOfficeDocuments
        sftp.cbSftpSupportVideo.isVisible = mediaCapabilities.supportsVideo
        sftp.cbSftpSupportAudio.isVisible = mediaCapabilities.supportsAudio
        sftp.cbSftpSupportPdf.isVisible = mediaCapabilities.supportsDocuments
        sftp.cbSftpSupportText.isVisible = mediaCapabilities.supportsDocuments

        sftp.cbSftpReadOnlyMode.setOnCheckedChangeListener { isChecked ->
            if (isChecked) sftp.cbSftpAddToDestinations.isChecked = false
            sftp.cbSftpAddToDestinations.isEnabled = !isChecked
        }
        sftp.cbSftpAllFiles.setOnCheckedChangeListener { isChecked ->
            updateMediaTypeCheckboxes(
                isChecked,
                sftp.cbSftpSupportImage, sftp.cbSftpSupportVideo, sftp.cbSftpSupportAudio,
                sftp.cbSftpSupportGif, sftp.cbSftpSupportText, sftp.cbSftpSupportPdf,
                sftp.cbSftpSupportEpub, sftp.cbSftpSupportOffice
            )
        }

        installTextInputTapFocusBridge(activity, sftp.tilSftpHost, sftp.etSftpHost)
        installTextInputTapFocusBridge(activity, sftp.tilSftpPort, sftp.etSftpPort)
        installTextInputTapFocusBridge(activity, sftp.tilSftpUsername, sftp.etSftpUsername)
        installTextInputTapFocusBridge(activity, sftp.tilSftpPassword, sftp.etSftpPassword)
        installTextInputTapFocusBridge(activity, sftp.tilSftpPath, sftp.etSftpPath)
        installTextInputTapFocusBridge(activity, sftp.tilSftpResourceName, sftp.etSftpResourceName)
        installTextInputTapFocusBridge(activity, sftp.tilSftpPinCode, sftp.etSftpPinCode)
        installTextInputTapFocusBridge(activity, sftp.tilSftpHostKeyFingerprint, sftp.etSftpHostKeyFingerprint)

        sectionsManager.register(
            sftp.headerSftpServerVerification,
            sftp.contentSftpServerVerification,
            "add_resource__sftp__server_verification"
        )
        sectionsManager.register(
            sftp.headerSftpConditions,
            sftp.contentSftpConditions,
            "add_resource__sftp__conditions"
        )
        sectionsManager.register(
            sftp.headerSftpMediaTypes,
            sftp.contentSftpMediaTypes,
            "add_resource__sftp__media_types"
        )
        sectionsManager.register(
            sftp.headerSftpAdditional,
            sftp.contentSftpAdditional,
            "add_resource__sftp__additional"
        )
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
            smbForm.etSmbServer.setText(subnet)
            smbForm.etSmbServer.setSelection(subnet.length)
        }
    }

    // ========== Media Type Init (called from showSmbFolderOptions / showSftpFolderOptions) ==========

    /**
     * The defaults land asynchronously, so a caller that must win over them (the copy prefill)
     * passes [afterDefaults]; writing its values synchronously would be overwritten on resume.
     */
    fun initSmbMediaTypes(afterDefaults: (() -> Unit)? = null) {
        activity.lifecycleScope.launch {
            val supportedTypes = viewModel.getSupportedMediaTypes()
            smbForm.cbSmbAllFiles.isChecked = false
            applyMediaTypeCheckboxes(supportedTypes, smb = true)
            smbForm.cbSmbRememberFileList.isChecked = viewModel.getSettings().defaultRememberFileList
            afterDefaults?.invoke()
        }
    }

    fun initSftpMediaTypes(afterDefaults: (() -> Unit)? = null) {
        activity.lifecycleScope.launch {
            val supportedTypes = viewModel.getSupportedMediaTypes()
            sftpForm.cbSftpAllFiles.isChecked = false
            applyMediaTypeCheckboxes(supportedTypes, smb = false)
            sftpForm.cbSftpRememberFileList.isChecked = viewModel.getSettings().defaultRememberFileList
            afterDefaults?.invoke()
        }
    }

    private fun applyMediaTypeCheckboxes(supportedTypes: Set<MediaType>, smb: Boolean) {
        if (smb) {
            smbForm.cbSmbSupportImage.applySupport(MediaType.IMAGE in supportedTypes, mediaCapabilities.supportsImages)
            smbForm.cbSmbSupportVideo.applySupport(MediaType.VIDEO in supportedTypes, mediaCapabilities.supportsVideo)
            smbForm.cbSmbSupportAudio.applySupport(MediaType.AUDIO in supportedTypes, mediaCapabilities.supportsAudio)
            smbForm.cbSmbSupportGif.applySupport(MediaType.GIF in supportedTypes, mediaCapabilities.supportsImages)
            smbForm.cbSmbSupportText.applySupport(MediaType.TEXT in supportedTypes, mediaCapabilities.supportsDocuments)
            smbForm.cbSmbSupportPdf.applySupport(MediaType.PDF in supportedTypes, mediaCapabilities.supportsDocuments)
            smbForm.cbSmbSupportEpub.applySupport(MediaType.EPUB in supportedTypes, mediaCapabilities.supportsEpub)
            smbForm.cbSmbSupportOffice
                .applySupport(MediaType.OFFICE_DOCUMENT in supportedTypes, supportsOfficeDocuments())
        } else {
            sftpForm.cbSftpSupportImage
                .applySupport(MediaType.IMAGE in supportedTypes, mediaCapabilities.supportsImages)
            sftpForm.cbSftpSupportVideo.applySupport(MediaType.VIDEO in supportedTypes, mediaCapabilities.supportsVideo)
            sftpForm.cbSftpSupportAudio.applySupport(MediaType.AUDIO in supportedTypes, mediaCapabilities.supportsAudio)
            sftpForm.cbSftpSupportGif.applySupport(MediaType.GIF in supportedTypes, mediaCapabilities.supportsImages)
            sftpForm.cbSftpSupportText
                .applySupport(MediaType.TEXT in supportedTypes, mediaCapabilities.supportsDocuments)
            sftpForm.cbSftpSupportPdf.applySupport(MediaType.PDF in supportedTypes, mediaCapabilities.supportsDocuments)
            sftpForm.cbSftpSupportEpub.applySupport(MediaType.EPUB in supportedTypes, mediaCapabilities.supportsEpub)
            sftpForm.cbSftpSupportOffice
                .applySupport(MediaType.OFFICE_DOCUMENT in supportedTypes, supportsOfficeDocuments())
        }
    }

    // ========== Profile Presets ==========

    fun showProfilePresetDialog(isSmb: Boolean) {
        // S1003: one shared dialog + one domain preset for both forms and the resource editor.
        val current = if (isSmb) smbProfilePreset else sftpProfilePreset
        ResourceProfileDialog.show(activity, current) { selected ->
            if (isSmb) {
                smbProfilePreset = selected
                smbForm.btnSmbProfilePreset.text = activity.getString(ResourceProfileDialog.labelResId(selected))
                applyProfilePreset(selected, smbForm.cbSmbAllFiles, smbForm.cbSmbRememberFileList, smbTypeCheckboxes())
            } else {
                sftpProfilePreset = selected
                sftpForm.btnSftpProfilePreset.text = activity.getString(ResourceProfileDialog.labelResId(selected))
                applyProfilePreset(
                    selected,
                    sftpForm.cbSftpAllFiles,
                    sftpForm.cbSftpRememberFileList,
                    sftpTypeCheckboxes()
                )
            }
        }
    }

    private fun smbTypeCheckboxes(): Map<MediaType, MaterialCheckBox> = mapOf(
        MediaType.IMAGE to smbForm.cbSmbSupportImage,
        MediaType.VIDEO to smbForm.cbSmbSupportVideo,
        MediaType.AUDIO to smbForm.cbSmbSupportAudio,
        MediaType.GIF to smbForm.cbSmbSupportGif,
        MediaType.TEXT to smbForm.cbSmbSupportText,
        MediaType.PDF to smbForm.cbSmbSupportPdf,
        MediaType.EPUB to smbForm.cbSmbSupportEpub,
        MediaType.OFFICE_DOCUMENT to smbForm.cbSmbSupportOffice
    )

    private fun sftpTypeCheckboxes(): Map<MediaType, MaterialCheckBox> = mapOf(
        MediaType.IMAGE to sftpForm.cbSftpSupportImage,
        MediaType.VIDEO to sftpForm.cbSftpSupportVideo,
        MediaType.AUDIO to sftpForm.cbSftpSupportAudio,
        MediaType.GIF to sftpForm.cbSftpSupportGif,
        MediaType.TEXT to sftpForm.cbSftpSupportText,
        MediaType.PDF to sftpForm.cbSftpSupportPdf,
        MediaType.EPUB to sftpForm.cbSftpSupportEpub,
        MediaType.OFFICE_DOCUMENT to sftpForm.cbSftpSupportOffice
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
        timber.log.Timber.d("S3736: manual SMB add port=${smbForm.etSmbPort.text}")
        viewModel.addSmbResourceManually(
            server = smbForm.etSmbServer.text.toString().trim().substringBefore(':'),
            shareName = smbForm.etSmbShareName.text.toString(),
            username = smbForm.etSmbUsername.text.toString(),
            password = smbForm.etSmbPassword.text.toString(),
            domain = smbForm.etSmbDomain.text?.toString()?.trim().orEmpty(),
            port = smbForm.etSmbPort.text?.toString()?.trim()?.toIntOrNull()
                ?: AddResourceActivity.DEFAULT_SMB_PORT,
            resourceName = smbForm.etSmbResourceName.text.toString().takeIf { it.isNotBlank() },
            comment = smbForm.etSmbComment.text.toString().takeIf { it.isNotBlank() },
            addToDestinations = smbForm.cbSmbAddToDestinations.isChecked,
            supportedTypes = getSmbSupportedTypes(),
            isReadOnly = isReadOnly,
            allFiles = smbForm.cbSmbAllFiles.isChecked,
            scanSubdirectories = smbForm.cbSmbScanSubdirectories.isChecked,
            rememberFileList = smbForm.cbSmbRememberFileList.isChecked,
            disableThumbnails = smbForm.cbSmbDisableThumbnails.isChecked,
            showSubfoldersAsItems = smbForm.cbSmbShowSubfoldersAsItems.isChecked,
            accessPin = smbForm.etSmbPinCode.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
            profile = smbProfilePreset
        )
    }

    fun addSftpResource() {
        val protocolType = getSelectedProtocol()
        val host = sftpForm.etSftpHost.text.toString().trim()

        if (!sftpForm.etSftpHost.isValid()) {
            Toast.makeText(activity, activity.getString(R.string.invalid_host_address), Toast.LENGTH_SHORT).show()
            sftpForm.etSftpHost.requestFocus()
            return
        }
        if (host.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.host_required), Toast.LENGTH_SHORT).show()
            return
        }

        val supportedTypes = getSftpSupportedTypes()
        if (supportedTypes.isEmpty()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.at_least_one_media_type_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val defaultPort = if (protocolType == ResourceType.SFTP) 22 else 21
        val port = sftpForm.etSftpPort.text.toString().trim().toIntOrNull() ?: defaultPort
        val username = sftpForm.etSftpUsername.text.toString().trim()
        val remotePath = sftpForm.etSftpPath.getNormalizedPath().ifEmpty { "/" }
        val resourceName = sftpForm.etSftpResourceName.text.toString().trim()
        val comment = sftpForm.etSftpComment.text.toString().trim()
        val accessPin = sftpForm.etSftpPinCode.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
        val hostKeyFingerprint = sftpForm.etSftpHostKeyFingerprint.text.toString().trim().ifEmpty { null }
        val commonParams = Triple(supportedTypes, accessPin, sftpProfilePreset)

        if (protocolType == ResourceType.SFTP && sftpForm.rbSftpSshKey.isChecked) {
            val privateKey = sftpForm.etSftpPrivateKey.text.toString().trim()
            if (privateKey.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.ssh_key_required), Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.addSftpResourceWithKey(
                host = host, port = port, username = username,
                privateKey = privateKey,
                keyPassphrase = sftpForm.etSftpKeyPassphrase.text.toString().trim().ifEmpty { null },
                remotePath = remotePath, resourceName = resourceName, comment = comment,
                supportedTypes = commonParams.first,
                allFiles = sftpForm.cbSftpAllFiles.isChecked,
                scanSubdirectories = sftpForm.cbSftpScanSubdirectories.isChecked,
                addToDestinations = sftpForm.cbSftpAddToDestinations.isChecked,
                isReadOnly = sftpForm.cbSftpReadOnlyMode.isChecked,
                rememberFileList = sftpForm.cbSftpRememberFileList.isChecked,
                disableThumbnails = sftpForm.cbSftpDisableThumbnails.isChecked,
                showSubfoldersAsItems = sftpForm.cbSftpShowSubfoldersAsItems.isChecked,
                accessPin = commonParams.second, profile = commonParams.third,
                hostKeyFingerprint = hostKeyFingerprint
            )
        } else {
            viewModel.addSftpFtpResource(
                protocolType = protocolType, host = host, port = port, username = username,
                password = sftpForm.etSftpPassword.text.toString().trim(),
                remotePath = remotePath, resourceName = resourceName, comment = comment,
                supportedTypes = commonParams.first,
                allFiles = sftpForm.cbSftpAllFiles.isChecked,
                scanSubdirectories = sftpForm.cbSftpScanSubdirectories.isChecked,
                addToDestinations = sftpForm.cbSftpAddToDestinations.isChecked,
                isReadOnly = sftpForm.cbSftpReadOnlyMode.isChecked,
                rememberFileList = sftpForm.cbSftpRememberFileList.isChecked,
                disableThumbnails = sftpForm.cbSftpDisableThumbnails.isChecked,
                showSubfoldersAsItems = sftpForm.cbSftpShowSubfoldersAsItems.isChecked,
                accessPin = commonParams.second, profile = commonParams.third,
                hostKeyFingerprint = hostKeyFingerprint
            )
        }
    }

    fun getSelectedProtocol(): ResourceType = when (sftpForm.rgProtocol.checkedRadioButtonId) {
        sftpForm.rbSftp.id -> ResourceType.SFTP
        sftpForm.rbFtp.id -> ResourceType.FTP
        else -> ResourceType.SFTP
    }

    private fun getSmbSupportedTypes(): Set<MediaType> = buildSet {
        if (smbForm.cbSmbSupportImage.isChecked) add(MediaType.IMAGE)
        if (smbForm.cbSmbSupportVideo.isChecked) add(MediaType.VIDEO)
        if (smbForm.cbSmbSupportAudio.isChecked) add(MediaType.AUDIO)
        if (smbForm.cbSmbSupportGif.isChecked) add(MediaType.GIF)
        if (smbForm.cbSmbSupportText.isChecked) add(MediaType.TEXT)
        if (smbForm.cbSmbSupportPdf.isChecked) add(MediaType.PDF)
        if (smbForm.cbSmbSupportEpub.isChecked) add(MediaType.EPUB)
        if (smbForm.cbSmbSupportOffice.isChecked) add(MediaType.OFFICE_DOCUMENT)
    }

    private fun getSftpSupportedTypes(): Set<MediaType> = buildSet {
        if (sftpForm.cbSftpSupportImage.isChecked) add(MediaType.IMAGE)
        if (sftpForm.cbSftpSupportVideo.isChecked) add(MediaType.VIDEO)
        if (sftpForm.cbSftpSupportAudio.isChecked) add(MediaType.AUDIO)
        if (sftpForm.cbSftpSupportGif.isChecked) add(MediaType.GIF)
        if (sftpForm.cbSftpSupportText.isChecked) add(MediaType.TEXT)
        if (sftpForm.cbSftpSupportPdf.isChecked) add(MediaType.PDF)
        if (sftpForm.cbSftpSupportEpub.isChecked) add(MediaType.EPUB)
        if (sftpForm.cbSftpSupportOffice.isChecked) add(MediaType.OFFICE_DOCUMENT)
    }

    private fun supportsOfficeDocuments(): Boolean =
        mediaCapabilities.supportsDocuments && MediaTypeUtils.OFFICE_DOCUMENT_EXTENSIONS.isNotEmpty()

    /** S1519 formatting split: checked/enabled/visible triplet shared by all media-type checkboxes. */
    private fun MaterialCheckBox.applySupport(checked: Boolean, capabilityVisible: Boolean) {
        isChecked = checked
        isEnabled = true
        isVisible = capabilityVisible && checked
    }
}
