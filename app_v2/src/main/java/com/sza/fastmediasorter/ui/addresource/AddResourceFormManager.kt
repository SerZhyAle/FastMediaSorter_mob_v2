package com.sza.fastmediasorter.ui.addresource

import android.app.AlertDialog
import android.content.res.Configuration
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.common.installTextInputTapFocusBridge
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import com.sza.fastmediasorter.utils.NetworkUtils
import kotlinx.coroutines.launch
import timber.log.Timber

internal class AddResourceFormManager(
    private val activity: AddResourceActivity,
    private val binding: ActivityAddResourceBinding,
    private val viewModel: AddResourceViewModel
) {

    private val addResourceUiPrefs by lazy {
        activity.getSharedPreferences("add_resource_ui_state", android.content.Context.MODE_PRIVATE)
    }

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
            if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 2 else 1
    }

    fun applyFlavorRestrictions() {
        binding.cardCloudStorage.isVisible = BuildConfig.SUPPORT_CLOUD
        val showEpub = BuildConfig.ENABLE_EPUB
        binding.cbSmbSupportEpub.isVisible = showEpub
        binding.cbSftpSupportEpub.isVisible = showEpub
        binding.cbSmbSupportVideo.isVisible = BuildConfig.SUPPORT_VIDEO
        binding.cbSftpSupportVideo.isVisible = BuildConfig.SUPPORT_VIDEO
        binding.cbSmbSupportAudio.isVisible = BuildConfig.SUPPORT_AUDIO
        binding.cbSftpSupportAudio.isVisible = BuildConfig.SUPPORT_AUDIO
        binding.cbSmbSupportPdf.isVisible = BuildConfig.SUPPORT_DOCUMENTS
        binding.cbSftpSupportPdf.isVisible = BuildConfig.SUPPORT_DOCUMENTS
        binding.cbSmbSupportText.isVisible = BuildConfig.SUPPORT_DOCUMENTS
        binding.cbSftpSupportText.isVisible = BuildConfig.SUPPORT_DOCUMENTS
    }

    fun setupCheckboxInteractions() {
        binding.cbLocalReadOnlyMode.setOnCheckedChangeListener { _, isChecked ->
            binding.cbLocalAddToDestinations.isChecked = if (isChecked) false else binding.cbLocalAddToDestinations.isChecked
            binding.cbLocalAddToDestinations.isEnabled = !isChecked
        }
        binding.cbSmbReadOnlyMode.setOnCheckedChangeListener { _, isChecked ->
            binding.cbSmbAddToDestinations.isChecked = if (isChecked) false else binding.cbSmbAddToDestinations.isChecked
            binding.cbSmbAddToDestinations.isEnabled = !isChecked
        }
        binding.cbSftpReadOnlyMode.setOnCheckedChangeListener { _, isChecked ->
            binding.cbSftpAddToDestinations.isChecked = if (isChecked) false else binding.cbSftpAddToDestinations.isChecked
            binding.cbSftpAddToDestinations.isEnabled = !isChecked
        }
        binding.cbSmbAllFiles.setOnCheckedChangeListener { _, isChecked ->
            updateMediaTypeCheckboxes(isChecked,
                binding.cbSmbSupportImage, binding.cbSmbSupportVideo, binding.cbSmbSupportAudio,
                binding.cbSmbSupportGif, binding.cbSmbSupportText, binding.cbSmbSupportPdf, binding.cbSmbSupportEpub)
        }
        binding.cbSftpAllFiles.setOnCheckedChangeListener { _, isChecked ->
            updateMediaTypeCheckboxes(isChecked,
                binding.cbSftpSupportImage, binding.cbSftpSupportVideo, binding.cbSftpSupportAudio,
                binding.cbSftpSupportGif, binding.cbSftpSupportText, binding.cbSftpSupportPdf, binding.cbSftpSupportEpub)
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
        bindCollapsibleHeader(binding.headerSmbConditions, binding.contentSmbConditions, sectionKey("smb", "conditions"))
        bindCollapsibleHeader(binding.headerSmbMediaTypes, binding.contentSmbMediaTypes, sectionKey("smb", "media_types"))
        bindCollapsibleHeader(binding.headerSmbAdditional, binding.contentSmbAdditional, sectionKey("smb", "additional"))
        bindCollapsibleHeader(binding.headerSftpConditions, binding.contentSftpConditions, sectionKey("sftp", "conditions"))
        bindCollapsibleHeader(binding.headerSftpMediaTypes, binding.contentSftpMediaTypes, sectionKey("sftp", "media_types"))
        bindCollapsibleHeader(binding.headerSftpAdditional, binding.contentSftpAdditional, sectionKey("sftp", "additional"))
    }

    private fun sectionKey(resourceType: String, sectionId: String): String {
        val orientation = if (activity.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE) "land" else "port"
        return "add_${resourceType}_${orientation}_$sectionId"
    }

    private fun bindCollapsibleHeader(
        header: CollapsibleSectionHeader,
        content: android.view.View,
        key: String,
        defaultExpanded: Boolean = false,
    ) {
        val isExpanded = addResourceUiPrefs.getBoolean(key, defaultExpanded)
        header.setExpanded(isExpanded, notify = false)
        content.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
        header.setOnExpandedChangeListener { expanded ->
            content.visibility = if (expanded) android.view.View.VISIBLE else android.view.View.GONE
            addResourceUiPrefs.edit().putBoolean(key, expanded).apply()
        }
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
            binding.cbSmbSupportImage.apply { isChecked = MediaType.IMAGE in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_IMAGES && (MediaType.IMAGE in supportedTypes) }
            binding.cbSmbSupportVideo.apply { isChecked = MediaType.VIDEO in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_VIDEO && (MediaType.VIDEO in supportedTypes) }
            binding.cbSmbSupportAudio.apply { isChecked = MediaType.AUDIO in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_AUDIO && (MediaType.AUDIO in supportedTypes) }
            binding.cbSmbSupportGif.apply   { isChecked = MediaType.GIF  in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_IMAGES && (MediaType.GIF  in supportedTypes) }
            binding.cbSmbSupportText.apply  { isChecked = MediaType.TEXT in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_DOCUMENTS && (MediaType.TEXT in supportedTypes) }
            binding.cbSmbSupportPdf.apply   { isChecked = MediaType.PDF  in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_DOCUMENTS && (MediaType.PDF  in supportedTypes) }
            binding.cbSmbSupportEpub.apply  { isChecked = MediaType.EPUB in supportedTypes; isEnabled = true; isVisible = BuildConfig.ENABLE_EPUB && (MediaType.EPUB in supportedTypes) }
        } else {
            binding.cbSftpSupportImage.apply { isChecked = MediaType.IMAGE in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_IMAGES && (MediaType.IMAGE in supportedTypes) }
            binding.cbSftpSupportVideo.apply { isChecked = MediaType.VIDEO in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_VIDEO && (MediaType.VIDEO in supportedTypes) }
            binding.cbSftpSupportAudio.apply { isChecked = MediaType.AUDIO in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_AUDIO && (MediaType.AUDIO in supportedTypes) }
            binding.cbSftpSupportGif.apply   { isChecked = MediaType.GIF  in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_IMAGES && (MediaType.GIF  in supportedTypes) }
            binding.cbSftpSupportText.apply  { isChecked = MediaType.TEXT in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_DOCUMENTS && (MediaType.TEXT in supportedTypes) }
            binding.cbSftpSupportPdf.apply   { isChecked = MediaType.PDF  in supportedTypes; isEnabled = true; isVisible = BuildConfig.SUPPORT_DOCUMENTS && (MediaType.PDF  in supportedTypes) }
            binding.cbSftpSupportEpub.apply  { isChecked = MediaType.EPUB in supportedTypes; isEnabled = true; isVisible = BuildConfig.ENABLE_EPUB && (MediaType.EPUB in supportedTypes) }
        }
    }

    // ========== Profile Presets ==========

    fun showProfilePresetDialog(isSmb: Boolean) {
        val profiles = ResourceProfile.values()
        val labels = profiles.map { activity.getString(getProfileLabelResId(it)) }.toTypedArray()
        val current = if (isSmb) smbProfilePreset else sftpProfilePreset
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.select_resource_type))
            .setSingleChoiceItems(labels, profiles.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                val selected = profiles[which]
                if (isSmb) {
                    smbProfilePreset = selected
                    binding.btnSmbProfilePreset.text = activity.getString(getProfileLabelResId(selected))
                    applyProfilePresetToSmb(selected)
                } else {
                    sftpProfilePreset = selected
                    binding.btnSftpProfilePreset.text = activity.getString(getProfileLabelResId(selected))
                    applyProfilePresetToSftp(selected)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getProfileLabelResId(profile: ResourceProfile): Int = when (profile) {
        ResourceProfile.NONE -> R.string.label_profile_none
        ResourceProfile.AUDIO_LIBRARY -> R.string.label_profile_audio_library
        ResourceProfile.PHOTO_STORAGE -> R.string.label_profile_photo_storage
        ResourceProfile.VIDEO_LIBRARY -> R.string.label_profile_video_library
        ResourceProfile.DOCUMENTS -> R.string.label_profile_documents
        ResourceProfile.ALL_FILES -> R.string.label_profile_all_files
    }

    private fun applyProfilePresetToSmb(profile: ResourceProfile) {
        if (binding.cbSmbAllFiles.isChecked) binding.cbSmbAllFiles.isChecked = false
        when (profile) {
            ResourceProfile.AUDIO_LIBRARY -> {
                binding.cbSmbSupportAudio.isChecked  = binding.cbSmbSupportAudio.isVisible
                binding.cbSmbSupportImage.isChecked  = false; binding.cbSmbSupportVideo.isChecked = false
                binding.cbSmbSupportGif.isChecked    = false; binding.cbSmbSupportText.isChecked  = false
                binding.cbSmbSupportPdf.isChecked    = false; binding.cbSmbSupportEpub.isChecked  = false
                binding.cbSmbRememberFileList.isChecked = true
            }
            ResourceProfile.VIDEO_LIBRARY -> {
                binding.cbSmbSupportVideo.isChecked  = binding.cbSmbSupportVideo.isVisible
                binding.cbSmbSupportAudio.isChecked  = false; binding.cbSmbSupportImage.isChecked = false
                binding.cbSmbSupportGif.isChecked    = false; binding.cbSmbSupportText.isChecked  = false
                binding.cbSmbSupportPdf.isChecked    = false; binding.cbSmbSupportEpub.isChecked  = false
            }
            ResourceProfile.PHOTO_STORAGE -> {
                binding.cbSmbSupportImage.isChecked  = binding.cbSmbSupportImage.isVisible
                binding.cbSmbSupportGif.isChecked    = binding.cbSmbSupportGif.isVisible
                binding.cbSmbSupportVideo.isChecked  = false; binding.cbSmbSupportAudio.isChecked = false
                binding.cbSmbSupportText.isChecked   = false; binding.cbSmbSupportPdf.isChecked   = false
                binding.cbSmbSupportEpub.isChecked   = false
            }
            ResourceProfile.DOCUMENTS -> {
                binding.cbSmbSupportText.isChecked   = binding.cbSmbSupportText.isVisible
                binding.cbSmbSupportPdf.isChecked    = binding.cbSmbSupportPdf.isVisible
                binding.cbSmbSupportEpub.isChecked   = binding.cbSmbSupportEpub.isVisible
                binding.cbSmbSupportImage.isChecked  = false; binding.cbSmbSupportVideo.isChecked = false
                binding.cbSmbSupportAudio.isChecked  = false; binding.cbSmbSupportGif.isChecked   = false
            }
            ResourceProfile.ALL_FILES -> binding.cbSmbAllFiles.isChecked = true
            else -> Unit
        }
    }

    private fun applyProfilePresetToSftp(profile: ResourceProfile) {
        if (binding.cbSftpAllFiles.isChecked) binding.cbSftpAllFiles.isChecked = false
        when (profile) {
            ResourceProfile.AUDIO_LIBRARY -> {
                binding.cbSftpSupportAudio.isChecked  = binding.cbSftpSupportAudio.isVisible
                binding.cbSftpSupportImage.isChecked  = false; binding.cbSftpSupportVideo.isChecked = false
                binding.cbSftpSupportGif.isChecked    = false; binding.cbSftpSupportText.isChecked  = false
                binding.cbSftpSupportPdf.isChecked    = false; binding.cbSftpSupportEpub.isChecked  = false
                binding.cbSftpRememberFileList.isChecked = true
            }
            ResourceProfile.VIDEO_LIBRARY -> {
                binding.cbSftpSupportVideo.isChecked  = binding.cbSftpSupportVideo.isVisible
                binding.cbSftpSupportAudio.isChecked  = false; binding.cbSftpSupportImage.isChecked = false
                binding.cbSftpSupportGif.isChecked    = false; binding.cbSftpSupportText.isChecked  = false
                binding.cbSftpSupportPdf.isChecked    = false; binding.cbSftpSupportEpub.isChecked  = false
            }
            ResourceProfile.PHOTO_STORAGE -> {
                binding.cbSftpSupportImage.isChecked  = binding.cbSftpSupportImage.isVisible
                binding.cbSftpSupportGif.isChecked    = binding.cbSftpSupportGif.isVisible
                binding.cbSftpSupportVideo.isChecked  = false; binding.cbSftpSupportAudio.isChecked = false
                binding.cbSftpSupportText.isChecked   = false; binding.cbSftpSupportPdf.isChecked   = false
                binding.cbSftpSupportEpub.isChecked   = false
            }
            ResourceProfile.DOCUMENTS -> {
                binding.cbSftpSupportText.isChecked   = binding.cbSftpSupportText.isVisible
                binding.cbSftpSupportPdf.isChecked    = binding.cbSftpSupportPdf.isVisible
                binding.cbSftpSupportEpub.isChecked   = binding.cbSftpSupportEpub.isVisible
                binding.cbSftpSupportImage.isChecked  = false; binding.cbSftpSupportVideo.isChecked = false
                binding.cbSftpSupportAudio.isChecked  = false; binding.cbSftpSupportGif.isChecked   = false
            }
            ResourceProfile.ALL_FILES -> binding.cbSftpAllFiles.isChecked = true
            else -> Unit
        }
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
                accessPin = commonParams.second, profile = commonParams.third
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
                accessPin = commonParams.second, profile = commonParams.third
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
    }

    private fun getSftpSupportedTypes(): Set<MediaType> = buildSet {
        if (binding.cbSftpSupportImage.isChecked) add(MediaType.IMAGE)
        if (binding.cbSftpSupportVideo.isChecked) add(MediaType.VIDEO)
        if (binding.cbSftpSupportAudio.isChecked) add(MediaType.AUDIO)
        if (binding.cbSftpSupportGif.isChecked)   add(MediaType.GIF)
        if (binding.cbSftpSupportText.isChecked)  add(MediaType.TEXT)
        if (binding.cbSftpSupportPdf.isChecked)   add(MediaType.PDF)
        if (binding.cbSftpSupportEpub.isChecked)  add(MediaType.EPUB)
    }
}
