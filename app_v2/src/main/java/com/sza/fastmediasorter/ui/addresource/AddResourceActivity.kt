@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.ui.addresource

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.text.InputFilter
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.domain.model.ResourceProfile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.NetworkInterface
import javax.inject.Inject

@AndroidEntryPoint
class AddResourceActivity : BaseActivity<ActivityAddResourceBinding>() {

    private val viewModel: AddResourceViewModel by viewModels()
    
    private var copyResourceId: Long? = null
    
    @Inject
    lateinit var googleDriveClient: dagger.Lazy<GoogleDriveRestClient>
    
    @Inject
    lateinit var dropboxClient: dagger.Lazy<DropboxClient>
    
    @Inject
    lateinit var oneDriveClient: dagger.Lazy<OneDriveRestClient>
    
    private lateinit var resourceToAddAdapter: ResourceToAddAdapter
    private lateinit var smbResourceToAddAdapter: ResourceToAddAdapter
    
    private var googleDriveAccount: GoogleSignInAccount? = null
    private var isDropboxAuthenticated: Boolean = false
    private var isOneDriveAuthenticated: Boolean = false
    private var smbProfilePreset: ResourceProfile = ResourceProfile.NONE
    private var sftpProfilePreset: ResourceProfile = ResourceProfile.NONE
    
    private lateinit var helper: AddResourceHelper

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        timber.log.Timber.w("FOLDER_PICKER: SAF Result received, uri=$uri")
        if (uri != null) {
            timber.log.Timber.i("FOLDER_PICKER: SAF Selected folder: $uri")
            // Use centralized handler for both SAF and manual paths
            handleSelectedFolderUri(uri)
        } else {
            timber.log.Timber.w("FOLDER_PICKER: SAF User cancelled")
        }
    }
    
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.data)
    }
    
    private val sshKeyFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadSshKeyFromFile(it) }
    }

    override fun getViewBinding(): ActivityAddResourceBinding {
        return ActivityAddResourceBinding.inflate(layoutInflater)
    }
    
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if copying existing resource
        copyResourceId = intent.getLongExtra(EXTRA_COPY_RESOURCE_ID, -1L).takeIf { it != -1L }
        
        // Check for preselected resource tab
        val preselectedTabName = intent.getStringExtra(EXTRA_PRESELECTED_TAB)
        val preselectedTab = preselectedTabName?.let {
            try {
                com.sza.fastmediasorter.ui.main.ResourceTab.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        
        copyResourceId?.let {
            // Update toolbar title for copy mode
            binding.toolbar.title = getString(R.string.copy_resource_title)
            // Load will be triggered in observeData() after event subscription
        } ?: run {
            binding.toolbar.title = getString(R.string.add_resource_title)
            
            // Auto-open specific section based on preselected tab
            preselectedTab?.let { tab ->
                // Defer UI updates until after setupViews() completes
                binding.root.post {
                    when (tab) {
                        com.sza.fastmediasorter.ui.main.ResourceTab.LOCAL -> {
                            showLocalFolderOptions()
                        }
                        com.sza.fastmediasorter.ui.main.ResourceTab.SMB -> {
                            showSmbFolderOptions()
                        }
                        com.sza.fastmediasorter.ui.main.ResourceTab.FTP_SFTP -> {
                            showSftpFolderOptions()
                        }
                        com.sza.fastmediasorter.ui.main.ResourceTab.CLOUD -> {
                            showCloudStorageOptions()
                        }
                        com.sza.fastmediasorter.ui.main.ResourceTab.ALL,
                        com.sza.fastmediasorter.ui.main.ResourceTab.FAVORITES -> {
                            // Show all options (default behavior)
                        }
                    }
                }
            }
        }
    }

    override fun setupViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Set grid columns based on orientation (2 columns in landscape)
        updateResourceTypeGridColumns()
        
        helper = AddResourceHelper(this, binding)

        resourceToAddAdapter = ResourceToAddAdapter(
            onSelectionChanged = { resource, selected ->
                viewModel.toggleResourceSelection(resource, selected)
            },
            onNameChanged = { resource, newName ->
                viewModel.updateResourceName(resource, newName)
            },
            onDestinationChanged = { resource, isDestination ->
                viewModel.toggleDestination(resource, isDestination)
            },
            onScanSubdirectoriesChanged = { resource, scanSubdirectories ->
                viewModel.toggleScanSubdirectories(resource, scanSubdirectories)
            },
            onReadOnlyChanged = { resource, isReadOnly ->
                viewModel.toggleReadOnlyMode(resource, isReadOnly)
            },
            onMediaTypeToggled = { resource, type ->
                viewModel.toggleMediaType(resource, type)
            },
            onAllFilesChanged = { resource, allFiles ->
                viewModel.toggleAllFiles(resource, allFiles)
            }
        )
        
        binding.rvResourcesToAdd.adapter = resourceToAddAdapter
        
        smbResourceToAddAdapter = ResourceToAddAdapter(
            onSelectionChanged = { resource, selected ->
                viewModel.toggleResourceSelection(resource, selected)
            },
            onNameChanged = { resource, newName ->
                viewModel.updateResourceName(resource, newName)
            },
            onDestinationChanged = { resource, isDestination ->
                viewModel.toggleDestination(resource, isDestination)
            },
            onScanSubdirectoriesChanged = { resource, scanSubdirectories ->
                viewModel.toggleScanSubdirectories(resource, scanSubdirectories)
            },
            onReadOnlyChanged = { resource, isReadOnly ->
                viewModel.toggleReadOnlyMode(resource, isReadOnly)
            },
            onMediaTypeToggled = { resource, type ->
                viewModel.toggleMediaType(resource, type)
            },
            onAllFilesChanged = { resource, allFiles ->
                viewModel.toggleAllFiles(resource, allFiles)
            }
        )
        
        binding.rvSmbResourcesToAdd.adapter = smbResourceToAddAdapter

        binding.cardLocalFolder.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("LocalFolderCard", "AddResource")
            showLocalFolderOptions()
        }

        binding.cardNetworkFolder.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("NetworkFolderCard", "AddResource")
            showSmbFolderOptions()
        }
        
        binding.cardSftpFolder.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("SftpFolderCard", "AddResource")
            showSftpFolderOptions()
        }
        
        binding.cardCloudStorage.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("CloudStorageCard", "AddResource")
            showCloudStorageOptions()
        }
        
        binding.cardGoogleDrive.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("GoogleDriveCard", "AddResource")
            authenticateGoogleDrive()
        }
        
        binding.cardDropbox.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("DropboxCard", "AddResource")
            authenticateDropbox()
        }
        
        binding.cardOneDrive.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("OneDriveCard", "AddResource")
            authenticateOneDrive()
        }
        
        // SFTP/FTP protocol RadioGroup
        binding.rgProtocol.setOnCheckedChangeListener { _, checkedId ->
            val currentPort = binding.etSftpPort.text.toString()
            when (checkedId) {
                binding.rbSftp.id -> {
                    // Set port to 22 if empty or if it's FTP port (21)
                    if (currentPort.isBlank() || currentPort == "21") {
                        binding.etSftpPort.setText("22")
                    }
                }
                binding.rbFtp.id -> {
                    // Set port to 21 if empty or if it's SFTP port (22)
                    if (currentPort.isBlank() || currentPort == "22") {
                        binding.etSftpPort.setText("21")
                    }
                }
            }
        }

        binding.btnScan.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ScanLocal", "AddResource")
            viewModel.scanLocalFolders()
        }

        binding.btnAddManually.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddLocalManually", "AddResource")
            
            val androidVersion = Build.VERSION.SDK_INT
            val hasPermission = com.sza.fastmediasorter.core.util.PermissionHelper.hasAllFilesAccessPermission(this)
            
            timber.log.Timber.w("FOLDER_PICKER: Android SDK=$androidVersion, hasAllFilesAccess=$hasPermission")
            
            // Check MANAGE_EXTERNAL_STORAGE on Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                !hasPermission) {
                timber.log.Timber.w("FOLDER_PICKER: Showing permission dialog (Android 11+ without permission)")
                showAllFilesAccessPermissionDialog()
            } else {
                timber.log.Timber.w("FOLDER_PICKER: Showing folder selection dialog")
                showFolderSelectionDialog()
            }
        }

        binding.btnAddToResources.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSelectedLocal", "AddResource")
            viewModel.addSelectedResources()
        }

        // SMB buttons
        binding.btnSmbTest.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("SmbTest", "AddResource")
            testSmbConnection()
        }
        
        binding.btnScanNetwork.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ScanNetwork", "AddResource")
            val dialog = NetworkDiscoveryDialog.newInstance()
            dialog.onHostSelected = { host ->
                binding.etSmbServer.setText(host.ip)
                // Optional: show toast or indicator of detected services?
                val ports = host.openPorts.joinToString(", ")
                Toast.makeText(this, getString(R.string.msg_host_selected, host.hostname, ports), Toast.LENGTH_SHORT).show()
            }
            dialog.show(supportFragmentManager, NetworkDiscoveryDialog.TAG)
        }

        binding.btnSmbAddToResources.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSelectedSmb", "AddResource")
            // Add selected SMB resources from scan results
            viewModel.addSelectedResources()
        }

        binding.btnSmbAddManually.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSmbManually", "AddResource")
            // Add manually entered SMB resource
            addSmbResourceManually(binding.cbSmbReadOnlyMode.isChecked)
        }
        
        // SFTP buttons
        binding.btnSftpTest.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("SftpTest", "AddResource")
            testSftpConnection()
        }
        
        binding.btnSftpAddResource.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSftp", "AddResource")
            addSftpResource()
        }
        
        // SFTP auth method selection
        binding.rgSftpAuthMethod.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbSftpPassword -> {
                    binding.layoutSftpPasswordAuth.isVisible = true
                    binding.layoutSftpSshKeyAuth.isVisible = false
                }
                R.id.rbSftpSshKey -> {
                    binding.layoutSftpPasswordAuth.isVisible = false
                    binding.layoutSftpSshKeyAuth.isVisible = true
                }
            }
        }
        
        // SSH key file picker
        binding.btnSftpLoadKey.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("LoadSshKey", "AddResource")
            sshKeyFilePickerLauncher.launch(arrayOf("*/*"))
        }

        // Quick profile presets (Custom / Audio Library / Photo Storage)
        binding.btnSmbProfilePreset.setOnClickListener {
            showProfilePresetDialog(isSmb = true)
        }
        binding.btnSftpProfilePreset.setOnClickListener {
            showProfilePresetDialog(isSmb = false)
        }

        setupCheckboxInteractions()
        applyFlavorRestrictions()
    }
    
    /**
     * Apply product flavor-specific UI restrictions.
     * Hides resource type cards and options based on BuildConfig flags.
     */
    private fun applyFlavorRestrictions() {
        // Hide Cloud Storage card in lite flavor (SUPPORT_CLOUD = false)
        binding.cardCloudStorage.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_CLOUD
        
        // Hide SMB/Network folder card in lite flavor (SUPPORT_CLOUD = false means no network)
        // Note: SMB is separate from cloud, but lite flavor is for local-only use
        // For now, keep SMB/FTP enabled for lite since they are network but not cloud
        
        // Hide EPUB checkbox in lite and photos flavors (ENABLE_EPUB = false)
        val showEpub = com.sza.fastmediasorter.BuildConfig.ENABLE_EPUB
        binding.cbSmbSupportEpub.isVisible = showEpub
        binding.cbSftpSupportEpub.isVisible = showEpub
        
        // Hide video/audio options in photos flavor (SUPPORT_VIDEO = false, SUPPORT_AUDIO = false)
        val showVideo = com.sza.fastmediasorter.BuildConfig.SUPPORT_VIDEO
        val showAudio = com.sza.fastmediasorter.BuildConfig.SUPPORT_AUDIO
        binding.cbSmbSupportVideo.isVisible = showVideo
        binding.cbSftpSupportVideo.isVisible = showVideo
        binding.cbSmbSupportAudio.isVisible = showAudio
        binding.cbSftpSupportAudio.isVisible = showAudio
        
        // Hide document options (PDF, Text) in lite and photos flavors
        val showDocuments = com.sza.fastmediasorter.BuildConfig.SUPPORT_DOCUMENTS
        binding.cbSmbSupportPdf.isVisible = showDocuments
        binding.cbSftpSupportPdf.isVisible = showDocuments
        binding.cbSmbSupportText.isVisible = showDocuments
        binding.cbSftpSupportText.isVisible = showDocuments
    }

    private fun setupCheckboxInteractions() {
        // Local
        binding.cbLocalReadOnlyMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cbLocalAddToDestinations.isChecked = false
                binding.cbLocalAddToDestinations.isEnabled = false
            } else {
                binding.cbLocalAddToDestinations.isEnabled = true
            }
        }

        // SMB
        binding.cbSmbReadOnlyMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cbSmbAddToDestinations.isChecked = false
                binding.cbSmbAddToDestinations.isEnabled = false
            } else {
                binding.cbSmbAddToDestinations.isEnabled = true
            }
        }

        // SFTP
        binding.cbSftpReadOnlyMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cbSftpAddToDestinations.isChecked = false
                binding.cbSftpAddToDestinations.isEnabled = false
            } else {
                binding.cbSftpAddToDestinations.isEnabled = true
            }
        }
        
        // All Files checkboxes
        binding.cbSmbAllFiles.setOnCheckedChangeListener { _, isChecked ->
            updateMediaTypeCheckboxes(
                isChecked,
                binding.cbSmbSupportImage,
                binding.cbSmbSupportVideo,
                binding.cbSmbSupportAudio,
                binding.cbSmbSupportGif,
                binding.cbSmbSupportText,
                binding.cbSmbSupportPdf,
                binding.cbSmbSupportEpub
            )
        }
        
        binding.cbSftpAllFiles.setOnCheckedChangeListener { _, isChecked ->
            updateMediaTypeCheckboxes(
                isChecked,
                binding.cbSftpSupportImage,
                binding.cbSftpSupportVideo,
                binding.cbSftpSupportAudio,
                binding.cbSftpSupportGif,
                binding.cbSftpSupportText,
                binding.cbSftpSupportPdf,
                binding.cbSftpSupportEpub
            )
        }
    }
    
    private fun updateMediaTypeCheckboxes(
        allFilesEnabled: Boolean,
        vararg checkboxes: com.google.android.material.checkbox.MaterialCheckBox
    ) {
        checkboxes.forEach { checkbox ->
            checkbox.isChecked = allFilesEnabled || checkbox.isChecked
            checkbox.isEnabled = !allFilesEnabled
        }
    }

    private fun showProfilePresetDialog(isSmb: Boolean) {
        val profiles = arrayOf(
            ResourceProfile.NONE,
            ResourceProfile.AUDIO_LIBRARY,
            ResourceProfile.PHOTO_STORAGE
        )
        val labels = profiles.map { getString(getProfileLabelResId(it)) }.toTypedArray()
        val current = if (isSmb) smbProfilePreset else sftpProfilePreset
        val currentIndex = profiles.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_resource_type))
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selected = profiles[which]
                if (isSmb) {
                    smbProfilePreset = selected
                    binding.btnSmbProfilePreset.text = getString(getProfileLabelResId(selected))
                    applyProfilePresetToSmb(selected)
                } else {
                    sftpProfilePreset = selected
                    binding.btnSftpProfilePreset.text = getString(getProfileLabelResId(selected))
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
        if (binding.cbSmbAllFiles.isChecked) {
            binding.cbSmbAllFiles.isChecked = false
        }
        when (profile) {
            ResourceProfile.AUDIO_LIBRARY -> {
                binding.cbSmbSupportAudio.isChecked = binding.cbSmbSupportAudio.isVisible
                binding.cbSmbSupportImage.isChecked = false
                binding.cbSmbSupportVideo.isChecked = false
                binding.cbSmbSupportGif.isChecked = false
                binding.cbSmbSupportText.isChecked = false
                binding.cbSmbSupportPdf.isChecked = false
                binding.cbSmbSupportEpub.isChecked = false
            }
            ResourceProfile.PHOTO_STORAGE -> {
                binding.cbSmbSupportImage.isChecked = binding.cbSmbSupportImage.isVisible
                binding.cbSmbSupportGif.isChecked = binding.cbSmbSupportGif.isVisible
                binding.cbSmbSupportVideo.isChecked = false
                binding.cbSmbSupportAudio.isChecked = false
                binding.cbSmbSupportText.isChecked = false
                binding.cbSmbSupportPdf.isChecked = false
                binding.cbSmbSupportEpub.isChecked = false
            }
            else -> Unit
        }
    }

    private fun applyProfilePresetToSftp(profile: ResourceProfile) {
        if (binding.cbSftpAllFiles.isChecked) {
            binding.cbSftpAllFiles.isChecked = false
        }
        when (profile) {
            ResourceProfile.AUDIO_LIBRARY -> {
                binding.cbSftpSupportAudio.isChecked = binding.cbSftpSupportAudio.isVisible
                binding.cbSftpSupportImage.isChecked = false
                binding.cbSftpSupportVideo.isChecked = false
                binding.cbSftpSupportGif.isChecked = false
                binding.cbSftpSupportText.isChecked = false
                binding.cbSftpSupportPdf.isChecked = false
                binding.cbSftpSupportEpub.isChecked = false
            }
            ResourceProfile.PHOTO_STORAGE -> {
                binding.cbSftpSupportImage.isChecked = binding.cbSftpSupportImage.isVisible
                binding.cbSftpSupportGif.isChecked = binding.cbSftpSupportGif.isVisible
                binding.cbSftpSupportVideo.isChecked = false
                binding.cbSftpSupportAudio.isChecked = false
                binding.cbSftpSupportText.isChecked = false
                binding.cbSftpSupportPdf.isChecked = false
                binding.cbSftpSupportEpub.isChecked = false
            }
            else -> Unit
        }
    }

    override fun observeData() {
        // Load resource for copy mode AFTER event subscriptions are set up
        copyResourceId?.let { resourceId ->
            viewModel.loadResourceForCopy(resourceId)
        }
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Filter resources by type
                    val localResources = state.resourcesToAdd.filter { 
                        it.type == com.sza.fastmediasorter.domain.model.ResourceType.LOCAL 
                    }
                    val smbResources = state.resourcesToAdd.filter { 
                        it.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB 
                    }
                    
                    // Update adapters
                    resourceToAddAdapter.submitList(localResources)
                    resourceToAddAdapter.setSelectedPaths(state.selectedPaths)
                    
                    smbResourceToAddAdapter.submitList(smbResources)
                    smbResourceToAddAdapter.setSelectedPaths(state.selectedPaths)
                    
                    // Local folder UI visibility
                    val hasLocalResources = localResources.isNotEmpty()
                    binding.tvResourcesToAdd.isVisible = hasLocalResources
                    binding.rvResourcesToAdd.isVisible = hasLocalResources
                    binding.btnAddToResources.isVisible = hasLocalResources
                    
                    // SMB folder UI visibility
                    val hasSmbResources = smbResources.isNotEmpty()
                    binding.tvSmbResourcesToAdd.isVisible = hasSmbResources
                    binding.rvSmbResourcesToAdd.isVisible = hasSmbResources
                    binding.btnSmbAddToResources.isVisible = hasSmbResources
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loading.collect { isLoading ->
                    binding.progressBar.isVisible = isLoading
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AddResourceEvent.ShowError -> {
                            showError(event.message)
                        }
                        is AddResourceEvent.ShowMessage -> {
                            Toast.makeText(this@AddResourceActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is AddResourceEvent.ShowTestResult -> {
                            showTestResultDialog(event.message, event.isSuccess)
                        }
                        is AddResourceEvent.LoadResourceForCopy -> {
                            Timber.d("LoadResourceForCopy event received: ${event.resource.name}, type=${event.resource.type}")
                            helper.preFillResourceData(
                                event.resource,
                                event.username,
                                event.password,
                                event.domain,
                                event.sshKey,
                                event.sshPassphrase
                            )
                        }
                        AddResourceEvent.ResourcesAdded -> {
                            finish()
                        }
                    }
                }
            }
        }
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
    
    // copyToClipboard removed as it is now handled by DialogUtils

    /**
     * Set grid columns for resource type selection based on orientation.
     * Landscape mode uses 2 columns, portrait uses 1 column.
     */
    private fun updateResourceTypeGridColumns() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.layoutResourceTypes.columnCount = if (isLandscape) 2 else 1
    }

    private fun showError(message: String) {
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            if (settings.showDetailedErrors) {
                com.sza.fastmediasorter.ui.common.DialogUtils.showScrollableDialog(
                    this@AddResourceActivity,
                    getString(R.string.error),
                    message,
                    getString(android.R.string.ok)
                )
            } else {
                Toast.makeText(this@AddResourceActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    internal fun showLocalFolderOptions() {
        binding.layoutResourceTypes.visibility = android.view.View.GONE
        binding.tvTitle.visibility = android.view.View.GONE
        binding.toolbar.title = getString(com.sza.fastmediasorter.R.string.add_local_folder)
        binding.layoutLocalFolder.visibility = android.view.View.VISIBLE
    }

    internal fun showSmbFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.isVisible = true
        binding.tvTitle.text = getString(com.sza.fastmediasorter.R.string.add_network_folder)
        binding.toolbar.title = getString(R.string.add_resource_title)
        binding.layoutSmbFolder.isVisible = true
        binding.layoutSftpFolder.isVisible = false
        
        // Auto-fill SMB server field with device IP subnet
        setupIpAddressField()
        
        // Initialize media type checkboxes based on settings
        lifecycleScope.launch {
            val supportedTypes = viewModel.getSupportedMediaTypes()
            
            // Initialize with default state (not checked)
            binding.cbSmbAllFiles.isChecked = false
            
            // Initialize media type checkboxes based on global settings (but user can change)
            // Visibility depends on both user settings (supportedTypes) AND product flavor (BuildConfig)
            binding.cbSmbSupportImage.isChecked = com.sza.fastmediasorter.domain.model.MediaType.IMAGE in supportedTypes
            binding.cbSmbSupportImage.isEnabled = true
            binding.cbSmbSupportImage.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_IMAGES && 
                (com.sza.fastmediasorter.domain.model.MediaType.IMAGE in supportedTypes)
            
            binding.cbSmbSupportVideo.isChecked = com.sza.fastmediasorter.domain.model.MediaType.VIDEO in supportedTypes
            binding.cbSmbSupportVideo.isEnabled = true
            binding.cbSmbSupportVideo.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_VIDEO && 
                (com.sza.fastmediasorter.domain.model.MediaType.VIDEO in supportedTypes)
            
            binding.cbSmbSupportAudio.isChecked = com.sza.fastmediasorter.domain.model.MediaType.AUDIO in supportedTypes
            binding.cbSmbSupportAudio.isEnabled = true
            binding.cbSmbSupportAudio.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_AUDIO && 
                (com.sza.fastmediasorter.domain.model.MediaType.AUDIO in supportedTypes)
            
            binding.cbSmbSupportGif.isChecked = com.sza.fastmediasorter.domain.model.MediaType.GIF in supportedTypes
            binding.cbSmbSupportGif.isEnabled = true
            binding.cbSmbSupportGif.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_IMAGES && 
                (com.sza.fastmediasorter.domain.model.MediaType.GIF in supportedTypes)
            
            binding.cbSmbSupportText.isChecked = com.sza.fastmediasorter.domain.model.MediaType.TEXT in supportedTypes
            binding.cbSmbSupportText.isEnabled = true
            binding.cbSmbSupportText.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_DOCUMENTS && 
                (com.sza.fastmediasorter.domain.model.MediaType.TEXT in supportedTypes)
            
            binding.cbSmbSupportPdf.isChecked = com.sza.fastmediasorter.domain.model.MediaType.PDF in supportedTypes
            binding.cbSmbSupportPdf.isEnabled = true
            binding.cbSmbSupportPdf.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_DOCUMENTS && 
                (com.sza.fastmediasorter.domain.model.MediaType.PDF in supportedTypes)
            
            binding.cbSmbSupportEpub.isChecked = com.sza.fastmediasorter.domain.model.MediaType.EPUB in supportedTypes
            binding.cbSmbSupportEpub.isEnabled = true
            binding.cbSmbSupportEpub.isVisible = com.sza.fastmediasorter.BuildConfig.ENABLE_EPUB && 
                (com.sza.fastmediasorter.domain.model.MediaType.EPUB in supportedTypes)
        }
    }
    
    internal fun showSftpFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.isVisible = true
        binding.tvTitle.text = getString(R.string.add_sftp_ftp_title)
        binding.toolbar.title = getString(R.string.add_resource_title)
        binding.layoutSmbFolder.isVisible = false
        binding.layoutSftpFolder.isVisible = true
        binding.layoutCloudStorage.isVisible = false
        
        // Set default port to 22 (SFTP) when opening this section
        if (binding.etSftpPort.text.isNullOrBlank()) {
            binding.etSftpPort.setText("22")
        }
        
        // Select SFTP by default
        binding.rbSftp.isChecked = true
        
        // Initialize media type checkboxes based on settings
        lifecycleScope.launch {
            val supportedTypes = viewModel.getSupportedMediaTypes()
            
            // Initialize with default state (not checked)
            binding.cbSftpAllFiles.isChecked = false
            
            // Initialize media type checkboxes based on global settings (but user can change)
            // Visibility depends on both user settings (supportedTypes) AND product flavor (BuildConfig)
            binding.cbSftpSupportImage.isChecked = com.sza.fastmediasorter.domain.model.MediaType.IMAGE in supportedTypes
            binding.cbSftpSupportImage.isEnabled = true
            binding.cbSftpSupportImage.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_IMAGES && 
                (com.sza.fastmediasorter.domain.model.MediaType.IMAGE in supportedTypes)
            
            binding.cbSftpSupportVideo.isChecked = com.sza.fastmediasorter.domain.model.MediaType.VIDEO in supportedTypes
            binding.cbSftpSupportVideo.isEnabled = true
            binding.cbSftpSupportVideo.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_VIDEO && 
                (com.sza.fastmediasorter.domain.model.MediaType.VIDEO in supportedTypes)
            
            binding.cbSftpSupportAudio.isChecked = com.sza.fastmediasorter.domain.model.MediaType.AUDIO in supportedTypes
            binding.cbSftpSupportAudio.isEnabled = true
            binding.cbSftpSupportAudio.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_AUDIO && 
                (com.sza.fastmediasorter.domain.model.MediaType.AUDIO in supportedTypes)
            
            binding.cbSftpSupportGif.isChecked = com.sza.fastmediasorter.domain.model.MediaType.GIF in supportedTypes
            binding.cbSftpSupportGif.isEnabled = true
            binding.cbSftpSupportGif.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_IMAGES && 
                (com.sza.fastmediasorter.domain.model.MediaType.GIF in supportedTypes)
            
            binding.cbSftpSupportText.isChecked = com.sza.fastmediasorter.domain.model.MediaType.TEXT in supportedTypes
            binding.cbSftpSupportText.isEnabled = true
            binding.cbSftpSupportText.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_DOCUMENTS && 
                (com.sza.fastmediasorter.domain.model.MediaType.TEXT in supportedTypes)
            
            binding.cbSftpSupportPdf.isChecked = com.sza.fastmediasorter.domain.model.MediaType.PDF in supportedTypes
            binding.cbSftpSupportPdf.isEnabled = true
            binding.cbSftpSupportPdf.isVisible = com.sza.fastmediasorter.BuildConfig.SUPPORT_DOCUMENTS && 
                (com.sza.fastmediasorter.domain.model.MediaType.PDF in supportedTypes)
            
            binding.cbSftpSupportEpub.isChecked = com.sza.fastmediasorter.domain.model.MediaType.EPUB in supportedTypes
            binding.cbSftpSupportEpub.isEnabled = true
            binding.cbSftpSupportEpub.isVisible = com.sza.fastmediasorter.BuildConfig.ENABLE_EPUB && 
                (com.sza.fastmediasorter.domain.model.MediaType.EPUB in supportedTypes)
        }
    }
    
    internal fun showCloudStorageOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.isVisible = true
        binding.tvTitle.text = getString(R.string.cloud_storage)
        binding.toolbar.title = getString(R.string.add_resource_title)
        binding.layoutSmbFolder.isVisible = false
        binding.layoutSftpFolder.isVisible = false
        binding.layoutCloudStorage.isVisible = true
        
        updateCloudStorageStatus()
    }
    
    private fun updateCloudStorageStatus() {
        // Google Drive
        googleDriveAccount = GoogleSignIn.getLastSignedInAccount(this)
        
        if (googleDriveAccount != null) {
            binding.tvGoogleDriveStatus.text = getString(R.string.connected_as, googleDriveAccount?.email ?: "")
            binding.tvGoogleDriveStatus.isVisible = true
        } else {
            binding.tvGoogleDriveStatus.text = getString(R.string.not_connected)
            binding.tvGoogleDriveStatus.isVisible = true
        }
        
        // Dropbox - restore from storage and check connection
        lifecycleScope.launch {
            try {
                val restored = dropboxClient.get().tryRestoreFromStorage()
                if (restored) {
                    val testResult = dropboxClient.get().testConnection()
                    if (testResult is com.sza.fastmediasorter.data.cloud.CloudResult.Success) {
                        val email = dropboxClient.get().getAccountEmail() ?: "Unknown"
                        binding.tvDropboxStatus.text = getString(R.string.connected_as, email)
                        Timber.d("Dropbox connection restored: $email")
                    } else {
                        binding.tvDropboxStatus.text = getString(R.string.not_connected)
                        Timber.d("Dropbox restoration failed during test")
                    }
                } else {
                    binding.tvDropboxStatus.text = getString(R.string.not_connected)
                    Timber.d("Dropbox restoration failed")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore Dropbox connection")
                binding.tvDropboxStatus.text = getString(R.string.not_connected)
            }
        }
        
        // OneDrive - check if authenticated
        lifecycleScope.launch {
            try {
                if (oneDriveClient.get().isAuthenticated()) {
                    val testResult = oneDriveClient.get().testConnection()
                    if (testResult is com.sza.fastmediasorter.data.cloud.CloudResult.Success) {
                        val email = oneDriveClient.get().getAccountEmail() ?: "Unknown"
                        binding.tvOneDriveStatus.text = getString(R.string.connected_as, email)
                        Timber.d("OneDrive connected: $email")
                    } else {
                        binding.tvOneDriveStatus.text = getString(R.string.not_connected)
                        Timber.d("OneDrive test connection failed")
                    }
                } else {
                    binding.tvOneDriveStatus.text = getString(R.string.not_connected)
                    Timber.d("OneDrive not authenticated")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check OneDrive connection")
                binding.tvOneDriveStatus.text = getString(R.string.not_connected)
            }
        }
    }
    
    private fun authenticateGoogleDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        
        if (account != null) {
            showGoogleDriveSignedInOptions(account)
        } else {
            launchGoogleSignIn()
        }
    }
    
    private fun launchGoogleSignIn() {
        lifecycleScope.launch {
            try {
                val signInIntent = googleDriveClient.get().getSignInIntent()
                googleSignInLauncher.launch(signInIntent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to launch Google Sign-In")
                Toast.makeText(
                    this@AddResourceActivity,
                    getString(R.string.google_drive_authentication_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun handleGoogleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            googleDriveAccount = account
            updateCloudStorageStatus()
            
            Toast.makeText(
                this,
                getString(R.string.google_drive_signed_in, account.email ?: ""),
                Toast.LENGTH_SHORT
            ).show()
            
            // Navigate to folder selection
            navigateToGoogleDriveFolderPicker()
            
        } catch (e: ApiException) {
            Timber.e(e, "Google Sign-In failed: ${e.statusCode}")
            
            val errorMessage = when (e.statusCode) {
                10 -> { // DEVELOPER_ERROR
                    Timber.e("DEVELOPER_ERROR: OAuth Client ID not configured properly")
                    Timber.e("Package name: ${applicationContext.packageName}")
                    Timber.e("Required: Register SHA-1 fingerprint in Google Cloud Console")
                    Timber.e("See: https://developers.google.com/android/guides/client-auth")
                    "Google Drive authentication setup required. Check SHA-1 fingerprint in Google Cloud Console."
                }
                12 -> "Google Sign-In cancelled"
                7 -> "Network error. Check your internet connection."
                else -> getString(R.string.google_drive_authentication_failed)
            }
            
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }
    
    private fun navigateToGoogleDriveFolderPicker() {
        val intent = Intent(this, com.sza.fastmediasorter.ui.cloudfolders.GoogleDriveFolderPickerActivity::class.java)
        startActivity(intent)
    }
    
    private fun showGoogleDriveSignedInOptions(account: GoogleSignInAccount) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.google_drive))
            .setMessage(getString(R.string.connected_as, account.email ?: ""))
            .setPositiveButton(R.string.google_drive_select_folder) { _, _ ->
                navigateToGoogleDriveFolderPicker()
            }
            .setNegativeButton(R.string.google_drive_sign_out) { _, _ ->
                signOutGoogleDrive()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun signOutGoogleDrive() {
        GoogleSignIn.getClient(this, googleDriveClient.get().getSignInOptions()).signOut().addOnCompleteListener {
            googleDriveAccount = null
            updateCloudStorageStatus()
            Toast.makeText(this, getString(R.string.google_drive_signed_out), Toast.LENGTH_SHORT).show()
        }
    }
    
    // ========== Dropbox Methods ==========
    
    override fun onResume() {
        super.onResume()
        // Check if Dropbox authentication completed
        if (isDropboxAuthenticated) {
            lifecycleScope.launch {
                val result = dropboxClient.get().finishAuthentication()
                when (result) {
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Success -> {
                        Toast.makeText(
                            this@AddResourceActivity,
                            getString(R.string.dropbox_signed_in, result.accountName),
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToDropboxFolderPicker()
                    }
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Error -> {
                        showDropboxBetaDialog()
                    }
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Cancelled -> {
                        showDropboxBetaDialog()
                    }
                }
                isDropboxAuthenticated = false
            }
        }
        
        // Check if OneDrive authentication completed
        if (isOneDriveAuthenticated) {
            lifecycleScope.launch {
                val result = oneDriveClient.get().testConnection()
                when (result) {
                    is com.sza.fastmediasorter.data.cloud.CloudResult.Success -> {
                        Toast.makeText(
                            this@AddResourceActivity,
                            getString(R.string.msg_onedrive_auth_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToOneDriveFolderPicker()
                    }
                    is com.sza.fastmediasorter.data.cloud.CloudResult.Error -> {
                        Toast.makeText(
                            this@AddResourceActivity,
                            getString(R.string.onedrive_authentication_failed) + ": ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                isOneDriveAuthenticated = false
            }
        }
    }
    
    private fun authenticateDropbox() {
        lifecycleScope.launch {
            try {
                // Check if already authenticated
                val testResult = dropboxClient.get().testConnection()
                if (testResult is com.sza.fastmediasorter.data.cloud.CloudResult.Success) {
                    // Already authenticated
                    showDropboxSignedInOptions()
                } else {
                    // Use simple OAuth2 authentication without explicit scopes
                    // Scopes are configured in Dropbox App Console
                    com.dropbox.core.android.Auth.startOAuth2Authentication(
                        this@AddResourceActivity,
                        getString(R.string.dropbox_app_key)
                    )
                    isDropboxAuthenticated = true
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start Dropbox authentication")
                Toast.makeText(
                    this@AddResourceActivity,
                    getString(R.string.dropbox_authentication_failed) + ": ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun showDropboxBetaDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dropbox_beta_title)
            .setMessage(R.string.dropbox_beta_message)
            .setPositiveButton(R.string.dropbox_beta_send_email) { _, _ ->
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:serzhyale@gmail.com")
                    putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.dropbox_beta_email_subject))
                    putExtra(android.content.Intent.EXTRA_TEXT, getString(R.string.dropbox_beta_email_body))
                }
                try { startActivity(intent) } catch (e: Exception) { /* no email client */ }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun navigateToDropboxFolderPicker() {
        val intent = Intent(this, com.sza.fastmediasorter.ui.cloudfolders.DropboxFolderPickerActivity::class.java)
        startActivity(intent)
    }
    
    private fun showDropboxSignedInOptions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dropbox)
            .setMessage(R.string.msg_already_authenticated)
            .setPositiveButton(R.string.dropbox_select_folder) { _, _ ->
                navigateToDropboxFolderPicker()
            }
            .setNegativeButton(R.string.dropbox_sign_out) { _, _ ->
                signOutDropbox()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun signOutDropbox() {
        lifecycleScope.launch {
            dropboxClient.get().signOut()
            Toast.makeText(this@AddResourceActivity, getString(R.string.dropbox_signed_out), Toast.LENGTH_SHORT).show()
        }
    }
    
    // ========== OneDrive Methods ==========
    
    private fun authenticateOneDrive() {
        lifecycleScope.launch {
            try {
                val testResult = oneDriveClient.get().testConnection()
                if (testResult is com.sza.fastmediasorter.data.cloud.CloudResult.Success) {
                    showOneDriveSignedInOptions()
                } else {
                    val result = oneDriveClient.get().authenticate()
                    when (result) {
                        is com.sza.fastmediasorter.data.cloud.AuthResult.Success -> {
                            navigateToOneDriveFolderPicker()
                        }
                        is com.sza.fastmediasorter.data.cloud.AuthResult.Error -> {
                            if (result.message.contains("Interactive sign-in required")) {
                                // Trigger interactive sign-in
                                oneDriveClient.get().signIn(this@AddResourceActivity) { signInResult ->
                                    if (signInResult is com.sza.fastmediasorter.data.cloud.AuthResult.Success) {
                                        navigateToOneDriveFolderPicker()
                                    } else if (signInResult is com.sza.fastmediasorter.data.cloud.AuthResult.Error) {
                                        Toast.makeText(
                                            this@AddResourceActivity,
                                            getString(R.string.onedrive_authentication_failed) + ": ${signInResult.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                Toast.makeText(
                                    this@AddResourceActivity,
                                    getString(R.string.onedrive_authentication_failed) + ": ${result.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        is com.sza.fastmediasorter.data.cloud.AuthResult.Cancelled -> {
                            Toast.makeText(
                                this@AddResourceActivity,
                                getString(R.string.msg_onedrive_auth_cancelled),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start OneDrive authentication")
                Toast.makeText(
                    this@AddResourceActivity,
                    getString(R.string.onedrive_authentication_failed) + ": ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun navigateToOneDriveFolderPicker() {
        val intent = Intent(this, com.sza.fastmediasorter.ui.cloudfolders.OneDriveFolderPickerActivity::class.java)
        startActivity(intent)
    }
    
    private fun showOneDriveSignedInOptions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.onedrive)
            .setMessage(R.string.msg_already_authenticated)
            .setPositiveButton(R.string.onedrive_select_folder) { _, _ ->
                navigateToOneDriveFolderPicker()
            }
            .setNegativeButton(R.string.onedrive_sign_out) { _, _ ->
                signOutOneDrive()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun signOutOneDrive() {
        lifecycleScope.launch {
            oneDriveClient.get().signOut()
            Toast.makeText(this@AddResourceActivity, getString(R.string.onedrive_signed_out), Toast.LENGTH_SHORT).show()
        }
    }

    private fun testSmbConnection() {
        val server = binding.etSmbServer.text.toString().trim().replace(',', '.')
        
        // Validate IP/hostname using custom widget
        if (!binding.etSmbServer.isValid()) {
            Toast.makeText(this, getString(R.string.invalid_server_address), Toast.LENGTH_SHORT).show()
            binding.etSmbServer.requestFocus()
            return
        }
        
        val shareName = binding.etSmbShareName.text.toString().trim()
        val username = binding.etSmbUsername.text.toString().trim()
        val password = binding.etSmbPassword.text.toString().trim()
        val domain = binding.etSmbDomain.text.toString().trim()
        val portStr = binding.etSmbPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 445

        if (server.isEmpty()) {
            Toast.makeText(this, getString(R.string.server_address_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        // shareName is optional - if empty, tests server and lists shares
        // if provided, tests specific share access
        viewModel.testSmbConnection(server, shareName, username, password, domain, port)
    }

    private fun scanSmbShares() {
        val server = binding.etSmbServer.text.toString().trim().replace(',', '.')
        val username = binding.etSmbUsername.text.toString().trim()
        val password = binding.etSmbPassword.text.toString().trim()
        val domain = binding.etSmbDomain.text.toString().trim()
        val portStr = binding.etSmbPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 445

        if (server.isEmpty()) {
            Toast.makeText(this, getString(R.string.server_address_required), Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.scanSmbShares(server, username, password, domain, port)
    }

    /**
     * Add manually entered SMB resource (when user types share name directly)
     */
    private fun addSmbResourceManually(isReadOnly: Boolean = false) {
        val server = binding.etSmbServer.text.toString()
        val shareName = binding.etSmbShareName.text.toString()
        val username = binding.etSmbUsername.text.toString()
        val password = binding.etSmbPassword.text.toString()
        val resourceName = binding.etSmbResourceName.text.toString()
        val comment = binding.etSmbComment.text.toString()
        // pinCode not currently used
        // ... (rest of validation) ...

        viewModel.addSmbResourceManually(
            server = server,
            shareName = shareName,
            username = username,
            password = password,
            domain = "", // Domain support if added later
            port = 445, // Default SMB port
            resourceName = resourceName.takeIf { it.isNotBlank() },
            comment = comment.takeIf { it.isNotBlank() },
            addToDestinations = binding.cbSmbAddToDestinations.isChecked,
            supportedTypes = getSmbSupportedTypes(),
            isReadOnly = isReadOnly,
            allFiles = binding.cbSmbAllFiles.isChecked,
            scanSubdirectories = binding.cbSmbScanSubdirectories.isChecked
        )
    }


    /**
     * Get supported media types from SMB checkboxes
     */
    private fun getSmbSupportedTypes(): Set<com.sza.fastmediasorter.domain.model.MediaType> {
        val types = mutableSetOf<com.sza.fastmediasorter.domain.model.MediaType>()
        if (binding.cbSmbSupportImage.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.IMAGE)
        if (binding.cbSmbSupportVideo.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.VIDEO)
        if (binding.cbSmbSupportAudio.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.AUDIO)
        if (binding.cbSmbSupportGif.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.GIF)
        if (binding.cbSmbSupportText.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.TEXT)
        if (binding.cbSmbSupportPdf.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.PDF)
        if (binding.cbSmbSupportEpub.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.EPUB)
        return types
    }

    /**
     * Get supported media types from SFTP checkboxes
     */
    private fun getSftpSupportedTypes(): Set<com.sza.fastmediasorter.domain.model.MediaType> {
        val types = mutableSetOf<com.sza.fastmediasorter.domain.model.MediaType>()
        if (binding.cbSftpSupportImage.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.IMAGE)
        if (binding.cbSftpSupportVideo.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.VIDEO)
        if (binding.cbSftpSupportAudio.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.AUDIO)
        if (binding.cbSftpSupportGif.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.GIF)
        if (binding.cbSftpSupportText.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.TEXT)
        if (binding.cbSftpSupportPdf.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.PDF)
        if (binding.cbSftpSupportEpub.isChecked) types.add(com.sza.fastmediasorter.domain.model.MediaType.EPUB)
        return types
    }

    /**
     * Setup IP address input field with auto-fill and validation
     * Spec: Auto-fill with device IP subnet (e.g., "192.168.1."), 
     * allow only digits and dots, replace comma with dot,
     * block 4th dot and 4-digit numbers, validate each octet (0-255)
     */
    private fun setupIpAddressField() {
        // Auto-fill with device IP subnet
        val deviceIp = com.sza.fastmediasorter.utils.NetworkUtils.getLocalIpAddress(this)
        if (deviceIp != null) {
            val subnet = deviceIp.substringBeforeLast(".") + "."
            binding.etSmbServer.setText(subnet)
            binding.etSmbServer.setSelection(subnet.length)
        }

        // Custom IP widgets have built-in filtering and validation
    }


    
    // ========== SFTP/FTP Methods ==========
    
    private fun getSelectedProtocol(): com.sza.fastmediasorter.domain.model.ResourceType {
        return when (binding.rgProtocol.checkedRadioButtonId) {
            binding.rbSftp.id -> com.sza.fastmediasorter.domain.model.ResourceType.SFTP
            binding.rbFtp.id -> com.sza.fastmediasorter.domain.model.ResourceType.FTP
            else -> com.sza.fastmediasorter.domain.model.ResourceType.SFTP // Default to SFTP
        }
    }
    
    private fun testSftpConnection() {
        val protocolType = getSelectedProtocol()
        val host = binding.etSftpHost.text.toString().trim()
        
        // Validate IP/hostname using custom widget
        if (!binding.etSftpHost.isValid()) {
            Toast.makeText(this, getString(R.string.invalid_host_address), Toast.LENGTH_SHORT).show()
            binding.etSftpHost.requestFocus()
            return
        }
        
        val portStr = binding.etSftpPort.text.toString().trim()
        val defaultPort = if (protocolType == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) 22 else 21
        val port = portStr.toIntOrNull() ?: defaultPort
        val username = binding.etSftpUsername.text.toString().trim()
        
        if (host.isEmpty()) {
            Toast.makeText(this, getString(R.string.host_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Determine auth method for SFTP
        if (protocolType == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) {
            val useSshKey = binding.rbSftpSshKey.isChecked
            if (useSshKey) {
                val privateKey = binding.etSftpPrivateKey.text.toString().trim()
                val keyPassphrase = binding.etSftpKeyPassphrase.text.toString().trim().ifEmpty { null }
                
                if (privateKey.isEmpty()) {
                    Toast.makeText(this, getString(R.string.ssh_key_required), Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Test with SSH key
                viewModel.testSftpConnectionWithKey(host, port, username, privateKey, keyPassphrase)
            } else {
                // Test with password
                val password = binding.etSftpPassword.text.toString().trim()
                viewModel.testSftpFtpConnection(protocolType, host, port, username, password)
            }
        } else {
            // FTP always uses password
            val password = binding.etSftpPassword.text.toString().trim()
            viewModel.testSftpFtpConnection(protocolType, host, port, username, password)
        }
    }
    
    private fun addSftpResource() {
        val protocolType = getSelectedProtocol()
        val host = binding.etSftpHost.text.toString().trim()
        
        // Validate IP/hostname using custom widget
        if (!binding.etSftpHost.isValid()) {
            Toast.makeText(this, getString(R.string.invalid_host_address), Toast.LENGTH_SHORT).show()
            binding.etSftpHost.requestFocus()
            return
        }
        
        val portStr = binding.etSftpPort.text.toString().trim()
        val defaultPort = if (protocolType == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) 22 else 21
        val port = portStr.toIntOrNull() ?: defaultPort
        val username = binding.etSftpUsername.text.toString().trim()
        
        // Get normalized path from custom widget (with auto-correction)
        val remotePath = binding.etSftpPath.getNormalizedPath().ifEmpty { "/" }
        val resourceName = binding.etSftpResourceName.text.toString().trim()
        val comment = binding.etSftpComment.text.toString().trim()
        
        // Read supported media types from checkboxes
        val supportedTypes = getSftpSupportedTypes()
        
        if (supportedTypes.isEmpty()) {
            Toast.makeText(this, getString(R.string.at_least_one_media_type_required), Toast.LENGTH_SHORT).show()
            return
        }

        if (host.isEmpty()) {
            Toast.makeText(this, getString(R.string.host_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Determine auth method for SFTP
        if (protocolType == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) {
            val useSshKey = binding.rbSftpSshKey.isChecked
            if (useSshKey) {
                val privateKey = binding.etSftpPrivateKey.text.toString().trim()
                val keyPassphrase = binding.etSftpKeyPassphrase.text.toString().trim().ifEmpty { null }
                
                if (privateKey.isEmpty()) {
                    Toast.makeText(this, getString(R.string.ssh_key_required), Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Add with SSH key
                viewModel.addSftpResourceWithKey(
                    host = host,
                    port = port,
                    username = username,
                    privateKey = privateKey,
                    keyPassphrase = keyPassphrase,
                    remotePath = remotePath,
                    resourceName = resourceName,
                    comment = comment,
                    supportedTypes = supportedTypes,
                    allFiles = binding.cbSftpAllFiles.isChecked,
                    scanSubdirectories = binding.cbSftpScanSubdirectories.isChecked,
                    addToDestinations = binding.cbSftpAddToDestinations.isChecked,
                    isReadOnly = binding.cbSftpReadOnlyMode.isChecked
                )
            } else {
                // Add with password
                val password = binding.etSftpPassword.text.toString().trim()
                viewModel.addSftpFtpResource(
                    protocolType = protocolType,
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    remotePath = remotePath,
                    resourceName = resourceName,
                    comment = comment,
                    supportedTypes = supportedTypes,
                    allFiles = binding.cbSftpAllFiles.isChecked,
                    scanSubdirectories = binding.cbSftpScanSubdirectories.isChecked,
                    addToDestinations = binding.cbSftpAddToDestinations.isChecked,
                    isReadOnly = binding.cbSftpReadOnlyMode.isChecked
                )
            }
        } else {
            // FTP always uses password
            val password = binding.etSftpPassword.text.toString().trim()
            viewModel.addSftpFtpResource(
                protocolType = protocolType,
                host = host,
                port = port,
                username = username,
                password = password,
                remotePath = remotePath,
                resourceName = resourceName,
                comment = comment,
                supportedTypes = supportedTypes,
                allFiles = binding.cbSftpAllFiles.isChecked,
                scanSubdirectories = binding.cbSftpScanSubdirectories.isChecked,
                addToDestinations = binding.cbSftpAddToDestinations.isChecked,
                isReadOnly = binding.cbSftpReadOnlyMode.isChecked
            )
        }
    }
    
    private fun loadSshKeyFromFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val keyContent = inputStream.bufferedReader().use { it.readText() }
                binding.etSftpPrivateKey.setText(keyContent)
                Toast.makeText(this, getString(R.string.ssh_key_loaded), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load SSH key from file")
            Toast.makeText(this, getString(R.string.sftp_key_load_error), Toast.LENGTH_SHORT).show()
        }
    }
    

    
    
    /**
     * Show folder selection dialog with 3 options:
     * 1. Quick select from common folders
     * 2. Manual path input
     * 3. System folder picker (SAF)
     */
    private fun showFolderSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_folder_selection, null)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_folder)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        
        // Get reference to manual path field
        val etManualPath = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etManualPath)
        
        // Common folder paths
        // Note: Android 11+ apps moved to /Android/media/
        val quickFolders = mapOf(
            R.id.btnRoot to "/storage/emulated/0",
            R.id.btnDCIM to "/storage/emulated/0/DCIM",
            R.id.btnPictures to "/storage/emulated/0/Pictures",
            R.id.btnDownload to "/storage/emulated/0/Download",
            R.id.btnDocuments to "/storage/emulated/0/Documents",
            R.id.btnWhatsApp to "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media",
            R.id.btnTelegram to "/storage/emulated/0/Android/media/org.telegram.messenger/Telegram",
            R.id.btnInstagram to "/storage/emulated/0/Android/media/com.instagram.android/Instagram"
        )
        
        // Quick select buttons
        quickFolders.forEach { (buttonId, path) ->
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(buttonId)?.setOnClickListener {
                timber.log.Timber.i("Quick select: $path")
                
                // Special handling for Root - open folder browser
                if (buttonId == R.id.btnRoot) {
                    timber.log.Timber.i("Root button: Opening folder browser")
                    dialog.dismiss()
                    showFolderBrowserDialog(path)
                } else {
                    selectFolderByPath(path, dialog)
                }
            }
        }
        
        // Manual path validation
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnValidatePath)?.setOnClickListener {
            val path = etManualPath?.text?.toString()?.trim() ?: ""
            timber.log.Timber.i("Validating manual path: $path")
            selectFolderByPath(path, dialog)
        }
        
        // SAF picker fallback
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBrowseWithSAF)?.setOnClickListener {
            timber.log.Timber.i("Using SAF picker")
            dialog.dismiss()
            folderPickerLauncher.launch(null)
        }
        
        dialog.show()
    }
    
    /**
     * Select folder by direct file path.
     * Validates existence, directory status, and read permissions.
     * For Android/media folders, adds path even if validation fails (requires MANAGE_EXTERNAL_STORAGE).
     */
    private fun selectFolderByPath(path: String, dialog: Dialog) {
        timber.log.Timber.w("FOLDER_PICKER: Attempting to select path: $path")
        
        if (path.isBlank()) {
            Toast.makeText(this, R.string.folder_path_hint, Toast.LENGTH_SHORT).show()
            return
        }
        
        val dir = java.io.File(path)
        val isAndroidMedia = path.contains("/Android/media/")
        val hasAllFilesAccess = com.sza.fastmediasorter.core.util.PermissionHelper.hasAllFilesAccessPermission(this)
        
        when {
            !dir.exists() && !isAndroidMedia -> {
                timber.log.Timber.e("FOLDER_PICKER: Path does not exist: $path")
                Toast.makeText(this, getString(R.string.folder_not_found), Toast.LENGTH_SHORT).show()
            }
            !dir.exists() && isAndroidMedia && !hasAllFilesAccess -> {
                timber.log.Timber.e("FOLDER_PICKER: Android/media path requires MANAGE_EXTERNAL_STORAGE: $path")
                Toast.makeText(this, getString(R.string.android_media_requires_permission), Toast.LENGTH_LONG).show()
                showAllFilesAccessPermissionDialog()
            }
            !dir.exists() && isAndroidMedia && hasAllFilesAccess -> {
                // Android/media path with permission - allow adding even if not readable
                timber.log.Timber.w("FOLDER_PICKER: Adding Android/media path with permission (may not be readable yet): $path")
                val uri = android.net.Uri.fromFile(dir)
                handleSelectedFolderUri(uri, path)
                
                Toast.makeText(
                    this,
                    getString(R.string.android_media_folder_added_warning),
                    Toast.LENGTH_LONG
                ).show()
                
                dialog.dismiss()
            }
            !dir.isDirectory -> {
                timber.log.Timber.e("FOLDER_PICKER: Path is not a directory: $path")
                Toast.makeText(this, getString(R.string.not_a_folder), Toast.LENGTH_SHORT).show()
            }
            !dir.canRead() && !isAndroidMedia -> {
                timber.log.Timber.e("FOLDER_PICKER: Cannot read directory: $path")
                Toast.makeText(this, getString(R.string.cannot_read_folder), Toast.LENGTH_SHORT).show()
            }
            !dir.canRead() && isAndroidMedia && hasAllFilesAccess -> {
                // Android/media with permission - allow adding
                timber.log.Timber.w("FOLDER_PICKER: Adding non-readable Android/media path with permission: $path")
                val uri = android.net.Uri.fromFile(dir)
                handleSelectedFolderUri(uri, path)
                
                Toast.makeText(
                    this,
                    getString(R.string.android_media_folder_added_warning),
                    Toast.LENGTH_LONG
                ).show()
                
                dialog.dismiss()
            }
            else -> {
                // Success! Convert to URI and handle as selected folder
                timber.log.Timber.i("FOLDER_PICKER: Path valid, selecting: $path")
                val uri = android.net.Uri.fromFile(dir)
                
                // Use the same handler as SAF picker
                handleSelectedFolderUri(uri, path)
                
                Toast.makeText(
                    this,
                    getString(R.string.folder_selected_successfully, dir.name),
                    Toast.LENGTH_SHORT
                ).show()
                
                dialog.dismiss()
            }
        }
    }
    
    /**
     * Handle selected folder URI (from SAF or direct path).
     * Centralized handler for both selection methods.
     */
    private fun handleSelectedFolderUri(uri: android.net.Uri, displayPath: String? = null) {
        timber.log.Timber.i("Selected folder URI: $uri")
        
        // Try to take persistable permission if it's a SAF URI
        if (uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                timber.log.Timber.d("Persistable permission granted for: $uri")
            } catch (e: SecurityException) {
                timber.log.Timber.w(e, "Could not take persistable permission")
            }
        }
        
        // Get folder name
        val folderName = displayPath?.substringAfterLast("/") ?: uri.lastPathSegment ?: "Folder"
        
        // Store URI as string path for local resource
        val uriString = uri.toString()
        
        timber.log.Timber.d("Adding folder to resources list: name=$folderName, uri=$uriString")
        
        // Add to resources list via ViewModel
        viewModel.addManualFolder(uri)
    }
    
    /**
     * Show dialog explaining why All Files Access permission is needed.
     * When user grants permission, opens folder picker automatically.
     */
    /**
     * Show folder browser dialog for selecting any subfolder starting from rootPath.
     */
    private fun showFolderBrowserDialog(startPath: String = "/storage/emulated/0") {
        val dialogView = layoutInflater.inflate(R.layout.dialog_folder_browser, null)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.browse_folders)
            .setView(dialogView)
            .create()
        
        val tvCurrentPath = dialogView.findViewById<android.widget.TextView>(R.id.tvCurrentPath)
        val rvFolders = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvFolders)
        val btnSelectCurrent = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSelectCurrent)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        
        var currentPath = startPath
        tvCurrentPath?.text = currentPath
        
        // Adapter for folders
        val folders = mutableListOf<String>()
        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val view = layoutInflater.inflate(R.layout.item_folder, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {}
            }
            
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val folderName = folders[position]
                val tvName = holder.itemView.findViewById<android.widget.TextView>(R.id.tvFolderName)
                
                // Show ".." for parent directory, otherwise show folder name
                tvName?.text = folderName
                
                holder.itemView.setOnClickListener {
                    if (folderName == "..") {
                        // Navigate up
                        val parent = java.io.File(currentPath).parent
                        if (parent != null && parent.startsWith("/storage/emulated/0")) {
                            currentPath = parent
                            loadFolders(currentPath, folders, this)
                            tvCurrentPath?.text = currentPath
                        }
                    } else {
                        // Navigate into folder
                        currentPath = "$currentPath/$folderName"
                        loadFolders(currentPath, folders, this)
                        tvCurrentPath?.text = currentPath
                    }
                }
            }
            
            override fun getItemCount() = folders.size
        }
        
        rvFolders?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvFolders?.adapter = adapter
        
        // Load initial folders
        loadFolders(currentPath, folders, adapter)
        
        btnSelectCurrent?.setOnClickListener {
            timber.log.Timber.i("Selected folder: $currentPath")
            selectFolderByPath(currentPath, dialog)
        }
        
        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Load subdirectories of the given path.
     */
    private fun loadFolders(path: String, folders: MutableList<String>, adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>) {
        folders.clear()
        
        try {
            val dir = java.io.File(path)
            
            // Add ".." for parent if not at root
            if (path != "/storage/emulated/0" && dir.parent != null) {
                folders.add("..")
            }
            
            // List subdirectories
            val subDirs = dir.listFiles { file ->
                file.isDirectory && !file.name.startsWith(".")
            }?.sortedBy { it.name } ?: emptyList()
            
            folders.addAll(subDirs.map { it.name })
            
            adapter.notifyDataSetChanged()
            
            timber.log.Timber.d("Loaded ${folders.size} folders from $path")
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to load folders from $path")
            android.widget.Toast.makeText(
                this,
                getString(R.string.cannot_read_folder),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun showAllFilesAccessPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.all_files_access_required)
            .setMessage(R.string.all_files_access_explanation)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                com.sza.fastmediasorter.core.util.PermissionHelper.requestAllFilesAccessPermission(this)
            }
            .setNeutralButton(R.string.continue_limited) { _, _ ->
                // Continue with limited folder selection
                Toast.makeText(
                    this,
                    R.string.folder_selection_limitations,
                    Toast.LENGTH_LONG
                ).show()
                folderPickerLauncher.launch(null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(true)
            .show()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            com.sza.fastmediasorter.core.util.PermissionHelper.REQUEST_CODE_ALL_FILES_ACCESS -> {
                // Check if permission was granted
                if (com.sza.fastmediasorter.core.util.PermissionHelper.hasAllFilesAccessPermission(this)) {
                    Toast.makeText(
                        this,
                        getString(R.string.grant_permission) + " - " + getString(android.R.string.ok),
                        Toast.LENGTH_SHORT
                    ).show()
                    // Auto-launch folder picker
                    folderPickerLauncher.launch(null)
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.folder_selection_limitations),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_COPY_RESOURCE_ID = "extra_copy_resource_id"
        private const val EXTRA_PRESELECTED_TAB = "extra_preselected_tab"
        
        fun createIntent(context: Context, copyResourceId: Long? = null, preselectedTab: com.sza.fastmediasorter.ui.main.ResourceTab? = null): Intent {
            return Intent(context, AddResourceActivity::class.java).apply {
                copyResourceId?.let { putExtra(EXTRA_COPY_RESOURCE_ID, it) }
                preselectedTab?.let { putExtra(EXTRA_PRESELECTED_TAB, it.name) }
            }
        }
    }
}
