@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.ui.editresource

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import android.view.View
import android.widget.PopupMenu
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityEditResourceBinding
import com.sza.fastmediasorter.ui.common.IpAddressInputFilter
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.data.cloud.CloudProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class EditResourceActivity : BaseActivity<ActivityEditResourceBinding>() {

    companion object {
        const val EXTRA_REQUIRES_RESCAN = "extra_requires_rescan"
    }

    private val viewModel: EditResourceViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    @Inject
    lateinit var googleDriveRestClient: com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
    
    private val googleSignInLauncher: ActivityResultLauncher<android.content.Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleGoogleSignInResult(result.data)
        }
    
    // TextWatcher references for temporary removal
    private var smbServerWatcher: TextWatcher? = null
    private var smbShareNameWatcher: TextWatcher? = null
    private var smbUsernameWatcher: TextWatcher? = null
    private var smbPasswordWatcher: TextWatcher? = null
    private var smbDomainWatcher: TextWatcher? = null
    private var smbPortWatcher: TextWatcher? = null
    
    private var sftpHostWatcher: TextWatcher? = null
    private var sftpPortWatcher: TextWatcher? = null
    private var sftpUsernameWatcher: TextWatcher? = null
    private var sftpPasswordWatcher: TextWatcher? = null
    private var sftpPathWatcher: TextWatcher? = null

    override fun getViewBinding(): ActivityEditResourceBinding {
        return ActivityEditResourceBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Apply flavor-based UI restrictions first
        applyFlavorRestrictions()
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Slideshow interval - text input with unit toggle
        binding.etSlideshowInterval.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Slideshow interval dropdown (1,5,10,30,60,120,300 sec)
        val slideshowOptions = arrayOf("1", "5", "10", "30", "60", "120", "300")
        val slideshowAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, slideshowOptions)
        binding.etSlideshowInterval.setAdapter(slideshowAdapter)
        
        binding.etSlideshowInterval.setOnItemClickListener { _, _, position, _ ->
            val seconds = slideshowOptions[position].toInt()
            viewModel.updateSlideshowInterval(seconds)
        }
        
        // Handle manual input
        binding.etSlideshowInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etSlideshowInterval.text.toString()
                val seconds = text.toIntOrNull() ?: 5
                val clampedSeconds = seconds.coerceIn(1, 3600)
                if (seconds != clampedSeconds) {
                    binding.etSlideshowInterval.setText(clampedSeconds.toString(), false)
                }
                viewModel.updateSlideshowInterval(clampedSeconds)
            }
        }

        // Media type checkboxes
        binding.cbSupportImages.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbSupportVideo.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbSupportAudio.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbSupportGif.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbSupportText.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbSupportPdf.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbSupportEpub.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        
        // Scan subdirectories - listener is set/reset in collect block to avoid programmatic trigger
        // Disable thumbnails - listener is set/reset in collect block to avoid programmatic trigger

        // Read-only mode checkbox
        binding.cbReadOnlyMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateReadOnlyMode(isChecked)
        }
        
        // All Files mode checkbox
        binding.cbAllFiles.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateAllFilesMode(isChecked)
        }

        // Resource name
        binding.etResourceName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateName(s?.toString() ?: "")
            }
        })

        // Resource comment
        binding.etResourceComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateComment(s?.toString() ?: "")
            }
        })

        // Access PIN
        binding.etAccessPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateAccessPin(s?.toString()?.takeIf { it.isNotBlank() })
            }
        })

        // Is destination switch
        binding.switchIsDestination.setOnCheckedChangeListener { _, isChecked ->
            Timber.d("Switch isDestination clicked: $isChecked")
            viewModel.updateIsDestination(isChecked)
        }
        
        // Clear Trash button
        binding.btnClearTrash.setOnClickListener {
            Timber.d("Button CLEAR_TRASH clicked")
            viewModel.requestClearTrash()
        }
        
        // Buttons
        binding.btnReset.setOnClickListener {
            Timber.d("Button RESET clicked")
            viewModel.resetToOriginal()
        }

        binding.btnTest.setOnClickListener {
            viewModel.state.value.currentResource ?: return@setOnClickListener
            Timber.d("Button TEST clicked")
            viewModel.testConnection()
        }

        /* Speed button combined into Test button
        binding.btnTestSpeed.setOnClickListener {
            Timber.d("Button TEST SPEED clicked")
            viewModel.runSpeedTest()
        }
        */

        binding.btnSave.setOnClickListener {
            Timber.d("Button SAVE clicked")
            viewModel.saveChanges()
        }
        
        // Initialize SMB credentials listeners
        addSmbListeners()
        
        // Initialize SFTP credentials listeners
        addSftpListeners()
    }
    
    /**
     * Hide UI elements that are not supported by the current product flavor.
     * This hides media type checkboxes based on BuildConfig flags.
     */
    private fun applyFlavorRestrictions() {
        // Video checkbox - only show when SUPPORT_VIDEO is true
        binding.cbSupportVideo.isVisible = BuildConfig.SUPPORT_VIDEO
        
        // Audio checkbox - only show when SUPPORT_AUDIO is true
        binding.cbSupportAudio.isVisible = BuildConfig.SUPPORT_AUDIO
        
        // Documents (Text and PDF) - only show when SUPPORT_DOCUMENTS is true
        if (!BuildConfig.SUPPORT_DOCUMENTS) {
            binding.cbSupportText.isVisible = false
            binding.cbSupportPdf.isVisible = false
        }
        
        // EPUB checkbox - only show when ENABLE_EPUB is true
        binding.cbSupportEpub.isVisible = BuildConfig.ENABLE_EPUB
        
        // Hide entire EPUB layout row if EPUB is disabled
        if (!BuildConfig.ENABLE_EPUB) {
            binding.layoutMediaTypesEpub.isVisible = false
        }
        
        // Hide Text/PDF layout row if documents are disabled
        if (!BuildConfig.SUPPORT_DOCUMENTS) {
            binding.layoutMediaTypesTextPdf.isVisible = false
        }
    }
    
    private fun addSmbListeners() {
        smbServerWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbServer(s?.toString() ?: "")
            }
        }
        binding.etSmbServerEdit.addTextChangedListener(smbServerWatcher)
        binding.etSmbServerEdit.filters = arrayOf(IpAddressInputFilter())
        
        smbShareNameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbShareName(s?.toString() ?: "")
            }
        }
        binding.etSmbShareNameEdit.addTextChangedListener(smbShareNameWatcher)
        
        smbUsernameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbUsername(s?.toString() ?: "")
            }
        }
        binding.etSmbUsernameEdit.addTextChangedListener(smbUsernameWatcher)
        
        smbPasswordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbPassword(s?.toString() ?: "")
            }
        }
        binding.etSmbPasswordEdit.addTextChangedListener(smbPasswordWatcher)
        
        smbDomainWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbDomain(s?.toString() ?: "")
            }
        }
        binding.etSmbDomainEdit.addTextChangedListener(smbDomainWatcher)
        
        smbPortWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val port = s?.toString()?.toIntOrNull() ?: 445
                viewModel.updateSmbPort(port)
            }
        }
        binding.etSmbPortEdit.addTextChangedListener(smbPortWatcher)
    }
    
    private fun removeSmbListeners() {
        smbServerWatcher?.let { binding.etSmbServerEdit.removeTextChangedListener(it) }
        smbShareNameWatcher?.let { binding.etSmbShareNameEdit.removeTextChangedListener(it) }
        smbUsernameWatcher?.let { binding.etSmbUsernameEdit.removeTextChangedListener(it) }
        smbPasswordWatcher?.let { binding.etSmbPasswordEdit.removeTextChangedListener(it) }
        smbDomainWatcher?.let { binding.etSmbDomainEdit.removeTextChangedListener(it) }
        smbPortWatcher?.let { binding.etSmbPortEdit.removeTextChangedListener(it) }
    }
    
    private fun addSftpListeners() {
        sftpHostWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSftpHost(s?.toString() ?: "")
            }
        }
        binding.etSftpHostEdit.addTextChangedListener(sftpHostWatcher)
        binding.etSftpHostEdit.filters = arrayOf(IpAddressInputFilter())
        
        sftpPortWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val port = s?.toString()?.toIntOrNull() ?: 22
                viewModel.updateSftpPort(port)
            }
        }
        binding.etSftpPortEdit.addTextChangedListener(sftpPortWatcher)
        
        sftpUsernameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSftpUsername(s?.toString() ?: "")
            }
        }
        binding.etSftpUsernameEdit.addTextChangedListener(sftpUsernameWatcher)
        
        sftpPasswordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSftpPassword(s?.toString() ?: "")
            }
        }
        binding.etSftpPasswordEdit.addTextChangedListener(sftpPasswordWatcher)
        
        sftpPathWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSftpPath(s?.toString() ?: "")
            }
        }
        binding.etSftpPathEdit.addTextChangedListener(sftpPathWatcher)
    }
    
    private fun removeSftpListeners() {
        sftpHostWatcher?.let { binding.etSftpHostEdit.removeTextChangedListener(it) }
        sftpPortWatcher?.let { binding.etSftpPortEdit.removeTextChangedListener(it) }
        sftpUsernameWatcher?.let { binding.etSftpUsernameEdit.removeTextChangedListener(it) }
        sftpPasswordWatcher?.let { binding.etSftpPasswordEdit.removeTextChangedListener(it) }
        sftpPathWatcher?.let { binding.etSftpPathEdit.removeTextChangedListener(it) }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    state.currentResource?.let { resource ->
                        // Update toolbar title with resource type
                        val resourceTypeLabel = when (resource.type) {
                            ResourceType.LOCAL -> getString(R.string.resource_type_local)
                            ResourceType.SMB -> getString(R.string.resource_type_smb)
                            ResourceType.SFTP -> getString(R.string.resource_type_sftp)
                            ResourceType.FTP -> getString(R.string.resource_type_ftp)
                            ResourceType.CLOUD -> getString(R.string.resource_type_cloud)
                        }
                        
                        // Update toolbar icon and title
                        val iconRes = when (resource.type) {
                            ResourceType.LOCAL -> R.drawable.ic_resource_local
                            ResourceType.SMB -> R.drawable.ic_resource_smb
                            ResourceType.SFTP -> R.drawable.ic_resource_sftp
                            ResourceType.FTP -> R.drawable.ic_resource_ftp
                            ResourceType.CLOUD -> {
                                // Show provider-specific icon for Cloud resources
                                when (resource.cloudProvider) {
                                    CloudProvider.GOOGLE_DRIVE -> R.drawable.ic_provider_google_drive
                                    CloudProvider.ONEDRIVE -> R.drawable.ic_provider_onedrive
                                    CloudProvider.DROPBOX -> R.drawable.ic_provider_dropbox
                                    null -> R.drawable.ic_resource_cloud
                                }
                            }
                        }
                        binding.toolbar.findViewById<ImageView>(R.id.ivResourceTypeIcon)?.setImageResource(iconRes)
                        binding.toolbar.findViewById<TextView>(R.id.tvToolbarTitle)?.text = 
                            getString(R.string.edit_resource_with_type, resourceTypeLabel)
                        
                        // Update UI with resource data
                        // Only update if text differs to avoid cursor position issues
                        if (binding.etResourceName.text.toString() != resource.name) {
                            binding.etResourceName.setText(resource.name)
                        }
                        if (binding.etResourceComment.text.toString() != resource.comment) {
                            binding.etResourceComment.setText(resource.comment)
                        }
                        if (binding.etAccessPassword.text.toString() != (resource.accessPin ?: "")) {
                            binding.etAccessPassword.setText(resource.accessPin ?: "")
                        }
                        binding.etResourcePath.setText(resource.path)
                        binding.tvCreatedDate.text = dateFormat.format(Date(resource.createdDate))
                        binding.tvFileCount.text = when {
                            resource.fileCount >= 1000 -> ">1000"
                            else -> resource.fileCount.toString()
                        }
                        
                        // Display last browse date or "Never browsed"
                        binding.tvLastBrowseDate.text = resource.lastBrowseDate?.let {
                            dateFormat.format(Date(it))
                        } ?: getString(R.string.never_browsed)

                        // Slideshow interval
                        binding.etSlideshowInterval.setText(resource.slideshowInterval.toString(), false)

                        // Media types
                        binding.cbSupportImages.isChecked = MediaType.IMAGE in resource.supportedMediaTypes
                        binding.cbSupportVideo.isChecked = MediaType.VIDEO in resource.supportedMediaTypes
                        binding.cbSupportAudio.isChecked = MediaType.AUDIO in resource.supportedMediaTypes
                        binding.cbSupportGif.isChecked = MediaType.GIF in resource.supportedMediaTypes
                        binding.cbSupportText.isChecked = MediaType.TEXT in resource.supportedMediaTypes
                        binding.cbSupportPdf.isChecked = MediaType.PDF in resource.supportedMediaTypes
                        binding.cbSupportEpub.isChecked = MediaType.EPUB in resource.supportedMediaTypes
                        
                        // All Files mode is per-resource setting, independent of global settings
                        val allFilesEnabled = resource.allFiles
                        
                        // Update All Files checkbox state
                        binding.cbAllFiles.setOnCheckedChangeListener(null)
                        binding.cbAllFiles.isChecked = resource.allFiles
                        binding.cbAllFiles.isEnabled = true
                        binding.cbAllFiles.setOnCheckedChangeListener { _, isChecked ->
                            viewModel.updateAllFilesMode(isChecked)
                        }
                        
                        // When resource-level "All Files" is ON, show info and disable type checkboxes
                        if (allFilesEnabled) {
                            // Show resource-level info message
                            binding.tvAllFilesInfo.text = getString(R.string.all_files_resource_info)
                            binding.tvAllFilesInfo.isVisible = true
                            
                            // Check all checkboxes (read-only)
                            binding.cbSupportImages.isChecked = true
                            binding.cbSupportVideo.isChecked = true
                            binding.cbSupportAudio.isChecked = true
                            binding.cbSupportGif.isChecked = true
                            binding.cbSupportText.isChecked = true
                            binding.cbSupportPdf.isChecked = true
                            binding.cbSupportEpub.isChecked = true
                            
                            // Disable checkboxes (read-only mode)
                            binding.cbSupportImages.isEnabled = false
                            binding.cbSupportVideo.isEnabled = false
                            binding.cbSupportAudio.isEnabled = false
                            binding.cbSupportGif.isEnabled = false
                            binding.cbSupportText.isEnabled = false
                            binding.cbSupportPdf.isEnabled = false
                            binding.cbSupportEpub.isEnabled = false
                            
                            Timber.d("All Files is ON for resource - media types locked to ALL")
                        } else {
                            // Hide info message
                            binding.tvAllFilesInfo.isVisible = false
                            
                            // Enable checkboxes (normal mode)
                            binding.cbSupportImages.isEnabled = true
                            binding.cbSupportVideo.isEnabled = true
                            binding.cbSupportAudio.isEnabled = true
                            binding.cbSupportGif.isEnabled = true
                            binding.cbSupportText.isEnabled = true
                            binding.cbSupportPdf.isEnabled = true
                            binding.cbSupportEpub.isEnabled = true
                        }
                        
                        // Show/Hide Text and PDF options based on global settings
                        binding.layoutMediaTypesTextPdf.isVisible = state.isGlobalTextSupportEnabled || state.isGlobalPdfSupportEnabled
                        binding.cbSupportText.isVisible = state.isGlobalTextSupportEnabled
                        binding.cbSupportPdf.isVisible = state.isGlobalPdfSupportEnabled
                        
                        // Show/Hide EPUB option based on global settings
                        binding.layoutMediaTypesEpub.isVisible = state.isGlobalEpubSupportEnabled
                        binding.cbSupportEpub.isVisible = state.isGlobalEpubSupportEnabled
                        
                        // Scan subdirectories - protect listener from programmatic changes
                        binding.cbScanSubdirectories.setOnCheckedChangeListener(null)
                        binding.cbScanSubdirectories.isChecked = resource.scanSubdirectories
                        binding.cbScanSubdirectories.setOnCheckedChangeListener { _, isChecked ->
                            viewModel.updateScanSubdirectories(isChecked)
                        }
                        
                        // Show Subfolders As Items (only visible when scanSubdirectories is ON)
                        binding.layoutShowSubfoldersAsItems.isVisible = resource.scanSubdirectories
                        if (resource.scanSubdirectories) {
                            binding.cbShowSubfoldersAsItems.setOnCheckedChangeListener(null)
                            binding.cbShowSubfoldersAsItems.isChecked = resource.showSubfoldersAsItems
                            binding.cbShowSubfoldersAsItems.setOnCheckedChangeListener { _, isChecked ->
                                viewModel.updateShowSubfoldersAsItems(isChecked)
                            }
                            binding.tvShowSubfoldersHint.text = getString(R.string.show_subfolders_as_items_hint)
                        }
                        
                        // Disable thumbnails - protect listener from programmatic changes
                        binding.cbDisableThumbnails.setOnCheckedChangeListener(null)
                        binding.cbDisableThumbnails.isChecked = resource.disableThumbnails
                        binding.cbDisableThumbnails.setOnCheckedChangeListener { _, isChecked ->
                            viewModel.updateDisableThumbnails(isChecked)
                        }


                        // Read-only mode: always allow user to toggle (it's a preference, not a capability)
                        binding.cbReadOnlyMode.setOnCheckedChangeListener(null)
                        // If resource is not writable, default to read-only checked, but still allow toggle
                        binding.cbReadOnlyMode.isChecked = if (!resource.isWritable) true else resource.isReadOnly
                        binding.cbReadOnlyMode.isEnabled = true // Always editable
                        binding.cbReadOnlyMode.setOnCheckedChangeListener { _, isChecked ->
                            viewModel.updateReadOnlyMode(isChecked)
                        }


                        // Is destination - use state value for reactivity
                        binding.switchIsDestination.setOnCheckedChangeListener(null)
                        binding.switchIsDestination.isEnabled = state.canBeDestination
                        binding.switchIsDestination.isChecked = resource.isDestination && state.canBeDestination
                        binding.switchIsDestination.setOnCheckedChangeListener { _, isChecked ->
                            viewModel.updateIsDestination(isChecked)
                        }
                        
                        // Show/hide credentials sections based on resource type
                        binding.layoutSmbCredentials.isVisible = resource.type == ResourceType.SMB
                        binding.layoutSftpCredentials.isVisible = resource.type == ResourceType.SFTP || resource.type == ResourceType.FTP
                        
                        // Update credentials title based on resource type
                        binding.tvCredentialsTitle.text = when (resource.type) {
                            ResourceType.SFTP -> getString(R.string.sftp_credentials)
                            ResourceType.FTP -> getString(R.string.ftp_credentials)
                            else -> getString(R.string.credentials)
                        }
                        
                        // Update Test button text (always use Test Connection)
                        binding.btnTest.text = getString(R.string.test_connection)


                        // Speed Test Results
                        Timber.d("EditResourceActivity: resource.type=${resource.type}, readSpeed=${resource.readSpeedMbps}, writeSpeed=${resource.writeSpeedMbps}")
                        if (resource.type in listOf(ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP, ResourceType.CLOUD) && 
                            (resource.readSpeedMbps != null || resource.writeSpeedMbps != null)) {
                            binding.cardSpeedResults.isVisible = true
                            binding.tvReadSpeed.text = resource.readSpeedMbps?.let { String.format(Locale.getDefault(), "%.2f Mbps", it) } ?: "-"
                            binding.tvWriteSpeed.text = resource.writeSpeedMbps?.let { String.format(Locale.getDefault(), "%.2f Mbps", it) } ?: "-"
                            binding.tvRecThreads.text = resource.recommendedThreads?.toString() ?: "-"
                            
                            binding.tvLastTestDate.text = resource.lastSpeedTestDate?.let {
                                getString(R.string.last_test_date, dateFormat.format(Date(it)))
                            } ?: getString(R.string.never_tested)
                            Timber.d("EditResourceActivity: Speed test card VISIBLE")
                        } else {
                            binding.cardSpeedResults.isVisible = false
                            Timber.d("EditResourceActivity: Speed test card HIDDEN")
                        }
                        
                        // Show/Hide Speed Test button -> Now combined with Test
                        // binding.btnTestSpeed.isVisible = resource.type in listOf(ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP)
                    }
                    
                    // Show/hide Clear Trash button
                    binding.btnClearTrash.isVisible = state.hasTrashFolders
                    
                    // Update SMB credentials UI (remove listeners temporarily to avoid triggering)
                    if (state.currentResource?.type == ResourceType.SMB) {
                        removeSmbListeners()
                        // Only update if text differs to avoid cursor position issues
                        if (binding.etSmbServerEdit.text.toString() != state.smbServer) {
                            binding.etSmbServerEdit.setText(state.smbServer)
                        }
                        if (binding.etSmbShareNameEdit.text.toString() != state.smbShareName) {
                            binding.etSmbShareNameEdit.setText(state.smbShareName)
                        }
                        if (binding.etSmbUsernameEdit.text.toString() != state.smbUsername) {
                            binding.etSmbUsernameEdit.setText(state.smbUsername)
                        }
                        if (binding.etSmbPasswordEdit.text.toString() != state.smbPassword) {
                            binding.etSmbPasswordEdit.setText(state.smbPassword)
                        }
                        if (binding.etSmbDomainEdit.text.toString() != state.smbDomain) {
                            binding.etSmbDomainEdit.setText(state.smbDomain)
                        }
                        if (binding.etSmbPortEdit.text.toString() != state.smbPort.toString()) {
                            binding.etSmbPortEdit.setText(state.smbPort.toString())
                        }
                        addSmbListeners()
                    }
                    
                    // Update SFTP/FTP credentials UI (remove listeners temporarily to avoid triggering)
                    if (state.currentResource?.type == ResourceType.SFTP || state.currentResource?.type == ResourceType.FTP) {
                        removeSftpListeners()
                        // Only update if text differs to avoid cursor position issues
                        if (binding.etSftpHostEdit.text.toString() != state.sftpHost) {
                            binding.etSftpHostEdit.setText(state.sftpHost)
                        }
                        if (binding.etSftpPortEdit.text.toString() != state.sftpPort.toString()) {
                            binding.etSftpPortEdit.setText(state.sftpPort.toString())
                        }
                        if (binding.etSftpUsernameEdit.text.toString() != state.sftpUsername) {
                            binding.etSftpUsernameEdit.setText(state.sftpUsername)
                        }
                        if (binding.etSftpPasswordEdit.text.toString() != state.sftpPassword) {
                            binding.etSftpPasswordEdit.setText(state.sftpPassword)
                        }
                        if (binding.etSftpPathEdit.text.toString() != state.sftpPath) {
                            binding.etSftpPathEdit.setText(state.sftpPath)
                        }
                        addSftpListeners()
                    }

                    // Enable Save button always, Reset only when has changes
                    binding.btnSave.isEnabled = true
                    binding.btnReset.isEnabled = state.hasResourceChanges || state.hasSmbCredentialsChanges || state.hasSftpCredentialsChanges
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loading.collect { isLoading ->
                    // binding.progressBar.isVisible = isLoading // Replaced by layoutProgress for specific speed test
                    if (!viewModel.state.value.isTestingSpeed) {
                         binding.layoutProgress.isVisible = isLoading
                         binding.progressBar.isVisible = true
                         binding.tvProgressMessage.text = getString(R.string.loading)
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state.isTestingSpeed) {
                        binding.layoutProgress.isVisible = true
                        binding.progressBar.isVisible = true
                        binding.tvProgressMessage.text = state.speedTestStatus.ifEmpty { getString(R.string.analyzing_speed) }
                    } else if (!viewModel.loading.value) { // Hide if not loading either
                         binding.layoutProgress.isVisible = false
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is EditResourceEvent.ShowError -> {
                            com.sza.fastmediasorter.ui.common.DialogUtils.showScrollableDialog(
                                this@EditResourceActivity,
                                getString(R.string.error),
                                event.message,
                                getString(android.R.string.ok)
                            )
                        }
                        is EditResourceEvent.ShowMessage -> {
                            Toast.makeText(this@EditResourceActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is EditResourceEvent.ShowMessageRes -> {
                            val message = if (event.args.isNotEmpty()) {
                                getString(event.messageResId, *event.args.toTypedArray())
                            } else {
                                getString(event.messageResId)
                            }
                            Toast.makeText(this@EditResourceActivity, message, Toast.LENGTH_SHORT).show()
                        }
                        is EditResourceEvent.ResourceUpdated -> {
                            Toast.makeText(this@EditResourceActivity, getString(R.string.resource_updated), Toast.LENGTH_SHORT).show()
                            val resultIntent = Intent().apply {
                                putExtra(EXTRA_REQUIRES_RESCAN, event.requiresRescan)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }
                        is EditResourceEvent.TestResult -> {
                            showTestResultDialog(event.message, event.success)
                            
                            // If test successful but credentials not saved, offer to save automatically
                            if (event.success) {
                                val state = viewModel.state.value
                                if (state.hasSmbCredentialsChanges || state.hasSftpCredentialsChanges) {
                                    androidx.appcompat.app.AlertDialog.Builder(this@EditResourceActivity)
                                        .setTitle(R.string.save_credentials_title)
                                        .setMessage(R.string.save_credentials_message)
                                        .setPositiveButton(R.string.save_now) { _, _ ->
                                            viewModel.saveChanges()
                                        }
                                        .setNegativeButton(R.string.later, null)
                                        .show()
                                }
                            }
                        }
                        is EditResourceEvent.ConfirmClearTrash -> {
                            showClearTrashConfirmDialog(event.count)
                        }
                        is EditResourceEvent.TrashCleared -> {
                            Toast.makeText(
                                this@EditResourceActivity,
                                getString(R.string.trash_cleared, event.count),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is EditResourceEvent.RequestCloudReAuthentication -> {
                            showCloudReAuthenticationDialog()
                        }
                    }
                }
            }
        }
    }

    private fun updateMediaTypes() {
        val types = mutableSetOf<MediaType>()
        if (binding.cbSupportImages.isChecked) types.add(MediaType.IMAGE)
        if (binding.cbSupportVideo.isChecked) types.add(MediaType.VIDEO)
        if (binding.cbSupportAudio.isChecked) types.add(MediaType.AUDIO)
        if (binding.cbSupportGif.isChecked) types.add(MediaType.GIF)
        if (binding.cbSupportText.isChecked) types.add(MediaType.TEXT)
        if (binding.cbSupportPdf.isChecked) types.add(MediaType.PDF)
        if (binding.cbSupportEpub.isChecked) types.add(MediaType.EPUB)
        viewModel.updateSupportedMediaTypes(types)
    }
    
    private fun showTestResultDialog(message: String, isSuccess: Boolean) {
        val title = if (isSuccess) getString(R.string.connection_test_success_title) else getString(R.string.connection_test_failed_title)
        com.sza.fastmediasorter.ui.common.DialogUtils.showScrollableDialog(
            this,
            title,
            message,
            getString(android.R.string.ok)
        )
    }
    
    private fun showClearTrashConfirmDialog(count: Int) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_trash_confirm_title))
            .setMessage(getString(R.string.clear_trash_confirm_message, count))
            .setPositiveButton(R.string.clear_trash) { _, _ ->
                viewModel.clearTrash()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Test Result", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }
    
    private fun showCloudReAuthenticationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_connection))
            .setMessage(getString(R.string.cloud_auth_dialog_message))
            .setPositiveButton(R.string.sign_in_now) { dialog, _ ->
                dialog.dismiss()
                launchGoogleSignIn()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun launchGoogleSignIn() {
        val signInIntent = googleDriveRestClient.getSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }
    
    private fun handleGoogleSignInResult(data: android.content.Intent?) {
        lifecycleScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                
                Timber.d("Google Sign-In successful: ${account.email}")
                
                // Initialize client with account
                val result = googleDriveRestClient.handleSignInResult(account)
                
                when (result) {
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Success -> {
                        Toast.makeText(
                            this@EditResourceActivity,
                            getString(R.string.authentication_successful, result.accountName),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Error -> {
                        Toast.makeText(
                            this@EditResourceActivity,
                            getString(R.string.authentication_failed, result.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Cancelled -> {
                        Toast.makeText(
                            this@EditResourceActivity,
                            getString(android.R.string.cancel),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                
            } catch (e: ApiException) {
                Timber.e(e, "Google Sign-In failed")
                Toast.makeText(
                    this@EditResourceActivity,
                    getString(R.string.authentication_failed, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

}
