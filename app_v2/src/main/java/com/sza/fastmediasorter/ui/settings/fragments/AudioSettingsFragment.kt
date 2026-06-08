package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsAudioBinding
import com.sza.fastmediasorter.ui.settings.exitAllFilesForManualSupportToggle
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class AudioSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsAudioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

    companion object {
        private const val MB_TO_BYTES = 1024L * 1024L
        private const val PREFS_NAME_HINT = "playback_sections_state"
        private const val KEY_HAS_SHOWN_BATTERY_HINT = "has_shown_battery_hint_background_audio"

        // Audio empty state mode keys (stored in DataStore)
        private const val MODE_NONE = "NONE"
        private const val MODE_AVD_PULSE = "AVD_PULSE"
        private const val MODE_CANVAS_BARS = "CANVAS_BARS"
        private const val MODE_CANVAS_WAVES = "CANVAS_WAVES"
        private const val MODE_VISUALIZATION = "VISUALIZATION"
        /** Legacy DataStore value kept for backward compat; shown as Visualization in UI. */
        private const val MODE_GIF_LOOP = "GIF_LOOP"
    }

    // Ordered list of mode keys - index-aligned with dropdown labels
    private val emptyStateModeKeys = listOf(MODE_NONE, MODE_AVD_PULSE, MODE_CANVAS_BARS, MODE_CANVAS_WAVES, MODE_VISUALIZATION)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enablePersistentAudioPlayback = true))
            updateNotificationPermissionButtonVisibility()
        } else {
            binding.rowEnablePersistentAudioPlayback.setCheckedSilently(false)
            Snackbar.make(
                binding.root,
                R.string.notification_permission_required_for_background,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsAudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }

    private fun setupViews() {
        // Support Audio - help payload folded into the row (str_helpTitle/str_helpMessage)
        bindSwitch(binding.rowSupportAudio) { isChecked ->
            val current = viewModel.settings.value
            val updated = current
                .exitAllFilesForManualSupportToggle(isChecked)
                .copy(supportAudio = isChecked)
            viewModel.updateSettings(updated)
        }

        // Search audio covers online
        bindSwitch(binding.rowSearchAudioCoversOnline) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(searchAudioCoversOnline = isChecked))
            binding.rowSearchCoversOnlyWifi.isVisible = isChecked
            binding.rowSaveAudioMetadataLocally.isVisible = isChecked
        }

        // Search covers only on WiFi
        bindSwitch(binding.rowSearchCoversOnlyWifi) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(searchAudioCoversOnlyOnWifi = isChecked))
        }

        bindSwitch(binding.rowSaveAudioMetadataLocally) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(saveAudioMetadataLocally = isChecked))
        }

        // Enable photos during audio playback - help payload folded into the row
        bindSwitch(binding.rowEnablePhotosDuringAudio) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enablePhotosDuringAudio = isChecked))
            binding.layoutPhotosSourceSelector.isVisible = isChecked
        }

        // Select photos source button
        binding.btnSelectPhotosSource.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.ResourcePickerDialog(
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner,
                getResourcesUseCase = viewModel.getResourcesUseCase,
                currentSelection = viewModel.settings.value.audioBackgroundPhotosResourceId?.toLongOrNull(),
                title = getString(com.sza.fastmediasorter.R.string.select_photos_source),
                allowClear = true,
                onResourceSelected = { resource ->
                    val current = viewModel.settings.value
                    val updated = current.copy(audioBackgroundPhotosResourceId = resource?.id?.toString())
                    viewModel.updateSettings(updated)
                }
            ).show()
        }

        // Audio size limits
        binding.etAudioSizeMin.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isUpdatingFromSettings && !s.isNullOrBlank()) {
                    val minMb = s.toString().toLongOrNull() ?: 0L
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(audioSizeMin = minMb * MB_TO_BYTES))
                }
            }
        })

        binding.etAudioSizeMax.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isUpdatingFromSettings && !s.isNullOrBlank()) {
                    val maxMb = s.toString().toLongOrNull() ?: 0L
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(audioSizeMax = maxMb * MB_TO_BYTES))
                }
            }
        })

        // Free-standing help icon next to "Audio size limit" label - not part of a switch row,
        // kept as standalone ImageButton with TooltipDialog wiring.
        binding.iconHelpAudioSizeLimits.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.tooltip_audio_size_limits_title,
                com.sza.fastmediasorter.R.string.tooltip_audio_size_limits_message
            )
        }

        // Audio empty state dropdown
        val emptyStateModeLabels = listOf(
            getString(R.string.audio_empty_state_none),
            getString(R.string.audio_empty_state_avd_pulse),
            getString(R.string.audio_empty_state_canvas_bars),
            getString(R.string.audio_empty_state_canvas_waves),
            getString(R.string.audio_empty_state_visualization)
        )
        val emptyStateAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            emptyStateModeLabels
        )
        binding.actvAudioEmptyStateMode.setAdapter(emptyStateAdapter)
        binding.actvAudioEmptyStateMode.setOnItemClickListener { _, _, position, _ ->
            if (!isUpdatingFromSettings) {
                val selectedKey = emptyStateModeKeys.getOrElse(position) { MODE_NONE }
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(audioEmptyStateMode = selectedKey))
            }
        }

        // Background audio playback
        setupBackgroundAudioSection()

        // Default player button
        setupDefaultPlayerButton()
        // S0367: microphone-recording and camera-photos sections moved to PlaybackSettingsFragment.
    }

    private fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
            withSettingsUpdate {
                val isAllFilesEnabled = settings.allFiles

                // When All Files is enabled, force switch ON and disable it
                setSwitchChecked(binding.rowSupportAudio, isAllFilesEnabled || settings.supportAudio)

                setSwitchChecked(binding.rowSearchAudioCoversOnline, settings.searchAudioCoversOnline)
                setSwitchChecked(binding.rowSearchCoversOnlyWifi, settings.searchAudioCoversOnlyOnWifi)
                binding.rowSaveAudioMetadataLocally.isVisible = settings.searchAudioCoversOnline
                setSwitchChecked(binding.rowSaveAudioMetadataLocally, settings.saveAudioMetadataLocally)

                binding.rowSearchCoversOnlyWifi.isVisible = settings.searchAudioCoversOnline

                // Photos during audio playback
                setSwitchChecked(binding.rowEnablePhotosDuringAudio, settings.enablePhotosDuringAudio)
                binding.layoutPhotosSourceSelector.isVisible = settings.enablePhotosDuringAudio

                // Update selected photos source text
                if (settings.audioBackgroundPhotosResourceId != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val resourceId = settings.audioBackgroundPhotosResourceId.toLongOrNull()
                        if (resourceId != null) {
                            val resource = viewModel.resourceRepository.getResourceById(resourceId)
                            binding.tvSelectedPhotosSource.text = resource?.name
                                ?: getString(com.sza.fastmediasorter.R.string.resource_not_found)
                        } else {
                            binding.tvSelectedPhotosSource.setText(com.sza.fastmediasorter.R.string.no_photos_source_selected)
                        }
                    }
                } else {
                    binding.tvSelectedPhotosSource.setText(com.sza.fastmediasorter.R.string.no_photos_source_selected)
                }

                val minMb = settings.audioSizeMin / MB_TO_BYTES
                val maxMb = settings.audioSizeMax / MB_TO_BYTES

                if (binding.etAudioSizeMin.text.toString() != minMb.toString()) {
                    binding.etAudioSizeMin.setText(minMb.toString())
                }
                if (binding.etAudioSizeMax.text.toString() != maxMb.toString()) {
                    binding.etAudioSizeMax.setText(maxMb.toString())
                }

                // Audio empty state dropdown
                // Normalize legacy "GIF_LOOP" → "VISUALIZATION" for index lookup
                val normalizedMode = if (settings.audioEmptyStateMode == MODE_GIF_LOOP) MODE_VISUALIZATION else settings.audioEmptyStateMode
                val modeIndex = emptyStateModeKeys.indexOf(normalizedMode).takeIf { it >= 0 } ?: 0
                val emptyStateModeLabels = listOf(
                    getString(R.string.audio_empty_state_none),
                    getString(R.string.audio_empty_state_avd_pulse),
                    getString(R.string.audio_empty_state_canvas_bars),
                    getString(R.string.audio_empty_state_canvas_waves),
                    getString(R.string.audio_empty_state_visualization)
                )
                binding.actvAudioEmptyStateMode.setText(emptyStateModeLabels[modeIndex], false)

                // Background audio playback
                if (BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) {
                    setSwitchChecked(binding.rowEnablePersistentAudioPlayback, settings.enablePersistentAudioPlayback)
                    setSwitchChecked(binding.rowShowNowPlayingPanel, settings.showNowPlayingPanel)
                    updateNotificationPermissionButtonVisibility()
                    // Sync exit behavior radio group
                    val radioId = when (settings.backgroundAudioExitBehavior) {
                        com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.ALWAYS_STOP ->
                            R.id.radioExitBehaviorAlwaysStop
                        com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.ALWAYS_CONTINUE ->
                            R.id.radioExitBehaviorAlwaysContinue
                        com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.ASK ->
                            R.id.radioExitBehaviorAsk
                    }
                    if (binding.radioGroupExitBehavior.checkedRadioButtonId != radioId) {
                        binding.radioGroupExitBehavior.check(radioId)
                    }
                    updateExitBehaviorVisibility()
                }

                // S0367: microphone-recording and camera-photos state sync moved to PlaybackSettingsFragment.
            }
        }
    }

    // ── Background Audio Section ──

    private fun setupBackgroundAudioSection() {
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) {
            binding.rowEnablePersistentAudioPlayback.isVisible = false
            binding.btnNotificationPermission.visibility = View.GONE
            binding.layoutExitBehaviorSection.visibility = View.GONE
            return
        }

        bindSwitch(binding.rowEnablePersistentAudioPlayback) { isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    return@bindSwitch
                }
                showBatteryOptimizationHintIfNeeded()
            }
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enablePersistentAudioPlayback = isChecked))
            updateNotificationPermissionButtonVisibility()
            updateExitBehaviorVisibility()
        }

        bindSwitch(binding.rowShowNowPlayingPanel) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showNowPlayingPanel = isChecked))
        }

        binding.btnNotificationPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
        }

        updateNotificationPermissionButtonVisibility()
        setupExitBehaviorSection()
    }

    private fun setupExitBehaviorSection() {
        binding.radioGroupExitBehavior.setOnCheckedChangeListener { _, checkedId ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val behavior = when (checkedId) {
                R.id.radioExitBehaviorAlwaysStop ->
                    com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.ALWAYS_STOP
                R.id.radioExitBehaviorAlwaysContinue ->
                    com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.ALWAYS_CONTINUE
                else -> com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.ASK
            }
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(backgroundAudioExitBehavior = behavior))
        }
        updateExitBehaviorVisibility()
    }

    private fun updateExitBehaviorVisibility() {
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) return
        val show = binding.rowEnablePersistentAudioPlayback.isChecked
        binding.layoutExitBehaviorSection.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun updateNotificationPermissionButtonVisibility() {
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) return
        binding.btnNotificationPermission.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !isNotificationPermissionGranted()
                && binding.rowEnablePersistentAudioPlayback.isChecked
            ) View.VISIBLE else View.GONE
    }

    private fun showBatteryOptimizationHintIfNeeded() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME_HINT, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HAS_SHOWN_BATTERY_HINT, false)) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.battery_optimization_hint_title)
            .setMessage(R.string.battery_optimization_hint_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putBoolean(KEY_HAS_SHOWN_BATTERY_HINT, true).apply()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        _binding?.let {
            DefaultPlayerHelper.applyButtonState(
                it.btnSetDefaultAudioPlayer, requireContext(), R.string.settings_set_default_audio_player
            )
        }
    }

    private fun setupDefaultPlayerButton() {
        DefaultPlayerHelper.applyButtonState(
            binding.btnSetDefaultAudioPlayer, requireContext(), R.string.settings_set_default_audio_player
        )
        binding.btnSetDefaultAudioPlayer.setOnClickListener {
            val current = viewModel.settings.value
            if (!current.isPrimaryMediaPlayer) {
                viewModel.updateSettings(current.copy(isPrimaryMediaPlayer = true))
            }
            DefaultPlayerHelper.showSetDefaultDialogForType(this, "audio/*")
        }
    }
}
