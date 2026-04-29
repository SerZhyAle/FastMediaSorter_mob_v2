package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import androidx.fragment.app.Fragment
import timber.log.Timber

/** Owns the entire setupViews() body: switch/spinner/input setup + button wiring. */
class GeneralSettingsViewSetupHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val getIsUpdatingSpinner: () -> Boolean,
    private val setIsUpdatingSpinner: (Boolean) -> Unit,
    private val cacheHelper: GeneralSettingsCacheHelper,
    private val permissionsHelper: GeneralSettingsPermissionsHelper,
    private val importExportHelper: GeneralSettingsImportExportHelper,
    private val credentialHelper: GeneralSettingsCredentialHelper,
    private val logHelper: GeneralSettingsLogHelper,
    private val resetHelper: GeneralSettingsResetHelper,
) {
    fun setup() {
        setupLanguageSpinner()
        setupSwitches()
        setupTooltips()
        setupNetworkParallelism()
        setupCacheSizeInput()
        setupSyncSection()
        setupDefaultCredentials()
        setupLinkButtons()
        setupActionButtons()
    }

    private fun setupLanguageSpinner() {
        val languages = fragment.resources.getStringArray(R.array.languages)
        val adapter = ArrayAdapter(fragment.requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        setIsUpdatingSpinner(true)
        binding.spinnerLanguage.adapter = adapter
        binding.spinnerLanguage.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (getIsUpdatingSpinner()) return
                val newLanguageCode = when (position) { 0 -> "en"; 1 -> "ru"; 2 -> "uk"; else -> "en" }
                val currentSettings = viewModel.settings.value
                if (newLanguageCode != currentSettings.language) {
                    viewModel.updateSettings(currentSettings.copy(language = newLanguageCode))
                    showRestartDialog(newLanguageCode)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        binding.spinnerLanguage.post { setIsUpdatingSpinner(false) }
    }

    private fun setupSwitches() {
        binding.switchPreventSleep.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(preventSleep = isChecked))
        }
        binding.switchEnableFavorites.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableFavorites = isChecked))
        }
        binding.switchSmallControls.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(showSmallControls = isChecked))
        }
        binding.switchCompactElements?.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(useCompactElements = isChecked))
        }
        binding.switchAllFiles.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) {
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
            if (!isChecked) {
                binding.switchShowHiddenFiles.isChecked = false
                viewModel.updateSettings(updatedSettings.copy(showHiddenFiles = false))
            }
        }
        binding.switchShowHiddenFiles.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(showHiddenFiles = isChecked))
        }
        binding.switchShowSubfoldersAsItems.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(showSubfoldersAsItems = isChecked))
        }
        binding.switchDefaultRememberFileList.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(defaultRememberFileList = isChecked))
        }
        binding.btnHelpRememberFileList.setOnClickListener { resetHelper.showRememberFileListHelpDialog() }
    }

    private fun setupTooltips() {
        binding.iconHelpFavorites.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.tooltip_favorites_title, R.string.tooltip_favorites_message)
        }
        binding.iconHelpPreventSleep.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.tooltip_prevent_sleep_title, R.string.tooltip_prevent_sleep_message)
        }
        binding.iconHelpCompactControls.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.tooltip_compact_controls_title, R.string.tooltip_compact_controls_message)
        }
        binding.iconHelpCompactElements?.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.tooltip_compact_elements_title, R.string.tooltip_compact_elements_message)
        }
        binding.iconHelpDefaultCredentials.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.tooltip_default_credentials_title, R.string.tooltip_default_credentials_message)
        }
        binding.iconHelpAllFiles.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.tooltip_all_files_title, R.string.tooltip_all_files_message)
        }
        binding.iconHelpShowHiddenFiles.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.tooltip_show_hidden_files_title, R.string.tooltip_show_hidden_files_message)
        }
        binding.iconHelpShowSubfolders.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.show_subfolders_as_items, R.string.show_subfolders_as_items_hint)
        }
    }

    private fun setupNetworkParallelism() {
        val parallelismOptions = arrayOf("1", "2", "4", "8", "12", "24")
        val parallelismAdapter = android.widget.ArrayAdapter(fragment.requireContext(), android.R.layout.simple_dropdown_item_1line, parallelismOptions)
        binding.actvNetworkParallelism.setAdapter(parallelismAdapter)
        binding.actvNetworkParallelism.setText(fragment.getString(R.string.number_format, viewModel.settings.value.networkParallelism), false)
        binding.actvNetworkParallelism.setOnItemClickListener { _, _, position, _ ->
            if (getIsUpdatingSpinner()) return@setOnItemClickListener
            val limit = parallelismOptions[position].toInt()
            val current = viewModel.settings.value
            if (current.networkParallelism != limit) {
                viewModel.updateSettings(current.copy(networkParallelism = limit))
                com.sza.fastmediasorter.data.network.ConnectionThrottleManager.setUserNetworkLimit(limit)
            }
        }
        binding.actvNetworkParallelism.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !getIsUpdatingSpinner()) {
                val limit = binding.actvNetworkParallelism.text.toString().toIntOrNull()
                if (limit != null && limit in 1..32) {
                    val current = viewModel.settings.value
                    if (current.networkParallelism != limit) {
                        viewModel.updateSettings(current.copy(networkParallelism = limit))
                        com.sza.fastmediasorter.data.network.ConnectionThrottleManager.setUserNetworkLimit(limit)
                    }
                } else {
                    binding.actvNetworkParallelism.setText(fragment.getString(R.string.number_format, viewModel.settings.value.networkParallelism), false)
                }
            }
        }
    }

    private fun setupCacheSizeInput() {
        val cacheSizeOptions = arrayOf("512", "1024", "2048", "4096", "8192", "16384")
        val cacheSizeAdapter = android.widget.ArrayAdapter(fragment.requireContext(), android.R.layout.simple_dropdown_item_1line, cacheSizeOptions)
        binding.actvCacheSizeLimit.setAdapter(cacheSizeAdapter)
        binding.actvCacheSizeLimit.setText(fragment.getString(R.string.number_format, viewModel.settings.value.cacheSizeMb), false)
        binding.actvCacheSizeLimit.setOnItemClickListener { _, _, position, _ ->
            if (getIsUpdatingSpinner()) return@setOnItemClickListener
            val sizeMb = cacheSizeOptions[position].toInt()
            if (viewModel.settings.value.cacheSizeMb != sizeMb) cacheHelper.showCacheSizeRestartDialog(sizeMb)
        }
        binding.actvCacheSizeLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !getIsUpdatingSpinner()) {
                val sizeMb = binding.actvCacheSizeLimit.text.toString().toIntOrNull()
                if (sizeMb != null && sizeMb in 512..16384) {
                    if (viewModel.settings.value.cacheSizeMb != sizeMb) cacheHelper.showCacheSizeRestartDialog(sizeMb)
                } else {
                    binding.actvCacheSizeLimit.setText(fragment.getString(R.string.number_format, viewModel.settings.value.cacheSizeMb), false)
                    Toast.makeText(fragment.requireContext(), "Cache size must be between 512 and 16384 MB", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSyncSection() {
        binding.switchEnableBackgroundSync.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableBackgroundSync = isChecked))
        }
        binding.switchEnableThumbnailPreload?.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableThumbnailPreload = isChecked))
            binding.layoutThumbnailPreloadWifiOnly?.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.switchThumbnailPreloadWifiOnly?.setOnCheckedChangeListener { _, isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(thumbnailPreloadWifiOnly = isChecked))
        }
        val syncIntervalOptions = arrayOf("5", "15", "60", "120", "300")
        val syncAdapter = android.widget.ArrayAdapter(fragment.requireContext(), android.R.layout.simple_dropdown_item_1line, syncIntervalOptions)
        binding.actvSyncInterval?.let { syncIntervalView ->
            syncIntervalView.setAdapter(syncAdapter)
            val currentMinutes = viewModel.settings.value.backgroundSyncIntervalHours * 60
            syncIntervalView.setText(fragment.getString(R.string.number_format, currentMinutes), false)
            syncIntervalView.setOnItemClickListener { _, _, position, _ ->
                if (getIsUpdatingSpinner()) return@setOnItemClickListener
                val minutes = syncIntervalOptions[position].toInt()
                val hours = (minutes / 60.0).toInt().coerceAtLeast(1)
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(backgroundSyncIntervalHours = hours))
            }
            syncIntervalView.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !getIsUpdatingSpinner()) {
                    val minutes = syncIntervalView.text.toString().toIntOrNull()
                    if (minutes != null && minutes >= 5) {
                        val hours = (minutes / 60.0).toInt().coerceAtLeast(1)
                        viewModel.updateSettings(viewModel.settings.value.copy(backgroundSyncIntervalHours = hours))
                    } else {
                        val previousMinutes = viewModel.settings.value.backgroundSyncIntervalHours * 60
                        syncIntervalView.setText(fragment.getString(R.string.number_format, previousMinutes), false)
                        Toast.makeText(fragment.requireContext(), R.string.slide_interval_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.btnSyncNow.setOnClickListener {
            if (viewModel.manualNetworkSyncState.value.inProgress) viewModel.cancelManualNetworkSync()
            else viewModel.startManualNetworkSync()
        }
    }

    private fun setupDefaultCredentials() {
        binding.etDefaultUser.setText(viewModel.settings.value.defaultUser)
        binding.etDefaultUser.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val current = viewModel.settings.value
                val newUser = binding.etDefaultUser.text.toString()
                if (current.defaultUser != newUser) {
                    viewModel.updateSettings(current.copy(defaultUser = newUser))
                    if (BuildConfig.OWNER_TRIGGER.isNotEmpty() && newUser.equals(BuildConfig.OWNER_TRIGGER, ignoreCase = true)) {
                        AlertDialog.Builder(fragment.requireContext())
                            .setTitle(R.string.import_resources_title)
                            .setMessage(R.string.import_resources_message)
                            .setPositiveButton(R.string.yes) { _, _ -> viewModel.importSzaResources(fragment.requireContext()) }
                            .setNegativeButton(R.string.no, null)
                            .show()
                    }
                }
            }
        }
        binding.etDefaultPassword.setText(viewModel.settings.value.defaultPassword)
        binding.etDefaultPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val current = viewModel.settings.value
                val newPassword = binding.etDefaultPassword.text.toString()
                if (current.defaultPassword != newPassword)
                    viewModel.updateSettings(current.copy(defaultPassword = newPassword))
            }
        }
    }

    private fun setupLinkButtons() {
        binding.btnUserGuide.setOnClickListener {
            openUrl(when (LocaleHelper.getLanguage(fragment.requireContext())) {
                "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/index-ru.html"
                "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/index-uk.html"
                else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/"
            }, "No browser found to open documentation")
        }
        binding.btnHowToGuides.setOnClickListener {
            openUrl(when (LocaleHelper.getLanguage(fragment.requireContext())) {
                "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/HOW_TO_RU.html"
                "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/HOW_TO_UK.html"
                else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/HOW_TO.html"
            }, "No browser found to open documentation")
        }
        binding.btnOpenWelcome.setOnClickListener {
            fragment.startActivity(Intent(fragment.requireContext(), WelcomeActivity::class.java))
        }
        binding.btnPrivacyPolicy.setOnClickListener {
            openUrl(when (LocaleHelper.getLanguage(fragment.requireContext())) {
                "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.ru.html"
                "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.uk.html"
                else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.html"
            }, "No browser found to open Privacy Policy")
        }
        binding.btnOpenSourceLicenses.setOnClickListener {
            fragment.parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, com.sza.fastmediasorter.ui.settings.fragments.OpenSourceLicensesFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupActionButtons() {
        binding.btnResetSettings.setOnClickListener { resetHelper.showResetSettingsConfirmation() }
        binding.btnResetGeneralSection.setOnClickListener { resetHelper.showResetGeneralSectionConfirmation() }

        binding.headerDebugSettings.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        binding.containerDebugSettings.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE

        if (BuildConfig.DEBUG && com.sza.fastmediasorter.ui.settings.IntegrationTestDialog.isAvailable()) {
            binding.btnIntegrationTests.visibility = View.VISIBLE
            binding.btnIntegrationTests.setOnClickListener {
                com.sza.fastmediasorter.ui.settings.IntegrationTestDialog()
                    .show(fragment.childFragmentManager, com.sza.fastmediasorter.ui.settings.IntegrationTestDialog.TAG)
            }
            binding.btnImportTestCredentials.visibility = View.VISIBLE
            binding.btnImportTestCredentials.setOnClickListener { credentialHelper.importTestCredentials() }
        } else {
            binding.btnIntegrationTests.visibility = View.GONE
            binding.btnImportTestCredentials.visibility = View.GONE
        }

        binding.btnExportSettings.setOnClickListener { importExportHelper.showExportSettingsConfirmation() }
        binding.btnImportSettings.setOnClickListener { importExportHelper.showImportSettingsConfirmation() }

        binding.btnLocalFilesPermission.setOnClickListener { permissionsHelper.handleLocalFilesPermissionAction() }
        binding.btnNetworkPermission.setOnClickListener {
            Toast.makeText(fragment.requireContext(), "Network permissions are already granted automatically", Toast.LENGTH_SHORT).show()
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            binding.btnManageMediaPermission.visibility = View.VISIBLE
        } else {
            binding.btnManageMediaPermission.visibility = View.GONE
        }
        permissionsHelper.updatePermissionButtonsState()

        logHelper.setupButtons()

        binding.btnAutoCalculateCache.setOnClickListener { cacheHelper.autoCalculateCacheSize() }
        binding.btnClearCache.setOnClickListener { cacheHelper.clearCache() }
        binding.btnResetSmbConnections.setOnClickListener { resetHelper.resetSmbConnections() }
        cacheHelper.updateCacheSize()
    }

    private fun showRestartDialog(languageCode: String) {
        val languageName = LocaleHelper.getLanguageName(languageCode)
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.restart_app_title)
            .setMessage(fragment.getString(R.string.restart_app_message, languageName))
            .setPositiveButton(R.string.restart) { _, _ ->
                LocaleHelper.markReturnToSettings(fragment.requireContext())
                LocaleHelper.changeLanguage(fragment.requireActivity(), languageCode)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                val currentLanguage = LocaleHelper.getLanguage(fragment.requireContext())
                val currentPosition = when (currentLanguage) { "en" -> 0; "ru" -> 1; "uk" -> 2; else -> 0 }
                setIsUpdatingSpinner(true)
                binding.spinnerLanguage.setSelection(currentPosition, false)
                binding.spinnerLanguage.post { setIsUpdatingSpinner(false) }
                viewModel.updateSettings(viewModel.settings.value.copy(language = currentLanguage))
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun openUrl(url: String, notFoundMessage: String) {
        try {
            fragment.startActivity(Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse(url) })
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(fragment.requireContext(), notFoundMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
