package com.sza.fastmediasorter.ui.settings.revised.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.DeviceStorageState
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import com.sza.fastmediasorter.domain.usecase.CalculateOptimalCacheSizeUseCase
import com.sza.fastmediasorter.ui.settings.BackupRestoreViewModel
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsBackupHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsCacheHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsCredentialHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsImportExportHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsLogHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsObserversHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsPermissionsHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsPrefetchHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsResetHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsSectionsHelper
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsViewSetupHelper
import com.sza.fastmediasorter.ui.settings.fragments.PermissionsManagementFragment
import com.sza.fastmediasorter.ui.settings.revised.helpers.RevisedGeneralSectionBinder
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class RevisedGeneralSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsGeneralBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var audioMetadataCacheRepository: AudioMetadataCacheRepository
    @Inject lateinit var streamingCacheRepository: StreamingCacheRepository
    @Inject lateinit var requestContextualPermission: com.sza.fastmediasorter.domain.usecase.RequestContextualPermissionUseCase
    @Inject lateinit var permissionRegistry: com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository

    private val viewModel: SettingsViewModel by activityViewModels()
    private val backupViewModel: BackupRestoreViewModel by viewModels()

    private lateinit var sectionBinder: RevisedGeneralSectionBinder

    // Shared flag passed as lambdas to helpers that need to suppress listeners during programmatic updates.
    private var isUpdatingSpinner = false

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

    private val calculateOptimalCacheSizeUseCase by lazy { CalculateOptimalCacheSizeUseCase() }

    private val sectionsHelper by lazy { GeneralSettingsSectionsHelper(binding, this) }
    private val resetHelper by lazy { GeneralSettingsResetHelper(binding, viewModel, this) }
    private val logHelper by lazy { GeneralSettingsLogHelper(binding, this, saveLogsLauncher) }
    private val permissionsHelper by lazy {
        GeneralSettingsPermissionsHelper(
            binding,
            this,
            mediaPermissionsLauncher,
            notificationPermissionLauncher,
            requestContextualPermission,
            permissionRegistry,
        )
    }
    private val importExportHelper by lazy {
        GeneralSettingsImportExportHelper(binding, viewModel, this, importSettingsFileLauncher)
    }
    private val credentialHelper by lazy {
        GeneralSettingsCredentialHelper(viewModel, this, importCredentialsLauncher)
    }
    private val cacheHelper by lazy {
        GeneralSettingsCacheHelper(binding, viewModel, this, audioMetadataCacheRepository, calculateOptimalCacheSizeUseCase)
    }
    private val backupHelper by lazy {
        GeneralSettingsBackupHelper(binding, this, backupViewModel)
    }
    private val prefetchHelper by lazy {
        GeneralSettingsPrefetchHelper(binding, viewModel, this, streamingCacheRepository)
    }
    private val observersHelper by lazy {
        GeneralSettingsObserversHelper(binding, viewModel, this, { isUpdatingSpinner }, { isUpdatingSpinner = it })
    }
    private val viewSetupHelper by lazy {
        GeneralSettingsViewSetupHelper(
            binding,
            viewModel,
            this,
            { isUpdatingSpinner },
            { isUpdatingSpinner = it },
            cacheHelper,
            permissionsHelper,
            importExportHelper,
            credentialHelper,
            logHelper,
            resetHelper,
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_settings_revised_general, container, false)
        val includedContent = root.findViewById<View>(R.id.revisedGeneralLegacyContent)
        _binding = FragmentSettingsGeneralBinding.bind(includedContent)
        sectionBinder = RevisedGeneralSectionBinder(binding.root)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sectionBinder.applyRevisedLayout()
        setupGmsBanner()
        logHelper.setupVersionInfo()
        backupHelper.setupWearCompanionButton()
        viewSetupHelper.setup()
        prefetchHelper.setup()
        collectOnLifecycle(viewModel.settings) { settings -> prefetchHelper.updateFromSettings(settings) }
        observersHelper.observeData()
        observersHelper.observeManualNetworkSyncState()
        observersHelper.refreshLastSyncStatus()
        cacheHelper.checkAndSuggestOptimalCacheSize()
        setupGeneralLayouts()
        sectionsHelper.setup()
        backupHelper.setupBackupButtons()
        backupHelper.observeBackupState()
        backupHelper.updateBackupAccountInfo()
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

    fun ensureSectionExpanded(sectionId: String) {
        sectionBinder.ensureSectionExpanded(sectionId)
    }

    private fun setupGmsBanner() {
        if (com.sza.fastmediasorter.core.util.GmsAvailabilityChecker.isOk) {
            binding.tvGmsSettingsLink.visibility = View.GONE
            return
        }
        binding.tvGmsSettingsLink.visibility = View.VISIBLE
        binding.tvGmsSettingsLink.setOnClickListener {
            try {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id=com.google.android.gms"),
                    ),
                )
            } catch (e: Exception) {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms"),
                    ),
                )
            }
        }
    }

    private fun setupGeneralLayouts() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        fun updateLayoutParams(view: View, isHorizontal: Boolean) {
            val params = view.layoutParams as LinearLayout.LayoutParams
            if (isHorizontal) {
                params.width = 0
                params.weight = 1f
            } else {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.weight = 0f
            }
            view.layoutParams = params
        }

        binding.containerSync.orientation = if (isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        updateLayoutParams(binding.layoutEnableSync, isLandscape)
        updateLayoutParams(binding.layoutSyncControls, isLandscape)
    }
}