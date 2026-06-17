package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import android.view.KeyEvent
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.cloud.UnifiedCloudAuthManager
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.icon.ResourceIconRegistry
import com.sza.fastmediasorter.ui.icon.ResourceIconSet
import com.sza.fastmediasorter.ui.icon.picker.IconPickerBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class AddResourceActivity : BaseActivity<ActivityAddResourceBinding>() {

    private val viewModel: AddResourceViewModel by viewModels()

    private var copyResourceId: Long? = null

    /** Icon id the user selected via the icon picker before committing the add (S0034 Phase 06). */
    private var userPickedIconId: String? = null

    private val keyboardDelegate = AddResourceKeyboardDelegate(object : AddResourceKeyboardDelegate.Callback {
        override fun navigateBack() { onBackPressedDispatcher.onBackPressed() }
        override fun showHelp() { InputHelpDialogFragment.show(supportFragmentManager, InputSurface.ADD_RESOURCE) }
        override fun activateFocused(): Boolean = activateFocusedViewOrAncestor()
        override fun moveFocus(direction: FocusDirection) {
            val focusDir = when (direction) {
                FocusDirection.UP, FocusDirection.PREVIOUS -> android.view.View.FOCUS_UP
                FocusDirection.DOWN, FocusDirection.NEXT -> android.view.View.FOCUS_DOWN
                FocusDirection.LEFT -> android.view.View.FOCUS_LEFT
                FocusDirection.RIGHT -> android.view.View.FOCUS_RIGHT
                FocusDirection.FIRST -> android.view.View.FOCUS_UP
                FocusDirection.LAST -> android.view.View.FOCUS_DOWN
            }
            currentFocus?.focusSearch(focusDir)?.requestFocus()
        }
        override fun isTextEditorFocused(): Boolean =
            (currentFocus as? android.widget.TextView)?.onCheckIsTextEditor() == true
    })

    @Inject lateinit var unifiedAuthManager: UnifiedCloudAuthManager
    @Inject lateinit var dropboxClient: dagger.Lazy<DropboxClient>
    @Inject lateinit var oneDriveClient: dagger.Lazy<OneDriveRestClient>
    @Inject lateinit var remoteSourceGate: RemoteSourceAvailabilityGate
    @Inject lateinit var mediaCapabilities: MediaCapabilities

    private lateinit var connectionManager: AddResourceConnectionManager
    private lateinit var scanManager: AddResourceScanManager
    private lateinit var formManager: AddResourceFormManager
    private lateinit var helper: AddResourceHelper

    private lateinit var resourceToAddAdapter: com.sza.fastmediasorter.ui.addresource.ResourceToAddAdapter
    private lateinit var smbResourceToAddAdapter: com.sza.fastmediasorter.ui.addresource.ResourceToAddAdapter

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        Timber.w("FOLDER_PICKER: SAF Result received, uri=$uri")
        if (uri != null) {
            Timber.i("FOLDER_PICKER: SAF Selected folder: $uri")
            scanManager.handleSelectedFolderUri(uri)
        } else {
            Timber.w("FOLDER_PICKER: SAF User cancelled")
        }
    }

    // S0200 Phase 04c: googleSignInLauncher removed - Credential Manager handles sign-in
    // through GoogleIdentityRepository.signInPrimary (via UnifiedCloudAuthManager).

    private val sshKeyFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { scanManager.loadSshKeyFromFile(it) }
    }

    override fun getViewBinding(): ActivityAddResourceBinding =
        ActivityAddResourceBinding.inflate(layoutInflater)

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        copyResourceId = intent.getLongExtra(EXTRA_COPY_RESOURCE_ID, -1L).takeIf { it != -1L }

        val preselectedTab = intent.getStringExtra(EXTRA_PRESELECTED_TAB)?.let {
            try { com.sza.fastmediasorter.ui.main.ResourceTab.valueOf(it) }
            catch (e: IllegalArgumentException) { null }
        }

        copyResourceId?.let {
            binding.toolbar.title = getString(R.string.copy_resource_title)
        } ?: run {
            binding.toolbar.title = getString(R.string.add_resource_title)
            preselectedTab?.let { tab ->
                binding.root.post {
                    when (tab) {
                        com.sza.fastmediasorter.ui.main.ResourceTab.LOCAL -> showLocalFolderOptions()
                        com.sza.fastmediasorter.ui.main.ResourceTab.SMB -> showSmbFolderOptions()
                        com.sza.fastmediasorter.ui.main.ResourceTab.FTP_SFTP -> showSftpFolderOptions()
                        com.sza.fastmediasorter.ui.main.ResourceTab.CLOUD -> showCloudStorageOptions()
                        // ALL/FAVORITES carry no specific source type, so the type picker would show.
                        // Apply the same single-option skip as the no-extra path (S0391).
                        com.sza.fastmediasorter.ui.main.ResourceTab.ALL,
                        com.sza.fastmediasorter.ui.main.ResourceTab.FAVORITES -> maybeSkipTypeSelection()
                    }
                }
            } ?: binding.root.post { maybeSkipTypeSelection() }
        }
    }

    /**
     * When the type picker would offer only the always-present Local card - every remote source is
     * disabled (S0391) or unsupported by the flavor - a one-option screen is pointless, so open the
     * Local folder options directly and skip it. Runs only on a fresh add (no preselected tab, not a
     * copy). Queries the gate directly rather than card visibility, because `setupViews()` (which
     * applies card visibility) is itself deferred by BaseActivity and may not have run yet -
     * `anyRemoteEnabled()` is exactly the predicate that drives remote-card visibility.
     */
    private fun maybeSkipTypeSelection() {
        if (!remoteSourceGate.anyRemoteEnabled()) {
            showLocalFolderOptions()
        }
    }

    /**
     * Initial focus for a non-touch open. On the first open the type-selection screen is shown
     * and btnAddToResources stays GONE until a local resource exists, so focusing it was a
     * no-op. Prefer the first visible resource-type card; fall back to btnAddToResources once
     * it becomes visible. S0289.
     */
    override fun getInitialFocusView(): android.view.View? {
        Timber.d("S0289: add-resource initial-focus / first visible type card")
        val isShown = { v: android.view.View -> v.visibility == android.view.View.VISIBLE }
        if (isShown(binding.btnAddToResources)) return binding.btnAddToResources
        return listOf(
            binding.cardLocalFolder,
            binding.cardNetworkFolder,
            binding.cardSftpFolder,
            binding.cardCloudStorage,
        ).firstOrNull(isShown) ?: binding.btnAddToResources
    }

    override fun setupViews() {
        formManager = AddResourceFormManager(this, binding, viewModel, remoteSourceGate, mediaCapabilities)
        formManager.applyEdgeToEdgeInsets()
        formManager.updateResourceTypeGridColumns()

        connectionManager = AddResourceConnectionManager(
            this, binding, viewModel, unifiedAuthManager, dropboxClient, oneDriveClient
        )
        scanManager = AddResourceScanManager(this, binding, viewModel, folderPickerLauncher, mediaCapabilities)
        helper = AddResourceHelper(this, binding)

        binding.toolbar.setNavigationOnClickListener { finish() }

        resourceToAddAdapter = ResourceToAddAdapter(
            onSelectionChanged = { resource, selected -> viewModel.toggleResourceSelection(resource, selected) },
            onNameChanged = { resource, newName -> viewModel.updateResourceName(resource, newName) },
            onDestinationChanged = { resource, isDestination -> viewModel.toggleDestination(resource, isDestination) },
            onScanSubdirectoriesChanged = { resource, scan -> viewModel.toggleScanSubdirectories(resource, scan) },
            onReadOnlyChanged = { resource, isReadOnly -> viewModel.toggleReadOnlyMode(resource, isReadOnly) },
            onMediaTypeToggled = { resource, type -> viewModel.toggleMediaType(resource, type) },
            onAllFilesChanged = { resource, allFiles -> viewModel.toggleAllFiles(resource, allFiles) }
        )
        binding.rvResourcesToAdd.adapter = resourceToAddAdapter

        smbResourceToAddAdapter = ResourceToAddAdapter(
            onSelectionChanged = { resource, selected -> viewModel.toggleResourceSelection(resource, selected) },
            onNameChanged = { resource, newName -> viewModel.updateResourceName(resource, newName) },
            onDestinationChanged = { resource, isDestination -> viewModel.toggleDestination(resource, isDestination) },
            onScanSubdirectoriesChanged = { resource, scan -> viewModel.toggleScanSubdirectories(resource, scan) },
            onReadOnlyChanged = { resource, isReadOnly -> viewModel.toggleReadOnlyMode(resource, isReadOnly) },
            onMediaTypeToggled = { resource, type -> viewModel.toggleMediaType(resource, type) },
            onAllFilesChanged = { resource, allFiles -> viewModel.toggleAllFiles(resource, allFiles) }
        )
        binding.rvSmbResourcesToAdd.adapter = smbResourceToAddAdapter

        // Card navigation
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
            viewModel.loadCloudAccounts(com.sza.fastmediasorter.data.cloud.CloudProvider.GOOGLE_DRIVE.name)
        }
        binding.cardDropbox.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("DropboxCard", "AddResource")
            viewModel.loadCloudAccounts(com.sza.fastmediasorter.data.cloud.CloudProvider.DROPBOX.name)
        }
        binding.cardOneDrive.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("OneDriveCard", "AddResource")
            viewModel.loadCloudAccounts(com.sza.fastmediasorter.data.cloud.CloudProvider.ONEDRIVE.name)
        }

        // Protocol toggle
        binding.rgProtocol.setOnCheckedChangeListener { _, checkedId ->
            val currentPort = binding.etSftpPort.text.toString()
            when (checkedId) {
                binding.rbSftp.id -> if (currentPort.isBlank() || currentPort == "21") binding.etSftpPort.setText(R.string.default_sftp_port)
                binding.rbFtp.id  -> if (currentPort.isBlank() || currentPort == "22") binding.etSftpPort.setText(R.string.default_ftp_port)
            }
            // Host-key pinning is an SSH concept; FTP has no host key, so hide the block for FTP.
            binding.cardSftpServerVerification.isVisible = checkedId == binding.rbSftp.id
        }

        // Local buttons
        binding.btnScan.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ScanLocal", "AddResource")
            viewModel.scanLocalFolders()
        }
        binding.btnAddManually.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddLocalManually", "AddResource")
            Timber.w("FOLDER_PICKER: Android SDK=${android.os.Build.VERSION.SDK_INT}, hasAllFilesAccess=${com.sza.fastmediasorter.core.util.PermissionHelper.hasAllFilesAccessPermission(this)}")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                !com.sza.fastmediasorter.core.util.PermissionHelper.hasAllFilesAccessPermission(this)) {
                scanManager.showAllFilesAccessPermissionDialog()
            } else {
                scanManager.showFolderSelectionDialog()
            }
        }
        binding.btnAddToResources.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSelectedLocal", "AddResource")
            viewModel.addSelectedResources()
        }

        // SMB buttons
        binding.btnSmbTest.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("SmbTest", "AddResource")
            connectionManager.testSmbConnection()
        }
        binding.btnScanNetwork.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ScanNetwork", "AddResource")
            val dialog = NetworkDiscoveryDialog.newInstance()
            dialog.onHostSelected = { host ->
                binding.etSmbServer.setText(host.ip)
                viewModel.scanShares(
                    host.ip,
                    binding.etSmbUsername.text?.toString()?.trim().orEmpty(),
                    binding.etSmbPassword.text?.toString()?.trim().orEmpty(),
                    binding.etSmbDomain.text?.toString()?.trim().orEmpty(),
                    binding.etSmbPort.text?.toString()?.trim()?.toIntOrNull() ?: 445
                )
            }
            dialog.show(supportFragmentManager, NetworkDiscoveryDialog.TAG)
        }
        binding.btnScanShares.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ScanShares", "AddResource")
            val server = binding.etSmbServer.text?.toString()?.trim().orEmpty()
            if (server.isEmpty()) {
                Toast.makeText(this, getString(R.string.server_address_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.scanShares(
                server,
                binding.etSmbUsername.text?.toString()?.trim().orEmpty(),
                binding.etSmbPassword.text?.toString()?.trim().orEmpty(),
                binding.etSmbDomain.text?.toString()?.trim().orEmpty(),
                binding.etSmbPort.text?.toString()?.trim()?.toIntOrNull() ?: 445
            )
        }
        binding.btnSmbAddToResources.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSelectedSmb", "AddResource")
            viewModel.addSelectedResources()
        }
        binding.btnSmbAddManually.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSmbManually", "AddResource")
            formManager.addSmbResourceManually(binding.cbSmbReadOnlyMode.isChecked)
        }

        // SFTP buttons
        binding.btnSftpTest.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("SftpTest", "AddResource")
            connectionManager.testSftpConnection()
        }
        binding.btnSftpAddResource.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSftp", "AddResource")
            formManager.addSftpResource()
        }
        binding.rgSftpAuthMethod.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutSftpPasswordAuth.isVisible = checkedId == R.id.rbSftpPassword
            binding.layoutSftpSshKeyAuth.isVisible = checkedId == R.id.rbSftpSshKey
        }
        binding.btnSftpLoadKey.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("LoadSshKey", "AddResource")
            sshKeyFilePickerLauncher.launch(arrayOf("*/*"))
        }

        // Profile presets
        binding.btnSmbProfilePreset.setOnClickListener { formManager.showProfilePresetDialog(isSmb = true) }
        binding.btnSftpProfilePreset.setOnClickListener { formManager.showProfilePresetDialog(isSmb = false) }

        // Help buttons
        binding.btnSmbHelpRememberFileList.setOnClickListener { connectionManager.showRememberFileListHelpDialog() }
        binding.btnSftpHelpRememberFileList.setOnClickListener { connectionManager.showRememberFileListHelpDialog() }

        formManager.setupCheckboxInteractions()
        formManager.setupTextInputTapBridges()
        formManager.setupCollapsibleSections()
        formManager.applyFlavorRestrictions()

        setupIconPicker()
    }

    /** Wires the pick-icon button in the toolbar for pre-add icon customisation (S0034 Phase 06). */
    private fun setupIconPicker() {
        // Show the button; it is hidden by default in toolbar_icon_action.xml
        binding.pickIconAction.btnPickIcon.visibility = android.view.View.VISIBLE

        // Pick a random icon from the Other set so each new resource starts with a unique look
        val initialIconId = ResourceIconRegistry.randomIdFor(ResourceIconSet.OTHER)
        ResourceIconRegistry.resolveDrawable(initialIconId)?.let { binding.pickIconAction.btnPickIcon.setImageResource(it) }

        binding.pickIconAction.btnPickIcon.setOnClickListener {
            IconPickerBottomSheet.newInstance(userPickedIconId, ResourceIconSet.OTHER)
                .show(supportFragmentManager, "icon_picker")
        }

        // Listen for the icon chosen by the user; update preview and remember for save
        supportFragmentManager.setFragmentResultListener(
            IconPickerBottomSheet.KEY, this
        ) { _, bundle ->
            val pickedId = bundle.getString(IconPickerBottomSheet.RESULT_ICON_ID) ?: return@setFragmentResultListener
            userPickedIconId = pickedId
            ResourceIconRegistry.resolveDrawable(pickedId)?.let { binding.pickIconAction.btnPickIcon.setImageResource(it) }
        }
    }

    override fun observeData() {
        copyResourceId?.let { viewModel.loadResourceForCopy(it) }

        collectOnLifecycle(viewModel.state) { state ->
            val localResources = state.resourcesToAdd.filter { it.type == ResourceType.LOCAL }
            val smbResources = state.resourcesToAdd.filter { it.type == ResourceType.SMB }

            resourceToAddAdapter.submitList(localResources)
            resourceToAddAdapter.setSelectedPaths(state.selectedPaths)
            smbResourceToAddAdapter.submitList(smbResources)
            smbResourceToAddAdapter.setSelectedPaths(state.selectedPaths)

            binding.tvResourcesToAdd.isVisible = localResources.isNotEmpty()
            binding.rvResourcesToAdd.isVisible = localResources.isNotEmpty()
            binding.btnAddToResources.isVisible = localResources.isNotEmpty()
            binding.tvSmbResourcesToAdd.isVisible = smbResources.isNotEmpty()
            binding.rvSmbResourcesToAdd.isVisible = smbResources.isNotEmpty()
            binding.btnSmbAddToResources.isVisible = smbResources.isNotEmpty()
        }

        collectOnLifecycle(viewModel.loading) { binding.progressBar.isVisible = it }

        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is AddResourceEvent.ShowAccountPicker -> connectionManager.showAccountPicker(event.providerName, event.accounts)
                is AddResourceEvent.ShowError -> connectionManager.showError(event.message)
                is AddResourceEvent.ShowMessage -> Toast.makeText(this@AddResourceActivity, event.message, Toast.LENGTH_SHORT).show()
                is AddResourceEvent.ShowTestResult -> connectionManager.showTestResultDialog(event.message, event.isSuccess)
                is AddResourceEvent.LoadResourceForCopy -> {
                    Timber.d("LoadResourceForCopy event: ${event.resource.name}, type=${event.resource.type}")
                    helper.preFillResourceData(event.resource, event.username, event.password, event.domain, event.sshKey, event.sshPassphrase)
                }
                AddResourceEvent.ResourcesAdded -> finish()
                AddResourceEvent.ShowNoSharesFound -> connectionManager.showNoSharesFoundDialog()
                is AddResourceEvent.ShowSharePicker -> connectionManager.showSharePickerDialog(event.server, event.shares, event.manualShares)
                AddResourceEvent.ShowLocalNetworkPermission -> connectionManager.showLocalNetworkPermissionRationale()
            }
        }

        connectionManager.observeAuthEvents()
    }

    override fun onResumeWithViews() {
        connectionManager.handleResume()
    }

    // ========== Section Navigation ==========

    internal fun showLocalFolderOptions() {
        binding.layoutResourceTypes.visibility = android.view.View.GONE
        binding.tvTitle.visibility = android.view.View.GONE
        binding.toolbar.title = getString(R.string.add_local_folder)
        binding.layoutLocalFolder.visibility = android.view.View.VISIBLE
    }

    internal fun showSmbFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.isVisible = false
        binding.toolbar.title = if (copyResourceId == null) {
            getString(R.string.create_network_resource_smb)
        } else {
            getString(R.string.copy_resource_title)
        }
        binding.layoutSmbFolder.isVisible = true
        binding.layoutSftpFolder.isVisible = false
        formManager.setupIpAddressField()
        formManager.initSmbMediaTypes()
    }

    internal fun showSftpFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.isVisible = false
        binding.toolbar.title = getString(R.string.add_sftp_ftp_title)
        binding.layoutSmbFolder.isVisible = false
        binding.layoutSftpFolder.isVisible = true
        binding.layoutCloudStorage.isVisible = false
        if (binding.etSftpPort.text.isNullOrBlank()) binding.etSftpPort.setText(R.string.default_sftp_port)
        binding.rbSftp.isChecked = true
        // SFTP is the default protocol here; ensure the SSH-only host-key block is shown even when the radio state is unchanged.
        binding.cardSftpServerVerification.isVisible = true
        formManager.initSftpMediaTypes()
    }

    internal fun showCloudStorageOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.isVisible = false
        binding.toolbar.title = getString(R.string.cloud_storage)
        binding.layoutSmbFolder.isVisible = false
        binding.layoutSftpFolder.isVisible = false
        binding.layoutCloudStorage.isVisible = true
        connectionManager.updateCloudStorageStatus()
    }

    // ========== Activity Result ==========

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION") // super.onActivityResult parent API is deprecated; we keep the override for the legacy permissions branch.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            com.sza.fastmediasorter.core.util.PermissionHelper.REQUEST_CODE_ALL_FILES_ACCESS -> {
                if (com.sza.fastmediasorter.core.util.PermissionHelper.hasAllFilesAccessPermission(this)) {
                    Toast.makeText(this, getString(R.string.storage_permission_granted_continue), Toast.LENGTH_SHORT).show()
                    folderPickerLauncher.launch(null)
                } else {
                    Toast.makeText(this, getString(R.string.folder_selection_limitations), Toast.LENGTH_LONG).show()
                }
            }
            // S0200 Phase 04c: legacy Google Sign-In activity-result branch removed - Credential
            // Manager owns the handshake (no Intent round-trip).
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == com.sza.fastmediasorter.core.util.PermissionHelper.REQUEST_CODE_LOCAL_NETWORK) {
            com.sza.fastmediasorter.core.util.PermissionHelper.onLocalNetworkPermissionResult(this, grantResults)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyboardDelegate.handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
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
