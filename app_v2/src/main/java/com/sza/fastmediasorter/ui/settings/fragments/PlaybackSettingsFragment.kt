package com.sza.fastmediasorter.ui.settings.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsPlaybackBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import kotlinx.coroutines.launch
import timber.log.Timber

class PlaybackSettingsFragment : Fragment() {
    private var _binding: FragmentSettingsPlaybackBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    private var isUpdatingFromSettings = false

    // Latest recipient resources for the capture destination pickers. Kept hot by collecting
    // viewModel.destinations in observeData(); the WhileSubscribed flow would otherwise be cold here.
    private var destinationTargets: List<MediaResource> = emptyList()

    // S0367: RECORD_AUDIO consent for the relocated microphone-recording master toggle.
    // Must be created at field-init time (Fragment requirement for registerForActivityResult).
    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(micRecordingEnabled = true))
        } else {
            binding.rowMicRecordingEnabled.setCheckedSilently(false)
            Snackbar.make(binding.root, R.string.mic_recording_permission_denied, Snackbar.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val PREFS_NAME = "playback_sections_state"
        private const val KEY_SORTING_EXPANDED = "section_sorting_expanded"
        private const val KEY_FILE_OPS_EXPANDED = "section_file_ops_expanded"
        private const val KEY_PLAYER_UI_EXPANDED = "section_player_ui_expanded"
        private const val KEY_TOUCH_ZONES_EXPANDED = "section_touch_zones_expanded"
        private const val KEY_BEHAVIOUR_EXPANDED = "section_behaviour_expanded"
        private const val KEY_OTHER_FEATURES_EXPANDED = "section_other_features_expanded"
    }

    private data class ExpandableSection(
        val header: CollapsibleSectionHeader,
        val container: View,
        val prefKey: String,
        val defaultExpanded: Boolean,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsPlaybackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            setupViews()
            setupExpandableSections()
            scrollToHighlightedSettingIfRequested()
        } catch (e: Exception) {
            timber.log.Timber.tag("PlaybackSettings").e(e, "Error setting up views")
            Toast.makeText(context, getString(R.string.error_init_settings), Toast.LENGTH_LONG).show()
        }
        observeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyFlavorRestrictions() {
        val hasOcrAndTranslation = BuildConfig.ENABLE_TRANSLATION && 
            com.sza.fastmediasorter.core.util.DeviceCapabilities.isOcrSupported(requireContext())
        binding.rowCameraOcrTranslationEnabled.isVisible = hasOcrAndTranslation
        binding.layoutCameraOcrOnly.isVisible = hasOcrAndTranslation
        if (!hasOcrAndTranslation) {
            binding.rowCameraOcrTranslationEnabled.setCheckedSilently(false)
            binding.rowCameraOcrOnly.setCheckedSilently(false)
            val current = viewModel.settings.value
            if (current.cameraOcrTranslationEnabled || current.cameraOcrOnly) {
                viewModel.updateSettings(current.copy(
                    cameraOcrTranslationEnabled = false,
                    cameraOcrOnly = false
                ))
            }
        }
    }

    private fun setupViews() {
        applyFlavorRestrictions()

        // S0367: camera-photos + microphone-recording capture settings, relocated from Media → Audio.
        setupCaptureSection()

        binding.rowCameraOcrTranslationEnabled.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(cameraOcrTranslationEnabled = isChecked))
        }

        binding.rowCameraOcrOnly.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(cameraOcrOnly = isChecked))
        }

        // Prevent Sleep — moved from General > Network & Cache to Playback > Behaviour.
        binding.rowPreventSleep.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(preventSleep = isChecked))
        }

        binding.rowControlsKeybindings.setOnClickListener {
            com.sza.fastmediasorter.ui.settings.SettingsActivity.openKeybindingRemap(requireContext())
        }

        // Sort mode dropdown
        val sortModes = arrayOf(
            "Name (A-Z)", "Name (Z-A)",
            "Date (Old first)", "Date (New first)",
            "Size (Small first)", "Size (Large first)",
            "Type (A-Z)", "Type (Z-A)"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sortModes)
        binding.spinnerSortMode.setAdapter(adapter)
        binding.spinnerSortMode.setOnItemClickListener { _, _, position, _ ->
            val current = viewModel.settings.value
            val sortMode = SortMode.entries[position]
            viewModel.updateSettings(current.copy(defaultSortMode = sortMode))
        }

        // Slideshow interval dropdown (1,5,10,30,60,120,300 sec)
        val slideshowOptions = arrayOf("1", "5", "10", "30", "60", "120", "300")
        val slideshowAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, slideshowOptions)
        binding.etSlideshowInterval.setAdapter(slideshowAdapter)
        binding.etSlideshowInterval.setText(getString(R.string.number_format, viewModel.settings.value.slideshowInterval), false)

        binding.etSlideshowInterval.setOnItemClickListener { _, _, position, _ ->
            val seconds = slideshowOptions[position].toInt()
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(slideshowInterval = seconds))
        }

        binding.etSlideshowInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etSlideshowInterval.text.toString()
                val seconds = text.toIntOrNull() ?: 5
                val clampedSeconds = seconds.coerceIn(1, 3600)
                if (seconds != clampedSeconds) {
                    binding.etSlideshowInterval.setText(getString(R.string.number_format, clampedSeconds), false)
                }
                val current = viewModel.settings.value
                if (clampedSeconds != current.slideshowInterval) {
                    viewModel.updateSettings(current.copy(slideshowInterval = clampedSeconds))
                }
            }
        }

        // Switches (migrated to SettingsToggleRow under S0258)
        binding.rowPlayToEnd.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(playToEndInSlideshow = isChecked))
        }

        binding.rowAllowRename.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(allowRename = isChecked))
        }

        binding.rowAllowDelete.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(allowDelete = isChecked))
        }

        binding.rowConfirmDelete.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(confirmDelete = isChecked))
        }

        binding.rowHideSystemUiInFullscreen.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(hideSystemUiInFullscreen = isChecked))
        }

        // S0162: Screen rotation control - hide entire row on non-sensor devices
        val hasAccelerometer = requireContext().packageManager
            .hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
        binding.layoutFollowSystemRotation.isVisible = hasAccelerometer
        if (hasAccelerometer) {
            binding.rowFollowSystemRotation.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(followSystemRotation = isChecked))
            }
        }

        binding.rowShowCommandPanel.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultShowCommandPanel = isChecked))
        }

        binding.rowDetailedErrors.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showDetailedErrors = isChecked))
        }

        binding.rowShowPlayerHint.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showPlayerHintOnFirstRun = isChecked))
        }

        binding.rowAlwaysShowTouchZones.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(alwaysShowTouchZonesOverlay = isChecked))
        }

        binding.iconHelpSlideshow.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_slideshow_title,
                R.string.tooltip_slideshow_message
            )
        }

        // Help for touch zones is now inline on rowAlwaysShowTouchZones (folded by SettingsToggleRow).

        binding.btnShowHintNow.setOnClickListener {
            // Reset first-run flag to trigger hint on next PlayerActivity launch
            viewModel.resetPlayerFirstRun()
            Toast.makeText(
                requireContext(),
                R.string.hint_will_be_shown_next_time,
                Toast.LENGTH_SHORT
            ).show()
        }

        // Hide primary-player row for flavors that don't support it; resume toggle is always visible
        binding.layoutDefaultPlayerToggles.isVisible = BuildConfig.SUPPORTS_DEFAULT_PLAYER

        if (BuildConfig.SUPPORTS_DEFAULT_PLAYER) {
            binding.rowPrimaryMediaPlayer.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                DefaultPlayerManager.applyPrimaryPlayerState(requireContext(), isChecked)
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(isPrimaryMediaPlayer = isChecked))
            }

            binding.rowAcceptSharedFiles.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                DefaultPlayerManager.applyShareReceiverState(requireContext(), isChecked)
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(acceptSharedFiles = isChecked))
            }
        }

        // S0003: Link auto-download - master toggle + open-in-player toggle.
        // Resource picker (row_link_autodownload_resource) is wired in Phase 05;
        // for Phase 01 the row stays informational and reflects the persisted id.
        binding.rowLinkAutodownloadEnabled.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(linkAutoDownloadEnabled = isChecked))
        }
        binding.rowLinkAutodownloadOpenInPlayer.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(linkAutoDownloadOpenInPlayer = isChecked))
        }
        binding.rowResumeOnNextLaunch.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(resumeOnNextLaunch = isChecked))
        }

        binding.rowShowBlackScreenButton.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showBlackScreenButton = isChecked))
        }

        binding.rowDefaultRememberFileList.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultRememberFileList = isChecked))
        }

        // Calculator + Embedded game (moved here from the General tab "Other functionality" group).
        binding.rowEnableCalculator.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableCalculator = isChecked))
        }

        binding.rowEmbeddedGame.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateEmbeddedGameEnabled(isChecked)
        }

        binding.btnResetPlaybackSection.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.reset_playback_section_title)
                .setMessage(R.string.reset_playback_section_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.resetPlaybackSection()
                    Toast.makeText(
                        requireContext(),
                        R.string.reset_playback_section_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Help for command panel is now inline on rowShowCommandPanel (folded by SettingsToggleRow).

        // PiP is only supported on Android 12+ (API 31)
        binding.layoutPip.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        binding.rowEnablePip.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enablePictureInPicture = isChecked))
        }

        // Panel single-eye 3D crop (spec_panel-stereo-single-eye)
        binding.rowPanelStereoSingleEye.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(panelStereoSingleEye = isChecked))
        }

        // S0158: Big Buttons Mode - stored in PlayerLayoutModePrefs (SharedPreferences, not DataStore).
        // ADR-2: change takes effect on next player open; no restart required.
        isUpdatingFromSettings = true
        binding.rowBigButtonsMode.setCheckedSilently(PlayerLayoutModePrefs.isBigButtonsMode(requireContext()))
        isUpdatingFromSettings = false
        binding.rowBigButtonsMode.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            PlayerLayoutModePrefs.setBigButtonsMode(requireContext(), isChecked)
        }
        // Help for big buttons mode is now inline on rowBigButtonsMode (folded by SettingsToggleRow).
    }

    /**
     * S0367/S0375: wires the camera-photos, video-recording, and microphone-recording capture rows
     * plus their destination selectors. Inverted-flag semantics for the camera/video master
     * toggles are preserved from the original implementation.
     */
    private fun setupCaptureSection() {

        // ── Camera Photos ──
        binding.rowCameraToResourceEnabled.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            // Reuse the existing negative persistence flags instead of duplicating camera settings keys.
            viewModel.updateSettings(current.copy(disableCameraCapture = !isChecked))
            binding.layoutCameraToResourceOptions.isVisible = isChecked
        }

        binding.rowCameraAskFilename.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(skipCameraFilenameDialog = !isChecked))
        }

        binding.rowCameraOpenForEditing.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(cameraCaptureOpenForEditing = isChecked))
        }

        binding.btnSelectCameraPhotosDest.setOnClickListener {
            showDestinationPicker(
                currentResourceId = viewModel.settings.value.cameraPhotosDestinationResourceId?.toLongOrNull()
            ) { resource ->
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(cameraPhotosDestinationResourceId = resource?.id?.toString()))
            }
        }

        // ── Video recording (S0371) ──
        // Master toggle persists inverted (disableVideoCapture), mirroring the camera-photos pattern.
        binding.rowVideoCaptureEnabled.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(disableVideoCapture = !isChecked))
            binding.layoutVideoCaptureOptions.isVisible = isChecked
        }

        binding.rowVideoCaptureOpenInPlayer.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(videoCaptureOpenInPlayer = isChecked))
        }

        binding.btnSelectVideoRecordingDest.setOnClickListener {
            showDestinationPicker(
                currentResourceId = viewModel.settings.value.videoRecordingDestinationResourceId?.toLongOrNull()
            ) { resource ->
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(videoRecordingDestinationResourceId = resource?.id?.toString()))
            }
        }

        // ── Microphone recording ──
        if (!BuildConfig.SUPPORT_MIC_RECORDING) {
            binding.rowMicRecordingEnabled.isVisible = false
            binding.rowMicRecordingAskFilename.isVisible = false
            binding.layoutMicRecordingDestSelector.isVisible = false
        } else {
            binding.rowMicRecordingEnabled.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                if (isChecked) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@setOnCheckedChangeListener
                    }
                    viewModel.updateSettings(viewModel.settings.value.copy(micRecordingEnabled = true))
                } else {
                    viewModel.updateSettings(viewModel.settings.value.copy(micRecordingEnabled = false))
                }
                binding.rowMicRecordingAskFilename.isVisible = isChecked
            }

            binding.rowMicRecordingAskFilename.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(micRecordingAskFilename = isChecked))
            }

            binding.btnSelectMicRecordingDest.setOnClickListener {
                showDestinationPicker(
                    currentResourceId = viewModel.settings.value.micRecordingDestinationResourceId?.toLongOrNull()
                ) { resource ->
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(micRecordingDestinationResourceId = resource?.id?.toString()))
                }
            }
        }
    }

    /**
     * S0367: single-choice picker over recipient resources usable as a capture target. Restricted to
     * resources that are also destinations (a real folder); predefined/virtual resources such as
     * "all files" are excluded. Includes an explicit "(clear)" entry that resolves the setting back
     * to its documented fallback.
     */
    private fun showDestinationPicker(
        currentResourceId: Long?,
        onPicked: (MediaResource?) -> Unit
    ) {
        val targets = destinationTargets
        if (targets.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_resources_available, Toast.LENGTH_SHORT).show()
            return
        }
        val clearLabel = getString(R.string.clear_selection)
        val labels = (listOf(clearLabel) + targets.map { it.name }).toTypedArray()
        val checkedIndex = currentResourceId
            ?.let { id -> targets.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?.let { it + 1 } // offset by the leading clear entry
            ?: 0
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_select_destination)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                onPicked(if (which == 0) null else targets[which - 1])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Resolves a destination resource label into [target], falling back to [fallbackRes] when unset/missing. */
    private fun refreshDestinationLabel(resourceId: String?, target: android.widget.TextView, fallbackRes: Int) {
        val id = resourceId?.toLongOrNull()
        if (id == null) {
            target.setText(fallbackRes)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val resource = viewModel.resourceRepository.getResourceById(id)
            target.text = resource?.name ?: getString(fallbackRes)
        }
    }

    private fun observeData() {
        // Only recipient resources (a real destination folder) are valid capture targets.
        collectOnLifecycle(viewModel.destinations) { destinationTargets = it }
        collectOnLifecycle(viewModel.settings) { settings ->
                    isUpdatingFromSettings = true
                    
                    val hasOcrAndTranslation = BuildConfig.ENABLE_TRANSLATION && 
                        com.sza.fastmediasorter.core.util.DeviceCapabilities.isOcrSupported(requireContext())
                    if (hasOcrAndTranslation) {
                        if (binding.rowCameraOcrTranslationEnabled.isChecked != settings.cameraOcrTranslationEnabled) {
                            binding.rowCameraOcrTranslationEnabled.setCheckedSilently(settings.cameraOcrTranslationEnabled)
                        }
                        if (binding.rowCameraOcrOnly.isChecked != settings.cameraOcrOnly) {
                            binding.rowCameraOcrOnly.setCheckedSilently(settings.cameraOcrOnly)
                        }
                        binding.layoutCameraOcrOnly.isVisible = settings.cameraOcrTranslationEnabled
                    }

                    // S0367: Camera Photos rows (inverted master/ask-filename flags) + destination label
                    if (binding.rowCameraToResourceEnabled.isChecked != !settings.disableCameraCapture) {
                        binding.rowCameraToResourceEnabled.setCheckedSilently(!settings.disableCameraCapture)
                    }
                    if (binding.rowCameraAskFilename.isChecked != !settings.skipCameraFilenameDialog) {
                        binding.rowCameraAskFilename.setCheckedSilently(!settings.skipCameraFilenameDialog)
                    }
                    if (binding.rowCameraOpenForEditing.isChecked != settings.cameraCaptureOpenForEditing) {
                        binding.rowCameraOpenForEditing.setCheckedSilently(settings.cameraCaptureOpenForEditing)
                    }
                    binding.layoutCameraToResourceOptions.isVisible = !settings.disableCameraCapture
                    refreshDestinationLabel(
                        settings.cameraPhotosDestinationResourceId,
                        binding.tvCameraPhotosDest,
                        R.string.setting_camera_photos_destination_default_camera
                    )

                    // S0371: Video recording rows (master toggle inverted, child gated by enable)
                    if (binding.rowVideoCaptureEnabled.isChecked != !settings.disableVideoCapture) {
                        binding.rowVideoCaptureEnabled.setCheckedSilently(!settings.disableVideoCapture)
                    }
                    if (binding.rowVideoCaptureOpenInPlayer.isChecked != settings.videoCaptureOpenInPlayer) {
                        binding.rowVideoCaptureOpenInPlayer.setCheckedSilently(settings.videoCaptureOpenInPlayer)
                    }
                    binding.layoutVideoCaptureOptions.isVisible = !settings.disableVideoCapture
                    refreshDestinationLabel(
                        settings.videoRecordingDestinationResourceId,
                        binding.tvVideoRecordingDest,
                        R.string.setting_video_recording_destination_default_movies
                    )

                    // S0367: Microphone recording rows + destination label (feature-gated)
                    if (BuildConfig.SUPPORT_MIC_RECORDING) {
                        if (binding.rowMicRecordingEnabled.isChecked != settings.micRecordingEnabled) {
                            binding.rowMicRecordingEnabled.setCheckedSilently(settings.micRecordingEnabled)
                        }
                        if (binding.rowMicRecordingAskFilename.isChecked != settings.micRecordingAskFilename) {
                            binding.rowMicRecordingAskFilename.setCheckedSilently(settings.micRecordingAskFilename)
                        }
                        binding.rowMicRecordingAskFilename.isVisible = settings.micRecordingEnabled
                        refreshDestinationLabel(
                            settings.micRecordingDestinationResourceId,
                            binding.tvMicRecordingDest,
                            R.string.setting_mic_recording_destination_default_downloads
                        )
                    }

                    // Prevent Sleep (relocated from General)
                    if (binding.rowPreventSleep.isChecked != settings.preventSleep) {
                        binding.rowPreventSleep.setCheckedSilently(settings.preventSleep)
                    }
                    // Sort mode
                    binding.spinnerSortMode.setText(getSortModeName(settings.defaultSortMode), false)

                    // Slideshow interval
                    val currentSlideshow = binding.etSlideshowInterval.text.toString().toIntOrNull()
                    if (currentSlideshow != settings.slideshowInterval) {
                        binding.etSlideshowInterval.setText(getString(R.string.number_format, settings.slideshowInterval), false)
                    }

                    // Switches (only update if value changed; setCheckedSilently avoids listener re-entry)
                    if (binding.rowPlayToEnd.isChecked != settings.playToEndInSlideshow) {
                        binding.rowPlayToEnd.setCheckedSilently(settings.playToEndInSlideshow)
                    }
                    if (binding.rowAllowRename.isChecked != settings.allowRename) {
                        binding.rowAllowRename.setCheckedSilently(settings.allowRename)
                    }
                    if (binding.rowAllowDelete.isChecked != settings.allowDelete) {
                        binding.rowAllowDelete.setCheckedSilently(settings.allowDelete)
                    }
                    if (binding.rowConfirmDelete.isChecked != settings.confirmDelete) {
                        binding.rowConfirmDelete.setCheckedSilently(settings.confirmDelete)
                    }
                    if (binding.rowHideSystemUiInFullscreen.isChecked != settings.hideSystemUiInFullscreen) {
                        binding.rowHideSystemUiInFullscreen.setCheckedSilently(settings.hideSystemUiInFullscreen)
                    }
                    // S0162
                    if (binding.rowFollowSystemRotation.isChecked != settings.followSystemRotation) {
                        binding.rowFollowSystemRotation.setCheckedSilently(settings.followSystemRotation)
                    }
                    if (binding.rowShowCommandPanel.isChecked != settings.defaultShowCommandPanel) {
                        binding.rowShowCommandPanel.setCheckedSilently(settings.defaultShowCommandPanel)
                    }
                    if (binding.rowDetailedErrors.isChecked != settings.showDetailedErrors) {
                        binding.rowDetailedErrors.setCheckedSilently(settings.showDetailedErrors)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (binding.rowEnablePip.isChecked != settings.enablePictureInPicture) {
                            binding.rowEnablePip.setCheckedSilently(settings.enablePictureInPicture)
                        }
                    }
                    if (binding.rowPanelStereoSingleEye.isChecked != settings.panelStereoSingleEye) {
                        binding.rowPanelStereoSingleEye.setCheckedSilently(settings.panelStereoSingleEye)
                    }
                    if (binding.rowShowPlayerHint.isChecked != settings.showPlayerHintOnFirstRun) {
                        binding.rowShowPlayerHint.setCheckedSilently(settings.showPlayerHintOnFirstRun)
                    }
                    if (binding.rowAlwaysShowTouchZones.isChecked != settings.alwaysShowTouchZonesOverlay) {
                        binding.rowAlwaysShowTouchZones.setCheckedSilently(settings.alwaysShowTouchZonesOverlay)
                    }

                    // Default Player toggles (visible only when SUPPORTS_DEFAULT_PLAYER)
                    if (BuildConfig.SUPPORTS_DEFAULT_PLAYER) {
                        if (binding.rowPrimaryMediaPlayer.isChecked != settings.isPrimaryMediaPlayer) {
                            binding.rowPrimaryMediaPlayer.setCheckedSilently(settings.isPrimaryMediaPlayer)
                        }
                        if (binding.rowAcceptSharedFiles.isChecked != settings.acceptSharedFiles) {
                            binding.rowAcceptSharedFiles.setCheckedSilently(settings.acceptSharedFiles)
                        }
                    }

                    if (binding.rowResumeOnNextLaunch.isChecked != settings.resumeOnNextLaunch) {
                        binding.rowResumeOnNextLaunch.setCheckedSilently(settings.resumeOnNextLaunch)
                    }

                    // S0003: Link auto-download
                    if (binding.rowLinkAutodownloadEnabled.isChecked != settings.linkAutoDownloadEnabled) {
                        binding.rowLinkAutodownloadEnabled.setCheckedSilently(settings.linkAutoDownloadEnabled)
                    }
                    if (binding.rowLinkAutodownloadOpenInPlayer.isChecked != settings.linkAutoDownloadOpenInPlayer) {
                        binding.rowLinkAutodownloadOpenInPlayer.setCheckedSilently(settings.linkAutoDownloadOpenInPlayer)
                    }
                    // Disable child controls when master toggle is off
                    binding.rowLinkAutodownloadOpenInPlayer.isEnabled = settings.linkAutoDownloadEnabled
                    binding.rowLinkAutodownloadResource.isEnabled = settings.linkAutoDownloadEnabled
                    binding.tvLinkAutodownloadResourceValue.isEnabled = settings.linkAutoDownloadEnabled
                    binding.tvLinkAutodownloadResourceValue.text = if (settings.linkAutoDownloadResourceId == null) {
                        getString(R.string.link_autodownload_resource_not_set)
                    } else {
                        // Phase 05 will resolve the resource label via GetDestinationsUseCase.
                        getString(R.string.link_autodownload_resource_not_set)
                    }
                    if (binding.rowShowBlackScreenButton.isChecked != settings.showBlackScreenButton) {
                        binding.rowShowBlackScreenButton.setCheckedSilently(settings.showBlackScreenButton)
                    }
                    if (binding.rowDefaultRememberFileList.isChecked != settings.defaultRememberFileList) {
                        binding.rowDefaultRememberFileList.setCheckedSilently(settings.defaultRememberFileList)
                    }
                    if (binding.rowEnableCalculator.isChecked != settings.enableCalculator) {
                        binding.rowEnableCalculator.setCheckedSilently(settings.enableCalculator)
                    }
                    if (binding.rowEmbeddedGame.isChecked != settings.embeddedGameEnabled) {
                        binding.rowEmbeddedGame.setCheckedSilently(settings.embeddedGameEnabled)
                    }

                    isUpdatingFromSettings = false
        }
    }

    /**
     * Handles the "embedded game" deep-link from GameLaunchIntents: expand the "Other features"
     * group (collapsed by default) and bring the embedded-game row on screen.
     */
    private fun scrollToHighlightedSettingIfRequested() {
        val highlight = requireActivity().intent?.getStringExtra(
            com.sza.fastmediasorter.ui.settings.SettingsActivity.EXTRA_HIGHLIGHT_SETTING
        )
        if (highlight != com.sza.fastmediasorter.ui.settings.SettingsActivity.HIGHLIGHT_EMBEDDED_GAME) return
        binding.headerOtherFeatures.setExpanded(true, notify = true)
        binding.containerOtherFeatures.isVisible = true
        saveSectionState(KEY_OTHER_FEATURES_EXPANDED, true)
        binding.rowEmbeddedGame.post {
            binding.rowEmbeddedGame.requestFocus()
            binding.rowEmbeddedGame.requestRectangleOnScreen(
                Rect(0, 0, binding.rowEmbeddedGame.width, binding.rowEmbeddedGame.height),
                false
            )
        }
    }

    private fun setupExpandableSections() {
        val savedStates = getSavedSectionStates()
        val sections = listOf(
            ExpandableSection(binding.headerSortingSlideshow, binding.containerSortingSlideshow, KEY_SORTING_EXPANDED, false),
            ExpandableSection(binding.headerFileOperations, binding.containerFileOperations, KEY_FILE_OPS_EXPANDED, false),
            ExpandableSection(binding.headerPlayerUI, binding.containerPlayerUI, KEY_PLAYER_UI_EXPANDED, false),
            ExpandableSection(binding.headerTouchZones, binding.containerTouchZones, KEY_TOUCH_ZONES_EXPANDED, false),
            ExpandableSection(binding.headerBehaviour, binding.containerBehaviour, KEY_BEHAVIOUR_EXPANDED, false),
            ExpandableSection(binding.headerOtherFeatures, binding.containerOtherFeatures, KEY_OTHER_FEATURES_EXPANDED, false),
        )

        sections.forEach { section ->
            val expanded = savedStates[section.prefKey] ?: section.defaultExpanded
            section.header.setExpanded(expanded, notify = false)
            section.container.isVisible = expanded
            section.header.setOnExpandedChangeListener { isExpanded ->
                section.container.isVisible = isExpanded
                saveSectionState(section.prefKey, isExpanded)
            }
        }
    }

    /**
     * Get saved section states from SharedPreferences.
     * Wrapped in StrictModeHelper to avoid violations during fragment creation.
     */
    private fun getSavedSectionStates(): Map<String, Boolean> {
        return StrictModeHelper.allowDiskReads {
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            mapOf(
                KEY_SORTING_EXPANDED to prefs.getBoolean(KEY_SORTING_EXPANDED, false),
                KEY_FILE_OPS_EXPANDED to prefs.getBoolean(KEY_FILE_OPS_EXPANDED, false),
                KEY_PLAYER_UI_EXPANDED to prefs.getBoolean(KEY_PLAYER_UI_EXPANDED, false),
                KEY_TOUCH_ZONES_EXPANDED to prefs.getBoolean(KEY_TOUCH_ZONES_EXPANDED, false),
                KEY_BEHAVIOUR_EXPANDED to prefs.getBoolean(KEY_BEHAVIOUR_EXPANDED, false),
                KEY_OTHER_FEATURES_EXPANDED to prefs.getBoolean(KEY_OTHER_FEATURES_EXPANDED, false)
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

    private fun getSortModeName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> getString(R.string.sort_mode_manual)
            SortMode.NAME_ASC -> getString(R.string.sort_mode_name_asc)
            SortMode.NAME_DESC -> getString(R.string.sort_mode_name_desc)
            SortMode.DATE_ASC -> getString(R.string.sort_mode_date_asc)
            SortMode.DATE_DESC -> getString(R.string.sort_mode_date_desc)
            SortMode.SIZE_ASC -> getString(R.string.sort_mode_size_asc)
            SortMode.SIZE_DESC -> getString(R.string.sort_mode_size_desc)
            SortMode.TYPE_ASC -> getString(R.string.sort_mode_type_asc)
            SortMode.TYPE_DESC -> getString(R.string.sort_mode_type_desc)
            SortMode.ARTIST_ASC -> getString(R.string.sort_mode_artist_asc)
            SortMode.ARTIST_DESC -> getString(R.string.sort_mode_artist_desc)
            SortMode.TITLE_ASC -> getString(R.string.sort_mode_title_asc)
            SortMode.TITLE_DESC -> getString(R.string.sort_mode_title_desc)
            SortMode.DURATION_ASC -> getString(R.string.sort_mode_duration_asc)
            SortMode.DURATION_DESC -> getString(R.string.sort_mode_duration_desc)
            SortMode.DATE_TAKEN_ASC -> getString(R.string.sort_mode_date_taken_asc)
            SortMode.DATE_TAKEN_DESC -> getString(R.string.sort_mode_date_taken_desc)
            SortMode.RANDOM -> getString(R.string.sort_mode_random)
        }
    }
}
