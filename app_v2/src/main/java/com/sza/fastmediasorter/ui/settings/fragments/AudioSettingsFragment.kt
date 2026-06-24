package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsAudioBinding
import com.sza.fastmediasorter.ui.settings.exitAllFilesForManualSupportToggle
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class AudioSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsAudioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

    @javax.inject.Inject
    lateinit var deliveryEnableInterceptor: com.sza.fastmediasorter.ui.delivery.DeliveryEnableInterceptor

    companion object {
        private const val MB_TO_BYTES = 1024L * 1024L

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

        // Audio empty state mode - trigger row opening a single-choice dialog (S0646)
        binding.rowAudioEmptyStateMode.setOnRowClickListener {
            Timber.d("S0646: audio empty-state visualizer row tapped, opening list-choice dialog")
            val options = emptyStateModeKeys.zip(emptyStateModeLabels()) { key, label ->
                com.sza.fastmediasorter.ui.dialog.SimpleValueChoiceDialog.Option(key, label)
            }
            val current = viewModel.settings.value
            com.sza.fastmediasorter.ui.dialog.SimpleValueChoiceDialog(
                requireContext(),
                viewLifecycleOwner,
                title = getString(R.string.audio_empty_state_label),
                options = options,
                currentKey = normalizeEmptyStateMode(current.audioEmptyStateMode),
                onSelected = { key ->
                    val selectedKey = key ?: return@SimpleValueChoiceDialog
                    if (selectedKey == MODE_VISUALIZATION) {
                        // Video background ships via on-demand delivery (Set C); offer the download when the
                        // set is not installed, and leave the prior mode persisted if the user refuses.
                        deliveryEnableInterceptor.requireInstalled(
                            this@AudioSettingsFragment,
                            com.sza.fastmediasorter.domain.delivery.DeliverableSet.AUDIO_VISUALIZATIONS,
                            onReady = { viewModel.updateSettings(current.copy(audioEmptyStateMode = MODE_VISUALIZATION)) },
                            onUnavailable = { /* no settings write; row stays on persisted mode */ }
                        )
                    } else {
                        viewModel.updateSettings(current.copy(audioEmptyStateMode = selectedKey))
                    }
                }
            ).show()
        }

        // Default player button
        setupDefaultPlayerButton()
        // S0367: microphone-recording and camera-photos sections moved to PlaybackSettingsFragment.
    }

    // Ordered labels index-aligned with [emptyStateModeKeys].
    private fun emptyStateModeLabels(): List<String> = listOf(
        getString(R.string.audio_empty_state_none),
        getString(R.string.audio_empty_state_avd_pulse),
        getString(R.string.audio_empty_state_canvas_bars),
        getString(R.string.audio_empty_state_canvas_waves),
        getString(R.string.audio_empty_state_visualization)
    )

    // Map the legacy "GIF_LOOP" DataStore value onto the current VISUALIZATION key for UI display.
    private fun normalizeEmptyStateMode(mode: String): String =
        if (mode == MODE_GIF_LOOP) MODE_VISUALIZATION else mode

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

                // Audio empty state mode row - reflect the persisted mode (legacy GIF_LOOP shown as
                // VISUALIZATION) so a refused on-demand download leaves the row on the prior mode.
                val modeIndex = emptyStateModeKeys.indexOf(normalizeEmptyStateMode(settings.audioEmptyStateMode))
                    .takeIf { it >= 0 } ?: 0
                binding.rowAudioEmptyStateMode.setValue(emptyStateModeLabels()[modeIndex])

                // S0367: microphone-recording and camera-photos state sync moved to PlaybackSettingsFragment.
            }
        }
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
