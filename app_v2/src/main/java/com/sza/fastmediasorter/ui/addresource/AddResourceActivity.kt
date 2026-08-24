package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.cloud.UnifiedCloudAuthManager
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.addresource.helpers.CreatedResourcePinManager
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class AddResourceActivity : BaseActivity<ActivityAddResourceBinding>() {

    // S1045: hosts typed/pre-filled SMB/SFTP/FTP passwords and SSH passphrases.
    override fun isSensitiveScreen(): Boolean = true

    private val viewModel: AddResourceViewModel by viewModels()

    private var copyResourceId: Long? = null
    private var pinShortcutOnCreate: Boolean = false

    private val keyboardDelegate = AddResourceKeyboardDelegate(object : AddResourceKeyboardDelegate.Callback {
        override fun navigateBack() { onBackPressedDispatcher.onBackPressed() }
        override fun showHelp() { InputHelpDialogFragment.show(supportFragmentManager, UiSurface.ADD_RESOURCE) }
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

    @Inject lateinit var createdResourcePinManager: CreatedResourcePinManager

    private lateinit var connectionManager: AddResourceConnectionManager
    private lateinit var scanManager: AddResourceScanManager
    private lateinit var formManager: AddResourceFormManager
    private lateinit var helper: AddResourceHelper
    private lateinit var watchPromptManager: AddResourceWatchPromptManager

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

    // S0421: .fmscfg has no registered MIME type, so the picker accepts any document.
    private val companionConfigPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importCompanionConfig(it) }
    }

    // S0988: camera QR scan returns the raw companion payload; the parser/import path is shared.
    private val companionQrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(
                com.sza.fastmediasorter.ui.companionimport.qr.CompanionQrScanActivity.EXTRA_PAYLOAD
            )?.let { payload -> viewModel.importCompanionConfigFromQr(payload) }
        }
    }

    override fun getViewBinding(): ActivityAddResourceBinding =
        ActivityAddResourceBinding.inflate(layoutInflater)

    // S1519: lazy ViewStub-backed form bindings - each resource-type form inflates on first access.
    internal val forms by lazy { AddResourceFormBindings(binding) }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        // S1331: registered here, not at btnScanNetwork - a recreated activity must have the listener
        // back before the restored discovery dialog resumes, otherwise the picked host is dropped.
        supportFragmentManager.setFragmentResultListener(NetworkDiscoveryDialog.RESULT_KEY, this) { _, bundle ->
            val hostIp = bundle.getString(NetworkDiscoveryDialog.RESULT_HOST_IP)
                ?: return@setFragmentResultListener
            forms.smb.etSmbServer.setText(hostIp)
            viewModel.scanShares(
                hostIp,
                forms.smb.etSmbUsername.text?.toString()?.trim().orEmpty(),
                forms.smb.etSmbPassword.text?.toString()?.trim().orEmpty(),
                forms.smb.etSmbDomain.text?.toString()?.trim().orEmpty(),
                forms.smb.etSmbPort.text?.toString()?.trim()?.toIntOrNull() ?: DEFAULT_SMB_PORT
            )
        }

        copyResourceId = intent.getLongExtra(EXTRA_COPY_RESOURCE_ID, -1L).takeIf { it != -1L }
        pinShortcutOnCreate = intent.getBooleanExtra(EXTRA_PIN_SHORTCUT_ON_CREATE, false)

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
        val isShown = { v: android.view.View -> v.visibility == android.view.View.VISIBLE }
        if (isShown(binding.btnAddToResources)) return binding.btnAddToResources
        return listOf(
            binding.cardLocalFolder,
            binding.cardNetworkFolder,
            binding.cardSftpFolder,
            binding.cardCloudStorage,
            binding.cardPairedWatch,
        ).firstOrNull(isShown) ?: binding.btnAddToResources
    }

    override fun setupViews() {
        formManager = AddResourceFormManager(this, binding, viewModel, remoteSourceGate, mediaCapabilities)
        formManager.applyEdgeToEdgeInsets()
        formManager.updateResourceTypeGridColumns()

        connectionManager = AddResourceConnectionManager(
            this, viewModel, unifiedAuthManager, dropboxClient, oneDriveClient
        )
        scanManager = AddResourceScanManager(this, viewModel, folderPickerLauncher, mediaCapabilities)
        helper = AddResourceHelper(this)
        watchPromptManager = AddResourceWatchPromptManager(this, viewModel)

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
        forms.local.rvResourcesToAdd.adapter = resourceToAddAdapter

        smbResourceToAddAdapter = ResourceToAddAdapter(
            onSelectionChanged = { resource, selected -> viewModel.toggleResourceSelection(resource, selected) },
            onNameChanged = { resource, newName -> viewModel.updateResourceName(resource, newName) },
            onDestinationChanged = { resource, isDestination -> viewModel.toggleDestination(resource, isDestination) },
            onScanSubdirectoriesChanged = { resource, scan -> viewModel.toggleScanSubdirectories(resource, scan) },
            onReadOnlyChanged = { resource, isReadOnly -> viewModel.toggleReadOnlyMode(resource, isReadOnly) },
            onMediaTypeToggled = { resource, type -> viewModel.toggleMediaType(resource, type) },
            onAllFilesChanged = { resource, allFiles -> viewModel.toggleAllFiles(resource, allFiles) }
        )
        forms.smb.rvSmbResourcesToAdd.adapter = smbResourceToAddAdapter

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
        binding.cardPairedWatch.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("PairedWatchCard", "AddResource")
            watchPromptManager.promptForName()
        }

        // S0991: import entry points next to the resource-type cards, sharing the same action source.
        binding.btnImportFromFile.setOnClickListener { launchCompanionFileImport() }
        binding.btnImportFromBarcode.isVisible = isBarcodeImportAvailable()
        binding.btnImportFromBarcode.setOnClickListener { launchCompanionQrScan() }

        forms.cloud.cardGoogleDrive.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("GoogleDriveCard", "AddResource")
            viewModel.loadCloudAccounts(com.sza.fastmediasorter.data.cloud.CloudProvider.GOOGLE_DRIVE.name)
        }
        forms.cloud.cardDropbox.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("DropboxCard", "AddResource")
            viewModel.loadCloudAccounts(com.sza.fastmediasorter.data.cloud.CloudProvider.DROPBOX.name)
        }
        forms.cloud.cardOneDrive.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("OneDriveCard", "AddResource")
            viewModel.loadCloudAccounts(com.sza.fastmediasorter.data.cloud.CloudProvider.ONEDRIVE.name)
        }

        // Protocol toggle
        forms.sftp.rgProtocol.setOnCheckedChangeListener { _, checkedId ->
            val currentPort = forms.sftp.etSftpPort.text.toString()
            when (checkedId) {
                forms.sftp.rbSftp.id -> if (currentPort.isBlank() || currentPort == "21") {
                    forms.sftp.etSftpPort.setText(R.string.default_sftp_port)
                }
                forms.sftp.rbFtp.id -> if (currentPort.isBlank() || currentPort == "22") {
                    forms.sftp.etSftpPort.setText(R.string.default_ftp_port)
                }
            }
            // Host-key pinning is an SSH concept; FTP has no host key, so hide the block for FTP.
            forms.sftp.cardSftpServerVerification.isVisible = checkedId == forms.sftp.rbSftp.id
        }

        // Local buttons
        forms.local.btnScan.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ScanLocal", "AddResource")
            viewModel.scanLocalFolders()
        }
        forms.local.btnAddManually.setOnClickListener {
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
        forms.smb.btnSmbTest.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("SmbTest", "AddResource")
            connectionManager.testSmbConnection()
        }
        forms.smb.btnScanNetwork.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ScanNetwork", "AddResource")
            NetworkDiscoveryDialog.newInstance()
                .show(supportFragmentManager, NetworkDiscoveryDialog.TAG)
        }
        forms.smb.btnScanShares.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ScanShares", "AddResource")
            val server = forms.smb.etSmbServer.text?.toString()?.trim().orEmpty()
            if (server.isEmpty()) {
                Toast.makeText(this, getString(R.string.server_address_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.scanShares(
                server,
                forms.smb.etSmbUsername.text?.toString()?.trim().orEmpty(),
                forms.smb.etSmbPassword.text?.toString()?.trim().orEmpty(),
                forms.smb.etSmbDomain.text?.toString()?.trim().orEmpty(),
                forms.smb.etSmbPort.text?.toString()?.trim()?.toIntOrNull() ?: DEFAULT_SMB_PORT
            )
        }
        forms.smb.btnSmbAddToResources.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSelectedSmb", "AddResource")
            viewModel.addSelectedResources()
        }
        forms.smb.btnSmbAddManually.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSmbManually", "AddResource")
            formManager.addSmbResourceManually(forms.smb.cbSmbReadOnlyMode.isChecked)
        }

        // SFTP buttons
        forms.sftp.btnSftpTest.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("SftpTest", "AddResource")
            connectionManager.testSftpConnection()
        }
        forms.sftp.btnSftpAddResource.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("AddSftp", "AddResource")
            formManager.addSftpResource()
        }
        forms.sftp.rgSftpAuthMethod.setOnCheckedChangeListener { _, checkedId ->
            forms.sftp.layoutSftpPasswordAuth.isVisible = checkedId == R.id.rbSftpPassword
            forms.sftp.layoutSftpSshKeyAuth.isVisible = checkedId == R.id.rbSftpSshKey
        }
        forms.sftp.btnSftpLoadKey.setOnClickListener {
            com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("LoadSshKey", "AddResource")
            sshKeyFilePickerLauncher.launch(arrayOf("*/*"))
        }
        // S0991/S0992: both SFTP-header import buttons delegate to the single shared action source.
        forms.sftp.btnSftpImportCompanion.setOnClickListener { launchCompanionFileImport() }
        // S0988: QR scan of a companion config. Hidden on camera-less devices and VR headsets
        // (Quest exposes no camera to CameraX), so the file import above stays the fallback there.
        forms.sftp.btnSftpScanCompanionQr.isVisible = isBarcodeImportAvailable()
        forms.sftp.btnSftpScanCompanionQr.setOnClickListener { launchCompanionQrScan() }
        // S0994: help link mirrors the file-import button's reachability (SFTP form is unreachable without companion).
        forms.sftp.btnSftpCompanionPublishHelp.setOnClickListener { openCompanionPublishGuide() }

        // Profile presets
        forms.smb.btnSmbProfilePreset.setOnClickListener { formManager.showProfilePresetDialog(isSmb = true) }
        forms.sftp.btnSftpProfilePreset.setOnClickListener { formManager.showProfilePresetDialog(isSmb = false) }

        formManager.setupCheckboxInteractions()
        formManager.setupTextInputTapBridges()
        formManager.setupCollapsibleSections()
        formManager.applyFlavorRestrictions()
    }

    // S0991/S0992: single action source shared by the type-screen entries and the SFTP-header buttons,
    // so the companion import path is wired once rather than duplicated per placement.
    private fun launchCompanionFileImport() {
        com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ImportCompanionConfig", "AddResource")
        companionConfigPickerLauncher.launch(arrayOf("*/*"))
    }

    private fun launchCompanionQrScan() {
        com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("ScanCompanionQr", "AddResource")
        companionQrScanLauncher.launch(
            com.sza.fastmediasorter.ui.companionimport.qr.CompanionQrScanActivity.createIntent(this)
        )
    }

    private fun openCompanionPublishGuide() {
        com.sza.fastmediasorter.utils.UserActionLogger.logButtonClick("CompanionPublishGuide", "AddResource")
        val factory = com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
        try {
            startActivity(factory.openUrl(factory.companionPublishGuideUrl()))
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "No browser to open companion publish-folders guide")
            Toast.makeText(this, R.string.settings_no_browser_for_docs, Toast.LENGTH_SHORT).show()
        }
    }

    /** Barcode/QR import needs a camera; camera-less devices (incl. VR headsets) fall back to file import. */
    private fun isBarcodeImportAvailable(): Boolean =
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)

    override fun observeData() {
        copyResourceId?.let { viewModel.loadResourceForCopy(it) }

        collectOnLifecycle(viewModel.state) { state ->
            val localResources = state.resourcesToAdd.filter { it.type == ResourceType.LOCAL }
            val smbResources = state.resourcesToAdd.filter { it.type == ResourceType.SMB }

            resourceToAddAdapter.submitList(localResources)
            resourceToAddAdapter.setSelectedPaths(state.selectedPaths)
            smbResourceToAddAdapter.submitList(smbResources)
            smbResourceToAddAdapter.setSelectedPaths(state.selectedPaths)

            forms.local.tvResourcesToAdd.isVisible = localResources.isNotEmpty()
            forms.local.rvResourcesToAdd.isVisible = localResources.isNotEmpty()
            binding.btnAddToResources.isVisible = localResources.isNotEmpty()
            forms.smb.tvSmbResourcesToAdd.isVisible = smbResources.isNotEmpty()
            forms.smb.rvSmbResourcesToAdd.isVisible = smbResources.isNotEmpty()
            forms.smb.btnSmbAddToResources.isVisible = smbResources.isNotEmpty()
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
                is AddResourceEvent.ResourcesAdded -> routeResourcesAdded(event.createdResourceIds)
                AddResourceEvent.ShowNoSharesFound -> connectionManager.showNoSharesFoundDialog()
                is AddResourceEvent.ShowSharePicker -> connectionManager.showSharePickerDialog(event.server, event.shares, event.manualShares)
                AddResourceEvent.ShowLocalNetworkPermission -> connectionManager.showLocalNetworkPermissionRationale()
            }
        }

        connectionManager.observeAuthEvents()
    }

    /**
     * S1423: closes the screen, pinning a shortcut first when the caller asked for one. `finish()`
     * runs after the await, never before - finishing cancels `lifecycleScope` and would drop the
     * pin request. Silence on a null message is deliberate: the resource-added toast has already
     * fired and the shortcut appearing is the confirmation.
     */
    private fun routeResourcesAdded(createdResourceIds: List<Long>) {
        if (!pinShortcutOnCreate) {
            finish()
            return
        }
        lifecycleScope.launch {
            val message = createdResourcePinManager.pinCreatedResources(
                context = this@AddResourceActivity,
                resourceIds = createdResourceIds
            )
            if (message != null) {
                Toast.makeText(this@AddResourceActivity, message, Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    override fun onResumeWithViews() {
        connectionManager.handleResume()
        // S1992: the all-files-access screen returns no activity result, so a grant made there is
        // read here instead. lateinit, so guard - an early finish() can resume before setupViews().
        if (::scanManager.isInitialized) scanManager.onHostResumed()
    }

    // ========== Section Navigation ==========

    internal fun showLocalFolderOptions() {
        binding.layoutResourceTypes.visibility = android.view.View.GONE
        binding.tvTitle.visibility = android.view.View.GONE
        binding.toolbar.title = getString(R.string.add_local_folder)
        forms.local.root.visibility = android.view.View.VISIBLE
    }

    internal fun showSmbFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.isVisible = false
        binding.toolbar.title = if (copyResourceId == null) {
            getString(R.string.create_network_resource_smb)
        } else {
            getString(R.string.copy_resource_title)
        }
        forms.smb.root.isVisible = true
        forms.sftpOrNull?.root?.isVisible = false
        formManager.setupIpAddressField()
        formManager.initSmbMediaTypes()
    }

    internal fun showSftpFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.isVisible = false
        binding.toolbar.title = getString(R.string.add_sftp_ftp_title)
        forms.smbOrNull?.root?.isVisible = false
        forms.sftp.root.isVisible = true
        forms.cloudOrNull?.root?.isVisible = false
        if (forms.sftp.etSftpPort.text.isNullOrBlank()) forms.sftp.etSftpPort.setText(R.string.default_sftp_port)
        forms.sftp.rbSftp.isChecked = true
        // SFTP is the default protocol here; ensure the SSH-only host-key block is shown even when the radio state is unchanged.
        forms.sftp.cardSftpServerVerification.isVisible = true
        formManager.initSftpMediaTypes()
    }

    internal fun showCloudStorageOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.isVisible = false
        binding.toolbar.title = getString(R.string.cloud_storage)
        forms.smbOrNull?.root?.isVisible = false
        forms.sftpOrNull?.root?.isVisible = false
        forms.cloud.root.isVisible = true
        connectionManager.updateCloudStorageStatus()
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
        private const val EXTRA_PIN_SHORTCUT_ON_CREATE = "extra_pin_shortcut_on_create"
        private const val DEFAULT_SMB_PORT = 445

        /**
         * S1423: [pinShortcutOnCreate] defaults to off so creation from inside the app keeps pinning
         * nothing; only a home-screen entry point opts in.
         */
        fun createIntent(
            context: Context,
            copyResourceId: Long? = null,
            preselectedTab: com.sza.fastmediasorter.ui.main.ResourceTab? = null,
            pinShortcutOnCreate: Boolean = false
        ): Intent {
            return Intent(context, AddResourceActivity::class.java).apply {
                copyResourceId?.let { putExtra(EXTRA_COPY_RESOURCE_ID, it) }
                preselectedTab?.let { putExtra(EXTRA_PRESELECTED_TAB, it.name) }
                if (pinShortcutOnCreate) {
                    putExtra(EXTRA_PIN_SHORTCUT_ON_CREATE, true)
                }
            }
        }
    }
}
