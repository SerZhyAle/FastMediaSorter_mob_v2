package com.sza.fastmediasorter.ui.settings.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.domain.model.DeviceStorageState
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.delivery.ExtensionsManagerFragment
import com.sza.fastmediasorter.domain.usecase.CalculateOptimalCacheSizeUseCase
import com.sza.fastmediasorter.domain.usecase.EnsureAllFilesPredefinedResourceUseCase
import com.sza.fastmediasorter.domain.usecase.GatherSystemInfoUseCase
import com.sza.fastmediasorter.domain.usecase.SaveTextFileToResourceUseCase
import com.sza.fastmediasorter.ui.settings.BackupRestoreViewModel
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.auth.AuthSessionsActivity
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsBackupHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsCacheHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsCredentialHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsImportExportHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsLogHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsObserversHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsPermissionsHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsResetHelper
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsPrefetchHelper
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.BuildConfig
import androidx.core.view.isVisible
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsViewSetupHelper
import com.sza.fastmediasorter.ui.settings.SettingsProfileViewModel
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsProfileHelper
import com.sza.fastmediasorter.ui.settings.helpers.SettingsRowStackManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class GeneralSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsGeneralBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var audioMetadataCacheRepository: AudioMetadataCacheRepository
    @Inject lateinit var streamingCacheRepository: StreamingCacheRepository
    @Inject lateinit var requestContextualPermission: com.sza.fastmediasorter.domain.usecase.RequestContextualPermissionUseCase
    @Inject lateinit var permissionRegistry: com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
    @Inject lateinit var gatherSystemInfoUseCase: GatherSystemInfoUseCase
    @Inject lateinit var ensureAllFilesPredefinedResourceUseCase: EnsureAllFilesPredefinedResourceUseCase
    @Inject lateinit var saveTextFileToResourceUseCase: SaveTextFileToResourceUseCase
    @Inject lateinit var mediaCapabilities: com.sza.fastmediasorter.core.capability.MediaCapabilities

    // S1052: empty except on standard + noLegal (shared capture engine binds the menu launcher).
    // Gates the debug-only screenshot-test button relocated into the General-tab debug section.
    @Inject
    lateinit var menuScreenshotLaunchers: Set<@JvmSuppressWildcards MenuScreenshotLauncher>

    // S0547: gate the Downloadable Extensions row - lite/photos ship no downloadable sets, so the
    // screen would open empty. Same contract the welcome page already uses.
    @Inject lateinit var capabilityAvailability: com.sza.fastmediasorter.core.capability.CapabilityAvailability

    // S0391: gate decides whether the cloud group toggle row is visible on this flavor.
    @Inject lateinit var remoteSourceAvailabilityGate: com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate

    private val viewModel: SettingsViewModel by activityViewModels()
    private val backupViewModel: BackupRestoreViewModel by viewModels()

    // S0200 Phase 06: ViewModel for the new "Google Account" Settings card.
    private val googleAccountViewModel: com.sza.fastmediasorter.ui.settings.GoogleAccountSettingsViewModel by viewModels()
    private val profileViewModel: SettingsProfileViewModel by viewModels()

    @Inject lateinit var cctChecker: com.sza.fastmediasorter.data.browser.CctAvailabilityChecker

    // Shared flag passed as lambdas to helpers that need to suppress listeners during programmatic updates
    private var isUpdatingSpinner = false

    // Activity result launchers must be registered at construction time (before onCreateView)
    private val importCredentialsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { credentialHelper.importCredentialsFromUri(it) }
        }

    private val importSettingsFileLauncher: androidx.activity.result.ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importExportHelper.importSettingsFromUri(it) }
        }

    private val saveLogsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val result = LogExportHelper.writeZipToUri(requireContext(), uri)
            when (result) {
                LogExportHelper.ExportResult.SaveSuccess ->
                    Toast.makeText(requireContext(), R.string.save_logs_success, Toast.LENGTH_SHORT).show()
                LogExportHelper.ExportResult.NoLogs ->
                    Toast.makeText(requireContext(), R.string.export_logs_no_files, Toast.LENGTH_SHORT).show()
                is LogExportHelper.ExportResult.Error ->
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }
    }

    private val mediaPermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsHelper.updatePermissionButtonsState()
        }

    private val notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionsHelper.updatePermissionButtonsState()
        }

    // S0491: favorites + resource-share export/import SAF launchers (registered before STARTED, handled by backupHelper).
    private val importFavoritesLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { backupViewModel.previewFavoritesImport(it) }
        }

    private val exportResourcesLauncher: androidx.activity.result.ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.CreateDocument(com.sza.fastmediasorter.domain.model.ResourceShareFormat.MIME_TYPE)) { uri ->
            uri?.let { backupViewModel.exportAllResources(it) }
        }

    private val importResourcesLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { backupViewModel.previewResourceImport(it) }
        }

    private val calculateOptimalCacheSizeUseCase by lazy { CalculateOptimalCacheSizeUseCase() }

    // All helpers are lazy - binding is only valid after onCreateView, and helpers are first
    // accessed from onViewCreated, so initialization is always safe.
    private val sectionsManager by lazy { CollapsibleSectionsManager(requireContext()) }
    private val resetHelper by lazy { GeneralSettingsResetHelper(binding, viewModel, this) }
    private val logHelper by lazy {
        GeneralSettingsLogHelper(
            binding = binding,
            fragment = this,
            saveLogsLauncher = saveLogsLauncher,
            gatherSystemInfoUseCase = gatherSystemInfoUseCase,
            getDestinationsUseCase = viewModel.getDestinationsUseCase,
            saveTextFileToResourceUseCase = saveTextFileToResourceUseCase,
        )
    }
    private val permissionsHelper by lazy {
        GeneralSettingsPermissionsHelper(binding, this, mediaPermissionsLauncher, notificationPermissionLauncher, requestContextualPermission, permissionRegistry)
    }
    private val importExportHelper by lazy {
        GeneralSettingsImportExportHelper(binding, viewModel, this, importSettingsFileLauncher)
    }
    private val credentialHelper by lazy {
        GeneralSettingsCredentialHelper(viewModel, this, importCredentialsLauncher)
    }
    private val cacheHelper by lazy {
        GeneralSettingsCacheHelper(binding, viewModel, this, audioMetadataCacheRepository, calculateOptimalCacheSizeUseCase, { isUpdatingSpinner = it })
    }
    private val backupHelper by lazy {
        GeneralSettingsBackupHelper(
            binding, this, backupViewModel, mediaCapabilities,
            importFavoritesLauncher, exportResourcesLauncher, importResourcesLauncher,
        )
    }
    private val googleAccountHelper by lazy {
        com.sza.fastmediasorter.ui.settings.helpers.GoogleAccountSettingsHelper(this, googleAccountViewModel, cctChecker)
    }
    private val prefetchHelper by lazy {
        GeneralSettingsPrefetchHelper(binding, viewModel, this, streamingCacheRepository)
    }
    private val observersHelper by lazy {
        GeneralSettingsObserversHelper(
            binding, viewModel, this, { isUpdatingSpinner }, { isUpdatingSpinner = it }, capabilityAvailability,
        )
    }
    private val viewSetupHelper by lazy {
        GeneralSettingsViewSetupHelper(
            binding, viewModel, this,
            { isUpdatingSpinner }, { isUpdatingSpinner = it },
            cacheHelper, permissionsHelper, importExportHelper, credentialHelper, logHelper, resetHelper,
            ensureAllFilesPredefinedResourceUseCase, remoteSourceAvailabilityGate
        )
    }
    // S0328: color theme spinner (Auto/Light/Dark) in General → Interface, after the language spinner.
    private val colorThemeHelper by lazy {
        com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsColorThemeHelper(
            binding, viewModel, this, { isUpdatingSpinner }, { isUpdatingSpinner = it }
        )
    }
    private val profileHelper by lazy {
        GeneralSettingsProfileHelper(binding, profileViewModel, this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsGeneralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as? ViewGroup)?.let(SettingsRowStackManager::stackNarrowPortraitRows)
        setupGmsBanner()
        setupSavedAuthorizationsRow()
        logHelper.setupVersionInfo()
        backupHelper.setupWearCompanionButton()
        // S0200 Phase 06: bind the new Google Account card after the layout is inflated.
        view.findViewById<View>(R.id.cardGoogleAccount)?.let { googleAccountHelper.bind(it) }
        viewSetupHelper.setup()
        colorThemeHelper.setup()
        profileHelper.setup()
        prefetchHelper.setup()
        collectOnLifecycle(viewModel.settings) { settings -> prefetchHelper.updateFromSettings(settings) }
        observersHelper.observeData()
        observersHelper.observeManualNetworkSyncState()
        observersHelper.refreshLastSyncStatus()
        cacheHelper.checkAndSuggestOptimalCacheSize()
        setupGeneralLayouts()
        setupCollapsibleSections()
        setupScreenshotTestButton()
        backupHelper.setupBackupButtons()
        backupHelper.observeBackupState()
        backupHelper.updateBackupAccountInfo()
        backupHelper.setupExportImportButtons()
        backupHelper.observeExportImportState()
        collectOnLifecycle(viewModel.deviceStorage) { state ->
            val text = when (state) {
                is DeviceStorageState.Success -> String.format("%.1f GB", state.availableGb)
                is DeviceStorageState.Error -> state.message
            }
            binding.textDeviceStorageValue?.text = text
        }
        binding.btnDeviceStorageRefresh?.setOnClickListener {
            viewModel.refreshDeviceStorage()
        }
        binding.btnPermissionsManagement?.setOnClickListener {
            requireActivity().supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, PermissionsManagementFragment())
                .addToBackStack(null)
                .commit()
        }
        // S0386 Phase 10: app-wide Downloadable Extensions aggregator lives on the General tab. Uses
        // the activity FragmentManager so the full-screen overlay attaches to a real container.
        // S0547: hide on flavors with nothing to download (lite/photos) - mirrors the welcome page gate.
        if (!capabilityAvailability.isExtensionsScreenAvailable()) {
            binding.btnDownloadableExtensions?.visibility = View.GONE
        } else {
            binding.btnDownloadableExtensions?.setOnClickListener {
                requireActivity().supportFragmentManager
                    .beginTransaction()
                    .add(android.R.id.content, ExtensionsManagerFragment(), ExtensionsManagerFragment.TAG)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGeneralLayouts()
    }

    override fun onResume() {
        super.onResume()
        permissionsHelper.updatePermissionButtonsState()
        cacheHelper.updateCacheSize()
        observersHelper.refreshLastSyncStatus()
    }

    override fun onDestroyView() {
        observersHelper.dismissManualSyncProgressDialog()
        super.onDestroyView()
        _binding = null
    }

    // S0255: Wire the saved-authorizations row inside the new "Authorization" group.
    // S0567: the SettingsSelectionRow owns the tooltip via ssr_help*; only the row click is wired here.
    // Row stays always enabled per strategic decision §6.2.
    private fun setupSavedAuthorizationsRow() {
        binding.rowSavedAuthorizations.setOnRowClickListener {
            AuthSessionsActivity.start(requireContext())
        }
    }

    // S1052: menu-screenshot test is a debug-only tool relocated here from the destinations tab. The
    // enclosing debug section is already BuildConfig.DEBUG-gated; the button adds the flavor axis -
    // shown only when a MenuScreenshotLauncher is bound (standard + noLegal). Mirrors the composite
    // gate in SettingsSearchCapabilityGate so search visibility cannot drift from UI visibility.
    private fun setupScreenshotTestButton() {
        val launcher = menuScreenshotLaunchers.firstOrNull()
        binding.btnTakeScreenshotNow.isVisible = BuildConfig.DEBUG && launcher != null
        if (BuildConfig.DEBUG && launcher != null) {
            binding.btnTakeScreenshotNow.setOnClickListener {
                Timber.d("S1052: screenshot-test tapped from General-tab debug section")
                launcher.launch(requireActivity())
            }
        }
    }

    private fun setupGmsBanner() {
        if (com.sza.fastmediasorter.core.util.GmsAvailabilityChecker.isOk) {
            binding.tvGmsSettingsLink.visibility = View.GONE
            return
        }
        binding.tvGmsSettingsLink.visibility = View.VISIBLE
        binding.tvGmsSettingsLink.setOnClickListener {
            try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=com.google.android.gms")))
            } catch (e: Exception) {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")))
            }
        }
    }

    // S0535: unified collapsible groups - one orchestrator + consolidated store, default collapsed.
    // Invisible headers (flavor-gated sections) are skipped so their bodies stay hidden.
    private fun setupCollapsibleSections() {
        fun register(header: CollapsibleSectionHeader, container: View, key: String) {
            if (!header.isVisible) return
            sectionsManager.register(header, container, key, defaultExpanded = false)
        }
        register(binding.headerInterface, binding.containerInterface, "general__interface")
        register(
            binding.headerMainWindowInterface,
            binding.containerMainWindowInterface,
            "general__main_window_interface",
        )
        register(binding.headerFileBrowser, binding.containerFileBrowser, "general__file_browser")
        register(binding.headerRemoteSources, binding.containerRemoteSources, "general__remote_sources")
        register(binding.headerAuthorization, binding.containerAuthorization, "general__authorization")
        register(binding.headerAppData, binding.containerAppData, "general__app_data")
        register(binding.headerSystem, binding.containerSystem, "general__system")
        if (BuildConfig.DEBUG) {
            register(binding.headerDebugSettings, binding.containerDebugSettings, "general__debug")
        }
    }

    private fun setupGeneralLayouts() {
        val isWide = resources.configuration.isWideLayout()

        fun updateLayoutParams(view: View, isHorizontal: Boolean) {
            val params = view.layoutParams as LinearLayout.LayoutParams
            if (isHorizontal) { params.width = 0; params.weight = 1f }
            else { params.width = ViewGroup.LayoutParams.MATCH_PARENT; params.weight = 0f }
            view.layoutParams = params
        }

        binding.containerSync.orientation = if (isWide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        updateLayoutParams(binding.layoutEnableSync, isWide)
        updateLayoutParams(binding.layoutSyncControls, isWide)
    }
}
