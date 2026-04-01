package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.DebugToolsBridge
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.usecase.RestoreFromGoogleDriveUseCase
import com.sza.fastmediasorter.ui.dialog.MaterialProgressDialog
import com.sza.fastmediasorter.ui.settings.BackupRestoreUiState
import com.sza.fastmediasorter.ui.settings.BackupRestoreViewModel
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import com.sza.fastmediasorter.utils.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class GeneralSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsGeneralBinding? = null
    private val binding get() = _binding!!
    
    @Inject lateinit var audioMetadataCacheRepository: AudioMetadataCacheRepository

    private val viewModel: SettingsViewModel by activityViewModels()
    private val backupViewModel: BackupRestoreViewModel by viewModels()

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Timber.d("Sign-in activity result: resultCode=${result.resultCode}")
        var apiErrorCode: Int? = null
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(com.google.android.gms.common.api.ApiException::class.java)
        } catch (e: com.google.android.gms.common.api.ApiException) {
            apiErrorCode = e.statusCode
            Timber.w("Sign-in failed (code=${e.statusCode}), trying last signed-in account")
            GoogleSignIn.getLastSignedInAccount(requireContext())
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error parsing Google sign-in result")
            GoogleSignIn.getLastSignedInAccount(requireContext())
        }
        backupViewModel.handleSignInResult(account, apiErrorCode)
    }
    
    companion object {
        private const val PREFS_NAME = "general_sections_state"
        private const val KEY_INTERFACE_EXPANDED = "section_interface_expanded"
        private const val KEY_FILES_EXPANDED = "section_files_expanded"
        private const val KEY_SYSTEM_EXPANDED = "section_system_expanded"
        private const val KEY_DEBUG_EXPANDED = "section_debug_expanded"
    }
    
    private val collapsibleSections = mutableListOf<CollapsibleSection>()
    
    private val importCredentialsLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { safeUri ->
            importCredentialsFromUri(safeUri)
        }
    }
    
    private val importSettingsFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { safeUri ->
            importSettingsFromUri(safeUri)
        }
    }

    private val saveLogsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val result = com.sza.fastmediasorter.core.logging.LogExportHelper.writeZipToUri(requireContext(), uri)
            when (result) {
                com.sza.fastmediasorter.core.logging.LogExportHelper.ExportResult.SaveSuccess ->
                    Toast.makeText(requireContext(), R.string.save_logs_success, Toast.LENGTH_SHORT).show()
                com.sza.fastmediasorter.core.logging.LogExportHelper.ExportResult.NoLogs ->
                    Toast.makeText(requireContext(), R.string.export_logs_no_files, Toast.LENGTH_SHORT).show()
                is com.sza.fastmediasorter.core.logging.LogExportHelper.ExportResult.Error ->
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }
    }

    private val mediaPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionButtonsState()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionButtonsState()
    }
    
    // Lazy initialization of CalculateOptimalCacheSizeUseCase
    private val calculateOptimalCacheSizeUseCase by lazy {
        com.sza.fastmediasorter.domain.usecase.CalculateOptimalCacheSizeUseCase()
    }
    
    // Flag to prevent infinite loop when programmatically updating spinner
    private var isUpdatingSpinner = false
    private var manualSyncProgressDialog: MaterialProgressDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsGeneralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupVersionInfo()
        setupViews()
        observeData()
        observeManualNetworkSyncState()
        refreshLastSyncStatus()
        checkAndSuggestOptimalCacheSize()
        setupGeneralLayouts()
        setupExpandableSections()
        setupBackupButtons()
        observeBackupState()
        updateBackupAccountInfo()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGeneralLayouts()
    }

    private fun setupGeneralLayouts() {
        val orientation = resources.configuration.orientation
        val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        
        // Helper to update layout params
        fun updateLayoutParams(view: android.view.View, isHorizontal: Boolean) {
            val params = view.layoutParams as android.widget.LinearLayout.LayoutParams
            if (isHorizontal) {
                params.width = 0
                params.weight = 1f
            } else {
                params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                params.weight = 0f
            }
            view.layoutParams = params
        }

        // Sync Container: Enable Sync + Interval + Sync Now
        binding.containerSync.orientation = if (isLandscape) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL
        updateLayoutParams(binding.layoutEnableSync, isLandscape)
        updateLayoutParams(binding.layoutSyncControls, isLandscape)

        // Confirm Delete + Move Container
        binding.containerConfirm.orientation = if (isLandscape) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL
        updateLayoutParams(binding.layoutConfirmDelete, isLandscape)
        updateLayoutParams(binding.layoutConfirmMove, isLandscape)

        // Cache Container: Size limit + Auto + Clear + Size info
        // binding.containerCache is now a ConstraintLayout and handles its own layout
        // binding.containerCache.orientation = if (isLandscape) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL
        // updateLayoutParams(binding.layoutCacheControls, isLandscape)
        // updateLayoutParams(binding.layoutCacheActions, isLandscape)
    }
    
    private fun checkAndSuggestOptimalCacheSize() {
        // Only suggest on first install (when user hasn't modified cache size manually)
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = viewModel.settings.value
            if (!settings.isCacheSizeUserModified) {
                val optimalSizeMb = calculateOptimalCacheSizeUseCase()
                // Only show suggestion if current size differs from optimal
                if (settings.cacheSizeMb != optimalSizeMb) {
                    showOptimalCacheSizeSuggestion(optimalSizeMb)
                }
            }
        }
    }
    
    private fun showOptimalCacheSizeSuggestion(optimalSizeMb: Int) {
        val storageInfo = calculateOptimalCacheSizeUseCase.getStorageInfo()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.optimize_cache_title)
            .setMessage(getString(R.string.optimize_cache_message, optimalSizeMb, storageInfo))
            .setPositiveButton(R.string.apply) { _, _ ->
                showCacheSizeRestartDialog(optimalSizeMb, isUserModified = false)
            }
            .setNegativeButton(R.string.keep_current) { _, _ ->
                // User declined - mark as user-modified to prevent future suggestions
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(isCacheSizeUserModified = true))
            }
            .setCancelable(false)
            .show()
    }
    
    private fun setupVersionInfo() {
        val versionInfo = "${com.sza.fastmediasorter.BuildConfig.VERSION_NAME} | Build ${com.sza.fastmediasorter.BuildConfig.VERSION_CODE} | sza@ukr.net"
        binding.tvVersionInfo.text = versionInfo
        
        // Make email clickable
        binding.tvVersionInfo.setOnClickListener {
            openEmailClient()
        }

        binding.tvVersionInfo.setOnLongClickListener {
            val intent = DebugToolsBridge.maybeCreateDebugMenuIntent(requireContext())
            if (intent != null) {
                startActivity(intent)
            } else {
                // Release build: share logs instead of opening debug menu
                shareLogs()
            }
            true
        }
    }
    
    private fun shareLogs() {
        val result = LogExportHelper.exportLogs(requireActivity())
        when (result) {
            is LogExportHelper.ExportResult.NoLogs ->
                Toast.makeText(requireContext(), R.string.export_logs_no_files, Toast.LENGTH_SHORT).show()
            is LogExportHelper.ExportResult.Error ->
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            else -> { /* share chooser launched */ }
        }
    }

    private fun openEmailClient() {
        val version = com.sza.fastmediasorter.BuildConfig.VERSION_NAME
        val subject = "About FastImageSorter (mobile) $version"
        val mailtoUri = android.net.Uri.parse("mailto:sza@ukr.net")
            .buildUpon()
            .appendQueryParameter("subject", subject)
            .build()
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = mailtoUri
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        
        try {
            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email)))
        } catch (e: Exception) {
            Timber.e(e, "Failed to open email client")
            Toast.makeText(
                requireContext(),
                R.string.no_email_client,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupViews() {
        // Language Spinner
        val languages = resources.getStringArray(com.sza.fastmediasorter.R.array.languages)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
        
        binding.spinnerLanguage.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Skip if we're programmatically updating the spinner
                if (isUpdatingSpinner) return
                
                val newLanguageCode = when (position) {
                    0 -> "en"
                    1 -> "ru"
                    2 -> "uk"
                    else -> "en"
                }
                
                // Check if language actually changed compared to current settings
                val currentSettings = viewModel.settings.value
                if (newLanguageCode != currentSettings.language) {
                    // Update settings
                    viewModel.updateSettings(currentSettings.copy(language = newLanguageCode))
                    
                    // Show restart dialog
                    showRestartDialog(newLanguageCode)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        
        // Prevent Sleep
        binding.switchPreventSleep.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(preventSleep = isChecked))
        }

        // Enable Favorites
        binding.switchEnableFavorites.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableFavorites = isChecked))
        }
        
        // Small Controls
        binding.switchSmallControls.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showSmallControls = isChecked))
        }
        
        // Safe Mode (Phase 2.1)
        binding.switchEnableSafeMode.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableSafeMode = isChecked))
            // Show/hide sub-options
            binding.layoutConfirmDelete.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.layoutConfirmMove.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.switchConfirmDelete.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(confirmDelete = isChecked))
        }
        
        binding.iconHelpSafeMode.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_safe_mode_title,
                R.string.tooltip_safe_mode_message
            )
        }
        
        binding.iconHelpFavorites.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_favorites_title,
                R.string.tooltip_favorites_message
            )
        }
        
        binding.iconHelpPreventSleep.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_prevent_sleep_title,
                R.string.tooltip_prevent_sleep_message
            )
        }
        
        binding.iconHelpCompactControls.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_compact_controls_title,
                R.string.tooltip_compact_controls_message
            )
        }
        
        binding.iconHelpDefaultCredentials.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_default_credentials_title,
                R.string.tooltip_default_credentials_message
            )
        }
        
        binding.switchAllFiles.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) {
                Timber.d("GeneralSettings: switchAllFiles listener blocked by isUpdatingSpinner")
                return@setOnCheckedChangeListener
            }
            Timber.d("GeneralSettings: User changed All Files to $isChecked")
            val current = viewModel.settings.value
            if (current.allFiles == isChecked) {
                Timber.d("GeneralSettings: All Files already $isChecked, ignoring duplicate event")
                return@setOnCheckedChangeListener
            }
            val updatedSettings = current.copy(allFiles = isChecked)
            viewModel.updateSettings(updatedSettings)
            // When All Files is turned off, also turn off Show Hidden Files
            if (!isChecked) {
                binding.switchShowHiddenFiles.isChecked = false
                // Use the already updated settings object instead of reading from viewModel.settings.value
                viewModel.updateSettings(updatedSettings.copy(showHiddenFiles = false))
            }
        }

        binding.iconHelpAllFiles.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_all_files_title,
                R.string.tooltip_all_files_message
            )
        }

        binding.switchShowHiddenFiles.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showHiddenFiles = isChecked))
        }

        binding.iconHelpShowHiddenFiles.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_show_hidden_files_title,
                R.string.tooltip_show_hidden_files_message
            )
        }
        
        // Show Subfolders as Items
        binding.switchShowSubfoldersAsItems.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showSubfoldersAsItems = isChecked))
        }

        binding.switchDefaultRememberFileList.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultRememberFileList = isChecked))
        }

        binding.btnHelpRememberFileList.setOnClickListener {
            showRememberFileListHelpDialog()
        }

        binding.iconHelpShowSubfolders.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.show_subfolders_as_items,
                R.string.show_subfolders_as_items_hint
            )
        }
        
        binding.switchConfirmMove.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(confirmMove = isChecked))
        }
        
        // Network Parallelism
        val parallelismOptions = arrayOf("1", "2", "4", "8", "12", "24")
        val parallelismAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, parallelismOptions)
        binding.actvNetworkParallelism.setAdapter(parallelismAdapter)
        
        binding.actvNetworkParallelism.setText(getString(R.string.number_format, viewModel.settings.value.networkParallelism), false)
        
        binding.actvNetworkParallelism.setOnItemClickListener { _, _, position, _ ->
            if (isUpdatingSpinner) return@setOnItemClickListener
            val limit = parallelismOptions[position].toInt()
            val current = viewModel.settings.value
            if (current.networkParallelism != limit) {
                viewModel.updateSettings(current.copy(networkParallelism = limit))
                // Update manager immediately
                com.sza.fastmediasorter.data.network.ConnectionThrottleManager.setUserNetworkLimit(limit)
            }
        }
        
        // Handle manual input
        binding.actvNetworkParallelism.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !isUpdatingSpinner) {
                val text = binding.actvNetworkParallelism.text.toString()
                val limit = text.toIntOrNull()
                if (limit != null && limit in 1..32) {
                    val current = viewModel.settings.value
                    if (current.networkParallelism != limit) {
                        viewModel.updateSettings(current.copy(networkParallelism = limit))
                        com.sza.fastmediasorter.data.network.ConnectionThrottleManager.setUserNetworkLimit(limit)
                    }
                } else {
                    // Invalid input, restore previous value
                    binding.actvNetworkParallelism.setText(getString(R.string.number_format, viewModel.settings.value.networkParallelism), false)
                }
            }
        }
        
        // Cache Size Limit
        val cacheSizeOptions = arrayOf("512", "1024", "2048", "4096", "8192", "16384")
        val cacheSizeAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, cacheSizeOptions)
        binding.actvCacheSizeLimit.setAdapter(cacheSizeAdapter)
        
        binding.actvCacheSizeLimit.setText(getString(R.string.number_format, viewModel.settings.value.cacheSizeMb), false)
        
        binding.actvCacheSizeLimit.setOnItemClickListener { _, _, position, _ ->
            if (isUpdatingSpinner) return@setOnItemClickListener
            val sizeMb = cacheSizeOptions[position].toInt()
            val current = viewModel.settings.value
            if (current.cacheSizeMb != sizeMb) {
                // Show restart dialog
                showCacheSizeRestartDialog(sizeMb)
            }
        }
        
        // Handle manual input for cache size
        binding.actvCacheSizeLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !isUpdatingSpinner) {
                val text = binding.actvCacheSizeLimit.text.toString()
                val sizeMb = text.toIntOrNull()
                if (sizeMb != null && sizeMb in 512..16384) {
                    val current = viewModel.settings.value
                    if (current.cacheSizeMb != sizeMb) {
                        // Show restart dialog
                        showCacheSizeRestartDialog(sizeMb)
                    }
                } else {
                    // Invalid input, restore previous value
                    binding.actvCacheSizeLimit.setText(getString(R.string.number_format, viewModel.settings.value.cacheSizeMb), false)
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Cache size must be between 512 and 16384 MB",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        
        // Network sync settings
        binding.switchEnableBackgroundSync.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableBackgroundSync = isChecked))
        }

        // X.11: Thumbnail preload toggle
        binding.switchEnableThumbnailPreload?.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableThumbnailPreload = isChecked))
            binding.layoutThumbnailPreloadWifiOnly?.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // X.11: Thumbnail preload Wi-Fi only sub-toggle
        binding.switchThumbnailPreloadWifiOnly?.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSpinner) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(thumbnailPreloadWifiOnly = isChecked))
        }
        
        // Sync interval dropdown
        val syncIntervalOptions = arrayOf("5", "15", "60", "120", "300")
        val syncAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, syncIntervalOptions)
        binding.actvSyncInterval?.let { syncIntervalView ->
            syncIntervalView.setAdapter(syncAdapter)
            
            // Convert hours to minutes for initial display
            val currentMinutes = viewModel.settings.value.backgroundSyncIntervalHours * 60
            syncIntervalView.setText(getString(R.string.number_format, currentMinutes), false)
            
            syncIntervalView.setOnItemClickListener { _, _, position, _ ->
                if (isUpdatingSpinner) return@setOnItemClickListener
                val minutes = syncIntervalOptions[position].toInt()
                val hours = (minutes / 60.0).toInt().coerceAtLeast(1) // At least 1 hour
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(backgroundSyncIntervalHours = hours))
            }
            
            // Handle manual input
            syncIntervalView.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !isUpdatingSpinner) {
                    val text = syncIntervalView.text.toString()
                    val minutes = text.toIntOrNull()
                    if (minutes != null && minutes >= 5) {
                        val hours = (minutes / 60.0).toInt().coerceAtLeast(1) // At least 1 hour
                        val current = viewModel.settings.value
                        viewModel.updateSettings(current.copy(backgroundSyncIntervalHours = hours))
                    } else {
                        // Invalid input, restore previous value
                        val previousMinutes = viewModel.settings.value.backgroundSyncIntervalHours * 60
                        syncIntervalView.setText(getString(R.string.number_format, previousMinutes), false)
                        android.widget.Toast.makeText(requireContext(), R.string.slide_interval_error, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        binding.btnSyncNow.setOnClickListener {
            if (viewModel.manualNetworkSyncState.value.inProgress) {
                viewModel.cancelManualNetworkSync()
            } else {
                viewModel.startManualNetworkSync()
            }
        }
        
        // Default User
        binding.etDefaultUser.setText(viewModel.settings.value.defaultUser)
        binding.etDefaultUser.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val current = viewModel.settings.value
                val newUser = binding.etDefaultUser.text.toString()
                if (current.defaultUser != newUser) {
                    viewModel.updateSettings(current.copy(defaultUser = newUser))
                    
                    // Check if user is "sza" and prompt to import resources
                    if (newUser.equals("sza", ignoreCase = true)) {
                        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Import Resources")
                            .setMessage("Add SZA resources?")
                            .setPositiveButton("Yes") { _, _ ->
                                viewModel.importSzaResources(requireContext())
                            }
                            .setNegativeButton("No", null)
                            .show()
                    }
                }
            }
        }
        
        // Default Password
        binding.etDefaultPassword.setText(viewModel.settings.value.defaultPassword)
        binding.etDefaultPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val current = viewModel.settings.value
                val newPassword = binding.etDefaultPassword.text.toString()
                if (current.defaultPassword != newPassword) {
                    viewModel.updateSettings(current.copy(defaultPassword = newPassword))
                }
            }
        }
        
        // User Guide Button
        binding.btnUserGuide.setOnClickListener {
            try {
                // Get current language from LocaleHelper (active language)
                val currentLanguage = LocaleHelper.getLanguage(requireContext())
                
                // Determine User Guide URL based on language
                val guideUrl = when (currentLanguage) {
                    "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/index-ru.html"
                    "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/index-uk.html"
                    else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/"
                }
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(guideUrl)
                }
                startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    "No browser found to open documentation",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        // Open Welcome Tutorial Button
        binding.btnOpenWelcome.setOnClickListener {
            startActivity(Intent(requireContext(), WelcomeActivity::class.java))
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            try {
                // Get current language from LocaleHelper (active language)
                val currentLanguage = LocaleHelper.getLanguage(requireContext())
                
                // Determine Privacy Policy URL based on language
                val privacyUrl = when (currentLanguage) {
                    "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.ru.html"
                    "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.uk.html"
                    else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.html"
                }
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(privacyUrl)
                }
                startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    "No browser found to open Privacy Policy",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnOpenSourceLicenses.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, OpenSourceLicensesFragment())
                .addToBackStack(null)
                .commit()
        }
        
        // Reset Settings Button
        binding.btnResetSettings.setOnClickListener {
            showResetSettingsConfirmation()
        }

        binding.btnResetGeneralSection.setOnClickListener {
            showResetGeneralSectionConfirmation()
        }

        binding.headerDebugSettings.isVisible = BuildConfig.DEBUG
        binding.containerDebugSettings.isVisible = BuildConfig.DEBUG
        
        // Integration Tests Button (DEBUG only)
        if (BuildConfig.DEBUG && com.sza.fastmediasorter.ui.settings.IntegrationTestDialog.isAvailable()) {
            binding.btnIntegrationTests.visibility = View.VISIBLE
            binding.btnIntegrationTests.setOnClickListener {
                com.sza.fastmediasorter.ui.settings.IntegrationTestDialog()
                    .show(childFragmentManager, com.sza.fastmediasorter.ui.settings.IntegrationTestDialog.TAG)
            }
            
            // Import Test Credentials Button (DEBUG only)
            binding.btnImportTestCredentials.visibility = View.VISIBLE
            binding.btnImportTestCredentials.setOnClickListener {
                importTestCredentials()
            }
        } else {
            binding.btnIntegrationTests.visibility = View.GONE
            binding.btnImportTestCredentials.visibility = View.GONE
        }
        
        // Export/Import Settings
        binding.btnExportSettings.setOnClickListener {
            showExportSettingsConfirmation()
        }
        
        binding.btnImportSettings.setOnClickListener {
            showImportSettingsConfirmation()
        }
        
        binding.btnLocalFilesPermission.setOnClickListener {
            handleLocalFilesPermissionAction()
        }
        
        binding.btnNetworkPermission.setOnClickListener {
            // Network permissions (INTERNET, ACCESS_NETWORK_STATE) are granted automatically
            // They are declared in AndroidManifest.xml and don't require runtime permissions
            Toast.makeText(
                requireContext(), 
                "Network permissions are already granted automatically", 
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // Manage Media Permission button (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            binding.btnManageMediaPermission.visibility = View.VISIBLE
        } else {
            binding.btnManageMediaPermission.visibility = View.GONE
        }
        
        // Update permission buttons state AFTER visibility is set
        updatePermissionButtonsState()
        
        // Log Buttons
        binding.btnShowLog.setOnClickListener {
            showLogDialog(fullLog = true)
        }
        
        binding.btnShowSessionLog.setOnClickListener {
            showLogDialog(fullLog = false)
        }

        binding.btnShareLogs?.setOnClickListener {
            when (val result = com.sza.fastmediasorter.core.logging.LogExportHelper.exportLogs(requireContext())) {
                com.sza.fastmediasorter.core.logging.LogExportHelper.ExportResult.Success -> {
                    Unit
                }
                com.sza.fastmediasorter.core.logging.LogExportHelper.ExportResult.NoLogs -> {
                    Toast.makeText(requireContext(), getString(R.string.export_logs_no_files), Toast.LENGTH_SHORT).show()
                }
                is com.sza.fastmediasorter.core.logging.LogExportHelper.ExportResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }

        binding.btnSaveLogs?.setOnClickListener {
            saveLogsLauncher.launch("fastmediasorter_logs.zip")
        }
        
        // Cache Management
        binding.btnAutoCalculateCache.setOnClickListener {
            autoCalculateCacheSize()
        }
        
        binding.btnClearCache.setOnClickListener {
            clearCache()
        }
        
        binding.btnResetSmbConnections.setOnClickListener {
            resetSmbConnections()
        }
        
        // Update cache size on view creation
        updateCacheSize()
    }

    override fun onResume() {
        super.onResume()
        // Update permission buttons state when returning from system settings
        updatePermissionButtonsState()
        // Update cache size when returning to fragment
        updateCacheSize()
        refreshLastSyncStatus()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    // Update language spinner
                    val languagePosition = when (settings.language) {
                        "en" -> 0
                        "ru" -> 1
                        "uk" -> 2
                        else -> 0
                    }
                    if (binding.spinnerLanguage.selectedItemPosition != languagePosition) {
                        // Set flag to prevent triggering onItemSelected
                        isUpdatingSpinner = true
                        binding.spinnerLanguage.setSelection(languagePosition, false)
                        // Reset flag after a short delay to allow UI to settle
                        binding.spinnerLanguage.post {
                            isUpdatingSpinner = false
                        }
                    }
                    
                    // Update switches (with protection from triggering listeners)
                    isUpdatingSpinner = true
                    
                    if (binding.switchPreventSleep.isChecked != settings.preventSleep) {
                        binding.switchPreventSleep.isChecked = settings.preventSleep
                    }
                    if (binding.switchEnableFavorites.isChecked != settings.enableFavorites) {
                        binding.switchEnableFavorites.isChecked = settings.enableFavorites
                    }
                    if (binding.switchSmallControls.isChecked != settings.showSmallControls) {
                        binding.switchSmallControls.isChecked = settings.showSmallControls
                    }
                    
                    if (binding.switchAllFiles.isChecked != settings.allFiles) {
                        Timber.d("GeneralSettings: observeData updating switchAllFiles: ${binding.switchAllFiles.isChecked} -> ${settings.allFiles}")
                        binding.switchAllFiles.isChecked = settings.allFiles
                    }
                    
                    // Show Hidden Files (depends on All Files)
                    binding.layoutShowHiddenFiles.visibility = if (settings.allFiles) View.VISIBLE else View.GONE
                    if (binding.switchShowHiddenFiles.isChecked != settings.showHiddenFiles) {
                        binding.switchShowHiddenFiles.isChecked = settings.showHiddenFiles
                    }
                    
                    // Show Subfolders as Items
                    if (binding.switchShowSubfoldersAsItems.isChecked != settings.showSubfoldersAsItems) {
                        binding.switchShowSubfoldersAsItems.isChecked = settings.showSubfoldersAsItems
                    }
                    if (binding.switchDefaultRememberFileList.isChecked != settings.defaultRememberFileList) {
                        binding.switchDefaultRememberFileList.isChecked = settings.defaultRememberFileList
                    }
                    
                    // Safe Mode (Phase 2.1)
                    if (binding.switchEnableSafeMode.isChecked != settings.enableSafeMode) {
                        binding.switchEnableSafeMode.isChecked = settings.enableSafeMode
                    }
                    // Show/hide sub-options based on enableSafeMode
                    binding.layoutConfirmDelete.visibility = if (settings.enableSafeMode) View.VISIBLE else View.GONE
                    binding.layoutConfirmMove.visibility = if (settings.enableSafeMode) View.VISIBLE else View.GONE
                    
                    if (binding.switchConfirmDelete.isChecked != settings.confirmDelete) {
                        binding.switchConfirmDelete.isChecked = settings.confirmDelete
                    }
                    if (binding.switchConfirmMove.isChecked != settings.confirmMove) {
                        binding.switchConfirmMove.isChecked = settings.confirmMove
                    }
                    
                    if (binding.switchEnableBackgroundSync.isChecked != settings.enableBackgroundSync) {
                        binding.switchEnableBackgroundSync.isChecked = settings.enableBackgroundSync
                    }

                    // X.11: Thumbnail preload
                    binding.switchEnableThumbnailPreload?.let { sw ->
                        if (sw.isChecked != settings.enableThumbnailPreload) sw.isChecked = settings.enableThumbnailPreload
                    }
                    binding.layoutThumbnailPreloadWifiOnly?.visibility =
                        if (settings.enableThumbnailPreload) View.VISIBLE else View.GONE
                    binding.switchThumbnailPreloadWifiOnly?.let { sw ->
                        if (sw.isChecked != settings.thumbnailPreloadWifiOnly) sw.isChecked = settings.thumbnailPreloadWifiOnly
                    }

                    isUpdatingSpinner = false
                    
                    // Update network parallelism
                    val currentParallelism = binding.actvNetworkParallelism.text.toString().toIntOrNull()
                    if (currentParallelism != settings.networkParallelism) {
                        binding.actvNetworkParallelism.setText(getString(R.string.number_format, settings.networkParallelism), false)
                    }
                    
                    // Update cache size limit
                    val currentCacheSize = binding.actvCacheSizeLimit.text.toString().toIntOrNull()
                    if (currentCacheSize != settings.cacheSizeMb) {
                        binding.actvCacheSizeLimit.setText(getString(R.string.number_format, settings.cacheSizeMb), false)
                    }
                    
                    // Update sync interval (convert hours to minutes)
                    val syncMinutes = settings.backgroundSyncIntervalHours * 60
                    binding.actvSyncInterval?.let { syncIntervalView ->
                        val displayedText = syncIntervalView.text.toString()
                        if (displayedText != syncMinutes.toString()) {
                            syncIntervalView.setText(getString(R.string.number_format, syncMinutes), false)
                        }
                    }
                }
            }
        }
    }

    private fun observeManualNetworkSyncState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.manualNetworkSyncState.collect { state ->
                    if (state.inProgress) {
                        binding.btnSyncNow.text = getString(R.string.cancel)
                        showOrUpdateManualSyncProgressDialog(state.processedCount, state.totalCount)
                        return@collect
                    }

                    binding.btnSyncNow.text = getString(R.string.sync_now)
                    dismissManualSyncProgressDialog()

                    when {
                        state.completed -> {
                            refreshLastSyncStatus()
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.sync_completed_successfully, state.successCount),
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.clearManualNetworkSyncTerminalState()
                        }

                        state.cancelled -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.dialog_file_operation_progress_btnCancel_text,
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.clearManualNetworkSyncTerminalState()
                        }

                        state.errorMessage != null -> {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.sync_failed, state.errorMessage),
                                Toast.LENGTH_LONG
                            ).show()
                            viewModel.clearManualNetworkSyncTerminalState()
                        }
                    }
                }
            }
        }
    }

    private fun showOrUpdateManualSyncProgressDialog(processedCount: Int, totalCount: Int) {
        if (!isAdded) {
            return
        }

        val dialog = manualSyncProgressDialog ?: MaterialProgressDialog(requireContext()).apply {
            setTitle(getString(R.string.sync_now))
            setProgressStyle(MaterialProgressDialog.STYLE_HORIZONTAL)
            show()
            manualSyncProgressDialog = this
        }

        val safeMax = maxOf(1, totalCount)
        val safeProgress = processedCount.coerceIn(0, safeMax)
        dialog.max = safeMax
        dialog.progress = safeProgress

        val message = if (totalCount > 0) {
            "${getString(R.string.sync_status_in_progress)} ($processedCount/$totalCount)"
        } else {
            getString(R.string.sync_status_in_progress)
        }
        dialog.setMessage(message)
    }

    private fun dismissManualSyncProgressDialog() {
        manualSyncProgressDialog?.dismiss()
        manualSyncProgressDialog = null
    }

    private fun refreshLastSyncStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            val lastSyncTimestamp = viewModel.getLastNetworkSyncTimestamp()
            if (!isAdded || _binding == null) {
                return@launch
            }

            val statusText = if (lastSyncTimestamp == null) {
                getString(R.string.never_synced)
            } else {
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    lastSyncTimestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
                getString(R.string.last_sync_time, relativeTime)
            }
            binding.tvSyncLastStatus?.text = statusText
        }
    }

    private fun showLogDialog(fullLog: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val logText = withContext(Dispatchers.IO) {
                if (fullLog) {
                    getFullLog()
                } else {
                    getSessionLog()
                }
            }

            if (!isAdded || _binding == null) return@launch

            com.sza.fastmediasorter.ui.common.DialogUtils.showScrollableDialog(
                requireContext(),
                if (fullLog) "Application Log" else "Current Session Log",
                logText,
                "Close"
            )
        }
    }

    private fun getFullLog(): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val bufferedReader = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream)
            )
            
            val log = StringBuilder()
            var lineCount = 0
            val maxLines = 512
            
            // Read last 512 lines
            val lines = bufferedReader.readLines()
            val startIndex = maxOf(0, lines.size - maxLines)
            
            for (i in startIndex until lines.size) {
                log.append(lines[i]).append("\n")
                lineCount++
            }
            
            bufferedReader.close()
            
            if (log.isEmpty()) {
                "No log entries found"
            } else {
                "Last $lineCount lines of log:\n\n$log"
            }
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    private fun getSessionLog(): String {
        return try {
            val packageName = requireContext().packageName
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val bufferedReader = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream)
            )
            
            val log = StringBuilder()
            var lineCount = 0
            
            bufferedReader.forEachLine { line ->
                // Filter only lines from current app
                if (line.contains(packageName, ignoreCase = true) || 
                    line.contains("FastMediaSorter", ignoreCase = true)) {
                    log.append(line).append("\n")
                    lineCount++
                }
            }
            
            bufferedReader.close()
            
            if (log.isEmpty()) {
                "No log entries found for current session"
            } else {
                "Current session log ($lineCount lines):\n\n$log"
            }
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Log", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.log_copied, Toast.LENGTH_SHORT).show()
    }

    private fun showRestartDialog(languageCode: String) {
        val languageName = LocaleHelper.getLanguageName(languageCode)
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.restart_app_title)
            .setMessage(getString(R.string.restart_app_message, languageName))
            .setPositiveButton(R.string.restart) { _, _ ->
                // Language already saved in DataStore by updateSettings()
                // Use changeLanguage() which handles Android 13+ LocaleManager correctly
                LocaleHelper.changeLanguage(requireActivity(), languageCode)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                // User declined restart - revert spinner to current active language
                // (the language that was loaded at app start, not the new selection)
                val currentLanguage = LocaleHelper.getLanguage(requireContext())
                val currentPosition = when (currentLanguage) {
                    "en" -> 0
                    "ru" -> 1
                    "uk" -> 2
                    else -> 0
                }
                isUpdatingSpinner = true
                binding.spinnerLanguage.setSelection(currentPosition, false)
                binding.spinnerLanguage.post { isUpdatingSpinner = false }
                
                // Revert settings to current active language
                val currentSettings = viewModel.settings.value
                viewModel.updateSettings(currentSettings.copy(language = currentLanguage))
                
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showCacheSizeRestartDialog(newCacheSizeMb: Int, isUserModified: Boolean = true) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.restart_app_title)
            .setMessage(getString(R.string.restart_app_cache_message, newCacheSizeMb))
            .setPositiveButton(R.string.restart) { _, _ ->
                // Save to DataStore with user-modified flag
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(
                    cacheSizeMb = newCacheSizeMb,
                    isCacheSizeUserModified = isUserModified
                ))
                
                // Save to SharedPreferences for Glide initialization
                requireContext().getSharedPreferences("glide_config", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putInt("cache_size_mb", newCacheSizeMb)
                    .apply()
                
                // Save the currently active language (resolved by LocaleHelper, not DataStore default)
                LocaleHelper.saveLanguage(requireContext(), LocaleHelper.getLanguage(requireContext()))
                
                // Restart app
                LocaleHelper.restartApp(requireActivity())
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                // User declined restart - revert to current cache size
                val currentCacheSize = viewModel.settings.value.cacheSizeMb
                isUpdatingSpinner = true
                binding.actvCacheSizeLimit.setText(getString(R.string.number_format, currentCacheSize), false)
                binding.actvCacheSizeLimit.post { isUpdatingSpinner = false }
                
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun updatePermissionButtonsState() {
        // Local files/media permission button adapts request/manage action
        val hasMediaPermissions = PermissionChecker.hasMediaPermissions(requireContext())
        binding.btnLocalFilesPermission.isEnabled = true
        binding.btnLocalFilesPermission.alpha = 1.0f
        binding.btnLocalFilesPermission.text = if (hasMediaPermissions) {
            getString(R.string.manage_local_files_permission)
        } else {
            getString(R.string.grant_local_files_permission)
        }
        binding.btnLocalFilesPermission.setOnClickListener {
            handleLocalFilesPermissionAction()
        }
        
        // Network Permission - always granted (normal permission), so always disabled
        val hasNetworkPermission = com.sza.fastmediasorter.core.util.PermissionHelper.hasInternetPermission(requireContext())
        binding.btnNetworkPermission.isEnabled = !hasNetworkPermission
        binding.btnNetworkPermission.alpha = if (hasNetworkPermission) 0.5f else 1.0f
        if (hasNetworkPermission) {
            binding.btnNetworkPermission.text = getString(R.string.network_permission_granted)
        } else {
            binding.btnNetworkPermission.text = getString(R.string.grant_network_permission)
        }
        
        // Manage Media Permission (Android 12+) - enable if not granted
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val hasManageMedia = com.sza.fastmediasorter.core.util.PermissionHelper.hasManageMediaPermission(requireContext())
            binding.btnManageMediaPermission.isEnabled = true
            binding.btnManageMediaPermission.alpha = 1.0f
            binding.btnManageMediaPermission.text = if (hasManageMedia) {
                getString(R.string.manage_manage_media_permission)
            } else {
                getString(R.string.grant_manage_media_permission)
            }
            binding.btnManageMediaPermission.setOnClickListener {
                if (com.sza.fastmediasorter.core.util.PermissionHelper.hasManageMediaPermission(requireContext())) {
                    openAppSettings()
                } else {
                    requestManageMediaPermission()
                }
            }
        }

        // Notification Permission (Android 13+, needed for background audio)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
            && com.sza.fastmediasorter.BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) {
            binding.btnNotificationPermission?.visibility = View.VISIBLE
            val hasNotification = androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            binding.btnNotificationPermission?.isEnabled = true
            binding.btnNotificationPermission?.alpha = 1.0f
            binding.btnNotificationPermission?.text = if (hasNotification) {
                getString(R.string.manage_notifications_permission)
            } else {
                getString(R.string.grant_notifications_permission)
            }
            binding.btnNotificationPermission?.setOnClickListener {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        requireContext(), android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    openAppSettings()
                } else {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            binding.btnNotificationPermission?.visibility = View.GONE
        }

        // Battery Optimization Permission — always shown so user can check/change
        val pm = requireContext().getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        binding.btnBatteryOptimizationPermission?.isEnabled = true
        binding.btnBatteryOptimizationPermission?.alpha = 1.0f
        binding.btnBatteryOptimizationPermission?.text = if (isIgnoring) {
            getString(R.string.manage_battery_optimization_permission)
        } else {
            getString(R.string.grant_battery_optimization_permission)
        }
        binding.btnBatteryOptimizationPermission?.setOnClickListener {
            val pmNow = requireContext().getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (pmNow.isIgnoringBatteryOptimizations(requireContext().packageName)) {
                // Already ignoring — open battery settings to let user revert
                try {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {
                    openAppSettings()
                }
            } else {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    openAppSettings()
                }
            }
        }
    }

    private fun handleLocalFilesPermissionAction() {
        if (PermissionChecker.hasMediaPermissions(requireContext())) {
            openAppSettings()
        } else {
            requestMediaPermissions()
        }
    }

    private fun requestMediaPermissions() {
        val permissions = PermissionChecker.getRequiredMediaPermissions()
        if (permissions.isEmpty()) {
            updatePermissionButtonsState()
            return
        }
        mediaPermissionsLauncher.launch(permissions)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }
    
    private fun requestManageMediaPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            com.sza.fastmediasorter.core.util.PermissionHelper.requestManageMediaPermission(requireActivity())
        }
    }
    
    private fun exportSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = viewModel.exportSettingsUseCase()
                
                result.onSuccess { _ ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_success),
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_failed, error.message ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Export settings failed")
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_failed, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showExportSettingsConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.export_settings_confirm_title)
            .setMessage(R.string.export_settings_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                exportSettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun importSettings() {
        // Show dialog with two import options
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_method_title)
            .setItems(arrayOf(
                getString(R.string.import_auto),
                getString(R.string.import_browse)
            )) { _, which ->
                when (which) {
                    0 -> importSettingsAuto() // Auto-find in Downloads
                    1 -> importSettingsFileLauncher.launch("*/*") // Browse for file
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showImportSettingsConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_settings_confirm_title)
            .setMessage(R.string.import_settings_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importSettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun importSettingsAuto() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = viewModel.importSettingsUseCase()
                
                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_success),
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Show restart dialog
                    showRestartAfterImportDialog()
                }.onFailure { error ->
                    // Show detailed error message to help user debug the issue
                    val message = error.message ?: "Unknown error"
                    val displayMessage = if (message.contains("File not found")) {
                        // Error already contains detailed information from ImportSettingsUseCase
                        message
                    } else {
                        getString(R.string.import_failed, message)
                    }
                    
                    // Show in a scrollable dialog for long error messages
                    com.sza.fastmediasorter.ui.common.DialogUtils.showScrollableDialog(
                        requireContext(),
                        getString(R.string.import_failed_title),
                        displayMessage,
                        getString(android.R.string.ok)
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Import settings failed")
                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_failed, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun importSettingsFromUri(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Timber.d("ImportSettings: Using SAF URI: $uri")
                val result = viewModel.importSettingsUseCase(uri.toString())
                
                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_success),
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Show restart dialog
                    showRestartAfterImportDialog()
                }.onFailure { error ->
                    val message = error.message ?: "Unknown error"
                    
                    com.sza.fastmediasorter.ui.common.DialogUtils.showScrollableDialog(
                        requireContext(),
                        getString(R.string.import_failed_title),
                        message,
                        getString(android.R.string.ok)
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Import settings from URI failed")
                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_failed, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun showRestartAfterImportDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.restart_required_title)
            .setMessage(R.string.restart_required_message)
            .setPositiveButton(R.string.restart_now) { _, _ ->
                // Use LocaleHelper for consistent restart behavior (handles Android 13+ correctly)
                LocaleHelper.restartApp(requireActivity())
            }
            .setNegativeButton(R.string.restart_later) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun updateCacheSize() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = requireContext().cacheDir
                val audioMetaCacheDir = java.io.File(requireContext().filesDir, AudioMetadataCacheRepository.CACHE_DIR_NAME)
                val totalSize = calculateDirectorySize(cacheDir) + calculateDirectorySize(audioMetaCacheDir)
                val formattedSize = formatFileSize(totalSize)

                // Проверяем лимит audio metadata cache
                val audioMetaCacheSize = audioMetadataCacheRepository.getCacheSize()

                withContext(Dispatchers.Main) {
                    binding.tvCacheSize.text = getString(R.string.cache_size_format, formattedSize)
                    if (audioMetaCacheSize > AudioMetadataCacheRepository.MAX_CACHE_BYTES) {
                        showAudioCacheSizeWarning()
                    }
                    // Reset button text to static "Clear Cache" in case passed from "calculating..." state
                    binding.btnClearCache.text = getString(R.string.clear_cache)
                    binding.btnClearCache.isEnabled = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to calculate cache size")
                withContext(Dispatchers.Main) {
                    binding.tvCacheSize.text = getString(R.string.cache_size_format, "N/A")
                    binding.btnClearCache.text = getString(R.string.clear_cache)
                    binding.btnClearCache.isEnabled = true
                }
            }
        }
    }
    
    private fun calculateDirectorySize(directory: java.io.File): Long {
        var size = 0L
        try {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating directory size: ${directory.absolutePath}")
        }
        return size
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format(Locale.getDefault(), "%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    
    private fun autoCalculateCacheSize() {
        val optimalSizeMb = calculateOptimalCacheSizeUseCase()
        val storageInfo = calculateOptimalCacheSizeUseCase.getStorageInfo()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.auto_calculate_cache_title)
            .setMessage(getString(R.string.auto_calculate_cache_message, optimalSizeMb, storageInfo))
            .setPositiveButton(R.string.apply) { _, _ ->
                val current = viewModel.settings.value
                if (current.cacheSizeMb != optimalSizeMb) {
                    // Update settings and show restart dialog (NOT marked as user-modified)
                    showCacheSizeRestartDialog(optimalSizeMb, isUserModified = false)
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.cache_size_already_optimal,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun clearCache() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_cache)
            .setMessage(R.string.clear_cache_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Show loading state
                binding.btnClearCache.isEnabled = false
                binding.btnClearCache.text = getString(R.string.cache_size_calculating)
                
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // 1. Clear Glide Disk Cache
                        try {
                            com.bumptech.glide.Glide.get(requireContext()).clearDiskCache()
                        } catch (e: Exception) {
                            Timber.e(e, "Glide clearDiskCache failed")
                        }
                        
                        // 2. Clear UnifiedFileCache (all network files)
                        try {
                            val app = requireActivity().application as com.sza.fastmediasorter.FastMediaSorterApp
                            val stats = app.unifiedCache.getCacheStats()
                            app.unifiedCache.clearAll()
                            Timber.d("Cleared UnifiedFileCache: ${stats.totalSizeMB} MB")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to clear UnifiedFileCache")
                        }
                        
                        // 3. Clear other cache files manually (including translation cache in memory)
                        val cacheDir = requireContext().cacheDir
                        deleteRecursive(cacheDir)
                        
                        // Clear global translation cache
                        com.sza.fastmediasorter.core.cache.TranslationCacheManager.clearAll()
                        
                        // 4. Clear MediaFilesCacheManager (cached file lists for resources)
                        try {
                            com.sza.fastmediasorter.core.cache.MediaFilesCacheManager.clearAllCaches()
                            Timber.d("Cleared MediaFilesCacheManager (resource file lists)")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to clear MediaFilesCacheManager")
                        }

                        // 5. Clear DB-cached file lists
                        try {
                            val app = requireActivity().application as com.sza.fastmediasorter.FastMediaSorterApp
                            app.cachedFileListRepository.deleteAllCachedFiles()
                            Timber.d("Cleared CachedFileListRepository (DB-cached file lists)")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to clear CachedFileListRepository")
                        }
                        
                        // 6. Clear playback positions
                        try {
                            val app = requireActivity().application as com.sza.fastmediasorter.FastMediaSorterApp
                            app.playbackPositionRepository.deleteAllPositions()
                            Timber.d("Cleared all playback positions")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to clear playback positions")
                        }

                        // 7. Clear audio metadata cache (filesDir/audio_metadata_cache/)
                        try {
                            audioMetadataCacheRepository.clearCache()
                            Timber.d("Cleared AudioMetadataCache")
                        } catch (e: Exception) {
                            Timber.d(e, "Failed to clear AudioMetadataCache")
                        }

                        withContext(Dispatchers.Main) {
                            // 7. Clear Glide Memory Cache
                            try {
                                com.bumptech.glide.Glide.get(requireContext()).clearMemory()
                            } catch (e: Exception) {
                                Timber.e(e, "Glide clearMemory failed")
                            }
                            
                            Toast.makeText(
                                requireContext(),
                                R.string.cache_cleared,
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            binding.btnClearCache.isEnabled = true
                            updateCacheSize()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to clear cache")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                R.string.cache_clear_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.btnClearCache.isEnabled = true
                            updateCacheSize()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAudioCacheSizeWarning() {
        if (!isAdded || activity?.isFinishing == true) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.audio_cache_size_warning_title)
            .setMessage(R.string.audio_cache_size_warning_message)
            .setPositiveButton(R.string.delete_old_files) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    audioMetadataCacheRepository.trimIfNeeded()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRememberFileListHelpDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.remember_file_list_help_title)
            .setMessage(R.string.remember_file_list_help_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun resetSmbConnections() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_smb_connections_title)
            .setMessage(R.string.reset_smb_connections_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Show loading state
                binding.btnResetSmbConnections.isEnabled = false
                binding.btnResetSmbConnections.text = getString(R.string.please_wait)
                
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // Reset all SMB connections and error state
                        viewModel.resetSmbConnectionsUseCase()
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                R.string.reset_smb_success,
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.btnResetSmbConnections.isEnabled = true
                            binding.btnResetSmbConnections.text = getString(R.string.reset_smb_connections)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to reset SMB connections")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to reset SMB connections: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            binding.btnResetSmbConnections.isEnabled = true
                            binding.btnResetSmbConnections.text = getString(R.string.reset_smb_connections)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun deleteRecursive(fileOrDirectory: java.io.File): Boolean {
        return try {
            if (fileOrDirectory.isDirectory) {
                fileOrDirectory.listFiles()?.forEach { child ->
                    deleteRecursive(child)
                }
            }
            // Delete files inside cache dir but not the cache dir itself
            if (fileOrDirectory != requireContext().cacheDir) {
                fileOrDirectory.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting: ${fileOrDirectory.absolutePath}")
            false
        }
    }

    /**
     * Show confirmation dialog before resetting all settings to defaults
     */
    private fun showResetSettingsConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_settings_title)
            .setMessage(R.string.reset_settings_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                resetSettingsToDefaults()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showResetGeneralSectionConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_general_section_title)
            .setMessage(R.string.reset_general_section_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                resetGeneralSection()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resetGeneralSection() {
        lifecycleScope.launch {
            try {
                viewModel.resetGeneralSection()
                Toast.makeText(
                    requireContext(),
                    R.string.reset_general_section_success,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset general section")
                Toast.makeText(
                    requireContext(),
                    getString(R.string.reset_general_section_failed, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Reset all settings to default values (preserves resources and destinations)
     */
    private fun resetSettingsToDefaults() {
        lifecycleScope.launch {
            try {
                viewModel.resetToDefaults()
                Toast.makeText(
                    requireContext(),
                    R.string.reset_settings_success,
                    Toast.LENGTH_LONG
                ).show()
                
                // UI will automatically update via observeData()
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset settings")
                Toast.makeText(
                    requireContext(),
                    "Failed to reset settings: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Import test credentials using System File Picker (SAF)
     * Solves EACCES/Permission Denied issues on Android 10+
     */
    private fun importTestCredentials() {
        try {
            importCredentialsLauncher.launch(arrayOf("application/json", "*/*"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to launch file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importCredentialsFromUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                Timber.i("Importing test credentials from URI: $uri")
                
                val json = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    }
                }
                
                if (json.isNullOrBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "File is empty or could not be read",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                val jsonObject = org.json.JSONObject(json)
                
                var credentialsImported = 0
                var resourcesImported = 0
                var settingsImported = false
                
                // Import credentials
                val credentialNameMap = mutableMapOf<String, String>() // Map JSON name -> UUID

                if (jsonObject.has("credentials")) {
                    val credArray = jsonObject.getJSONArray("credentials")
                    for (i in 0 until credArray.length()) {
                        val cred = credArray.getJSONObject(i)
                        val name = cred.getString("name")
                        val uuid = java.util.UUID.randomUUID().toString()
                        
                        // Note: cloudProvider is not stored in NetworkCredentialsEntity, it's part of the Resource or handled by logic
                        val credentials = com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity.create(
                            credentialId = uuid,
                            type = cred.getString("type"),
                            server = cred.getString("server"),
                            port = cred.optInt("port", 445),
                            username = cred.optString("username", ""),
                            plaintextPassword = cred.optString("password", ""),
                            domain = cred.optString("domain", ""),
                            shareName = cred.optString("shareName", "")
                        )
                        viewModel.addCredentials(credentials)
                        credentialNameMap[name] = uuid
                        credentialsImported++
                    }
                }
                
                // Import resources
                if (jsonObject.has("resources")) {
                    val resArray = jsonObject.getJSONArray("resources")
                    for (i in 0 until resArray.length()) {
                        val res = resArray.getJSONObject(i)
                        
                        // Find credentials by name using our map
                        var credentialsId: String? = null
                        if (res.has("credentialsName")) {
                            val credName = res.getString("credentialsName")
                            credentialsId = credentialNameMap[credName]
                        }
                        
                        val mediaTypes = mutableSetOf<com.sza.fastmediasorter.domain.model.MediaType>()
                        if (res.has("supportedMediaTypes")) {
                            val typesArray = res.getJSONArray("supportedMediaTypes")
                            for (j in 0 until typesArray.length()) {
                                mediaTypes.add(com.sza.fastmediasorter.domain.model.MediaType.valueOf(typesArray.getString(j)))
                            }
                        }
                        
                        val resource = com.sza.fastmediasorter.domain.model.MediaResource(
                            id = 0,
                            name = res.getString("name"),
                            path = res.getString("path"),
                            type = com.sza.fastmediasorter.domain.model.ResourceType.valueOf(res.getString("type")),
                            createdDate = System.currentTimeMillis(),
                            fileCount = 0,
                            isDestination = res.optBoolean("isDestination", false),
                            destinationOrder = if (res.optBoolean("isDestination", false)) res.optInt("destinationOrder", 0) else null,
                            credentialsId = credentialsId,
                            isWritable = true,
                            scanSubdirectories = res.optBoolean("scanSubdirectories", true),
                            supportedMediaTypes = mediaTypes,
                            slideshowInterval = 10,
                            allFiles = false,
                            cloudProvider = if (res.has("type") && res.getString("type") == "CLOUD" && res.has("cloudProvider"))
                                com.sza.fastmediasorter.data.cloud.CloudProvider.valueOf(res.getString("cloudProvider"))
                                else null
                        )
                        viewModel.addResourceDirectly(resource)
                        resourcesImported++
                    }
                }
                
                // Import settings
                if (jsonObject.has("settings")) {
                    val settings = jsonObject.getJSONObject("settings")
                    val currentSettings = viewModel.settings.value
                    
                    val updatedSettings = currentSettings.copy(
                        defaultSortMode = if (settings.has("defaultSortMode")) 
                            com.sza.fastmediasorter.domain.model.SortMode.valueOf(settings.getString("defaultSortMode")) 
                            else currentSettings.defaultSortMode,
                        slideshowInterval = settings.optInt("slideshowInterval", currentSettings.slideshowInterval),
                        networkParallelism = settings.optInt("parallelDownloads", currentSettings.networkParallelism),
                        cacheSizeMb = settings.optInt("cacheSizeMb", currentSettings.cacheSizeMb),
                        supportImages = settings.optBoolean("supportImages", currentSettings.supportImages),
                        supportVideos = settings.optBoolean("supportVideos", currentSettings.supportVideos),
                        supportAudio = settings.optBoolean("supportAudio", currentSettings.supportAudio),
                        supportGifs = settings.optBoolean("supportGifs", currentSettings.supportGifs),
                        supportText = settings.optBoolean("supportText", currentSettings.supportText),
                        supportPdf = settings.optBoolean("supportPdf", currentSettings.supportPdf),
                        imageSizeMin = settings.optLong("imageMinSize", currentSettings.imageSizeMin),
                        imageSizeMax = settings.optLong("imageMaxSize", currentSettings.imageSizeMax),
                        videoSizeMin = settings.optLong("videoMinSize", currentSettings.videoSizeMin),
                        videoSizeMax = settings.optLong("videoMaxSize", currentSettings.videoSizeMax),
                        audioSizeMin = settings.optLong("audioMinSize", currentSettings.audioSizeMin),
                        audioSizeMax = settings.optLong("audioMaxSize", currentSettings.audioSizeMax),
                        showVideoThumbnails = settings.optBoolean("showVideoThumbnails", currentSettings.showVideoThumbnails),
                        showPdfThumbnails = settings.optBoolean("showPdfThumbnails", currentSettings.showPdfThumbnails),
                        loadFullSizeImages = settings.optBoolean("loadFullSizeImages", currentSettings.loadFullSizeImages),
                        preventSleep = settings.optBoolean("preventSleep", currentSettings.preventSleep),
                        showSmallControls = settings.optBoolean("showSmallControls", currentSettings.showSmallControls),
                        defaultGridMode = settings.optBoolean("gridMode", currentSettings.defaultGridMode),
                        defaultIconSize = settings.optInt("iconSize", currentSettings.defaultIconSize),
                        defaultShowCommandPanel = settings.optBoolean("showCommandPanel", currentSettings.defaultShowCommandPanel),
                        showDetailedErrors = settings.optBoolean("detailedErrors", currentSettings.showDetailedErrors),
                        confirmDelete = settings.optBoolean("confirmDelete", currentSettings.confirmDelete),
                        confirmMove = settings.optBoolean("confirmMove", currentSettings.confirmMove),
                        allowRename = settings.optBoolean("allowRename", currentSettings.allowRename),
                        allowDelete = settings.optBoolean("allowDelete", currentSettings.allowDelete),
                        enableCopying = settings.optBoolean("copyingEnabled", currentSettings.enableCopying),
                        enableMoving = settings.optBoolean("movingEnabled", currentSettings.enableMoving),
                        enableUndo = settings.optBoolean("undoEnabled", currentSettings.enableUndo),
                        maxRecipients = settings.optInt("maxRecipients", currentSettings.maxRecipients),
                        goToNextAfterCopy = settings.optBoolean("goToNextAfterCopy", currentSettings.goToNextAfterCopy)
                    )
                    
                    viewModel.updateSettings(updatedSettings)
                    settingsImported = true
                }
                
                val message = buildString {
                    append("✅ Import successful:\n")
                    append("• $credentialsImported credentials\n")
                    append("• $resourcesImported resources")
                    if (settingsImported) append("\n• Settings updated")
                }
                
                Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_LONG
                ).show()
                
                Timber.i("Import complete: $credentialsImported credentials, $resourcesImported resources, settings=$settingsImported")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to import test credentials")
                Toast.makeText(
                    requireContext(),
                    "Failed to import: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun setupExpandableSections() {
        val savedStates = getSavedSectionStates()
        
        bindSectionToggle(
            binding.headerInterface,
            binding.containerInterface,
            getString(R.string.settings_category_interface),
            KEY_INTERFACE_EXPANDED,
            savedStates[KEY_INTERFACE_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerFiles,
            binding.containerFiles,
            getString(R.string.settings_category_files),
            KEY_FILES_EXPANDED,
            savedStates[KEY_FILES_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerSystem,
            binding.containerSystem,
            getString(R.string.settings_category_system),
            KEY_SYSTEM_EXPANDED,
            savedStates[KEY_SYSTEM_EXPANDED] ?: false
        )

        if (BuildConfig.DEBUG) {
            bindSectionToggle(
                binding.headerDebugSettings,
                binding.containerDebugSettings,
                getString(R.string.debug_settings_title),
                KEY_DEBUG_EXPANDED,
                savedStates[KEY_DEBUG_EXPANDED] ?: false
            )
        }
    }

    private fun bindSectionToggle(
        header: android.widget.TextView, 
        content: View, 
        title: String,
        prefKey: String,
        initiallyExpanded: Boolean
    ) {
        if (!header.isVisible) return
        
        // Set initial state
        content.isVisible = initiallyExpanded
        updateHeader(header, title, initiallyExpanded)

        header.setOnClickListener {
            val expanded = !content.isVisible
            content.isVisible = expanded
            updateHeader(header, title, expanded)
            saveSectionState(prefKey, expanded)
        }
    }

    private fun updateHeader(header: android.widget.TextView, title: String, expanded: Boolean) {
        val prefix = if (expanded) "▼" else "▶"
        header.text = getString(R.string.string_format_two_args, prefix, title)
    }
    
    /**
     * Get saved section states from SharedPreferences.
     * Wrapped in StrictModeHelper to avoid violations during fragment creation.
     */
    private fun getSavedSectionStates(): Map<String, Boolean> {
        return StrictModeHelper.allowDiskReads {
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            mapOf(
                KEY_INTERFACE_EXPANDED to prefs.getBoolean(KEY_INTERFACE_EXPANDED, false),
                KEY_FILES_EXPANDED to prefs.getBoolean(KEY_FILES_EXPANDED, false),
                KEY_SYSTEM_EXPANDED to prefs.getBoolean(KEY_SYSTEM_EXPANDED, false),
                KEY_DEBUG_EXPANDED to prefs.getBoolean(KEY_DEBUG_EXPANDED, false)
            )
        }
    }
    
    /**
     * Save section expanded state to SharedPreferences.
     * Wrapped in StrictModeHelper to avoid violations.
     */
    private fun saveSectionState(key: String, expanded: Boolean) {
        StrictModeHelper.allowDiskWrites {
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, expanded)
                .apply()
        }
    }

    // ==================== Backup/Restore ====================

    private fun setupBackupButtons() {
        binding.btnBackup.setOnClickListener {
            backupViewModel.startBackup()
        }
        binding.btnRestore.setOnClickListener {
            backupViewModel.startRestore()
        }
        binding.iconHelpBackupInfo.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_backup_info_title,
                R.string.tooltip_backup_info_message
            )
        }
    }

    private fun observeBackupState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                backupViewModel.uiState.collect { state ->
                    handleBackupState(state)
                }
            }
        }
    }

    private fun handleBackupState(state: BackupRestoreUiState) {
        binding.progressBackup.visibility = View.GONE
        binding.progressRestore.visibility = View.GONE
        binding.btnBackup.isEnabled = true
        binding.btnRestore.isEnabled = true

        when (state) {
            is BackupRestoreUiState.Idle -> { /* default state */ }

            is BackupRestoreUiState.Authenticating -> {
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }

            is BackupRestoreUiState.NeedsSignIn -> {
                launchBackupSignIn()
            }

            is BackupRestoreUiState.BackingUp -> {
                binding.progressBackup.visibility = View.VISIBLE
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }

            is BackupRestoreUiState.FetchingInfo -> {
                binding.progressRestore.visibility = View.VISIBLE
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }

            is BackupRestoreUiState.Restoring -> {
                binding.progressRestore.visibility = View.VISIBLE
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }

            is BackupRestoreUiState.BackupSuccess -> {
                showBackupSnackbar(getString(R.string.backup_success, state.resourceCount, state.favoritesCount, state.accountEmail))
                updateBackupAccountInfo()
                backupViewModel.resetState()
            }

            is BackupRestoreUiState.BackupInfoReady -> {
                showRestoreConfirmDialog(state.info)
            }

            is BackupRestoreUiState.RestoreSuccess -> {
                showRestoreSuccessMessage(state.result)
                backupViewModel.resetState()
            }

            is BackupRestoreUiState.Error -> {
                showBackupSnackbar(state.message)
                backupViewModel.resetState()
            }
        }
    }

    private fun launchBackupSignIn() {
        try {
            val intent = backupViewModel.getSignInIntent()
            signInLauncher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Google sign-in")
            showBackupSnackbar(getString(R.string.backup_failed, e.message ?: "Unknown error"))
        }
    }

    private fun showRestoreConfirmDialog(info: RestoreFromGoogleDriveUseCase.BackupInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_confirm_title)
            .setMessage(getString(R.string.restore_confirm_message, info.createdAt, info.deviceModel, info.resourceCount, info.favoritesCount))
            .setPositiveButton(R.string.restore_from_google_drive) { _, _ ->
                backupViewModel.confirmRestore()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                backupViewModel.resetState()
            }
            .setOnCancelListener {
                backupViewModel.resetState()
            }
            .show()
    }

    private fun showRestoreSuccessMessage(result: RestoreFromGoogleDriveUseCase.RestoreResult) {
        showBackupSnackbar(getString(R.string.restore_success, result.resourcesAdded, result.resourcesSkipped, result.favoritesAdded, result.favoritesSkipped))
        if (result.resourcesNeedingAuth > 0) {
            Snackbar.make(binding.root, getString(R.string.restore_needs_auth), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun updateBackupAccountInfo() {
        val email = backupViewModel.getAccountEmail()
        if (email != null) {
            binding.layoutBackupInfo.visibility = View.VISIBLE
            binding.tvBackupAccount.text = getString(R.string.backup_account, email)
        } else {
            binding.layoutBackupInfo.visibility = View.GONE
        }
    }

    private fun showBackupSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        dismissManualSyncProgressDialog()
        super.onDestroyView()
        _binding = null
    }
}

// ==================== Data Classes ====================

data class CollapsibleSection(
    val header: TextView,
    val container: LinearLayout,
    val prefKey: String
)

data class SectionData(
    val titleRes: Int,
    val contentViewIds: List<Int>,
    val prefKey: String
)
