package com.sza.fastmediasorter.ui.settings.fragments

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.core.util.DeviceCapabilities
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.databinding.ItemDestinationBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.dialog.ColorPickerDialog
import com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog
import com.sza.fastmediasorter.ui.dialog.ScheduledOperationDialog
import com.sza.fastmediasorter.ui.settings.ScheduledOperationsAdapter
import com.sza.fastmediasorter.ui.settings.ScheduledOperationsViewModel
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerSettingsManager
import com.sza.fastmediasorter.ui.settings.helpers.ScreenshotGestureActionPickerManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@android.annotation.SuppressLint("SetTextI18n")
@AndroidEntryPoint
class OperationsSettingsFragment : BaseSettingsFragment() {
    companion object {
        private const val PREFS_NAME = "settings_section_states"
        private const val KEY_SAFETY_EXPANDED = "operations_safety_expanded"
        private const val KEY_FILE_OPS_EXPANDED = "destinations_file_ops_expanded"
        private const val KEY_DESTINATIONS_EXPANDED = "destinations_list_expanded"
        private const val KEY_SCHEDULED_EXPANDED = "scheduled_ops_expanded"
        // "mgmt_" prefix avoids collisions with PlaybackSettingsFragment's "section_*" keys.
        private const val KEY_BEHAVIOUR_EXPANDED = "mgmt_behaviour_expanded"
        private const val KEY_OTHER_FEATURES_EXPANDED = "mgmt_other_features_expanded"
        private const val KEY_SYSTEM_APPS_EXPANDED = "mgmt_system_apps_expanded"
        private const val KEY_SCREEN_GESTURES_EXPANDED = "mgmt_screen_gestures_expanded"
    }

    private data class ExpandableSection(
        val header: CollapsibleSectionHeader,
        val container: View,
        val prefKey: String,
        val defaultExpanded: Boolean,
    )

    private var _binding: FragmentSettingsDestinationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    private val scheduledViewModel: ScheduledOperationsViewModel by activityViewModels()
    private lateinit var adapter: DestinationsAdapter
    private lateinit var scheduledAdapter: ScheduledOperationsAdapter

    @Inject
    lateinit var capabilityAvailability: CapabilityAvailability

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    // Empty on every flavor except noLegal, where the gesture-overlay capability contributes one.
    @Inject
    lateinit var screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>

    private val defaultPlayerSettingsManager = DefaultPlayerSettingsManager()

    private val gestureActionPickerManager by lazy {
        ScreenshotGestureActionPickerManager(capabilityAvailability)
    }

    // Latest destination resources for the capture/link destination pickers.
    private var destinationTargets: List<MediaResource> = emptyList()

    // RECORD_AUDIO consent for the microphone-recording master toggle.
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

    // Returns from the system "draw over other apps" screen; enable the overlay only if granted.
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val controller = screenGestureControllers.firstOrNull() ?: return@registerForActivityResult
        if (controller.isOverlayPermissionGranted(requireContext())) {
            controller.setEnabled(true)
            viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = true))
        } else {
            binding.rowGestureOverlayEnabled.setCheckedSilently(false)
        }
    }

    private val notificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateScheduledNotificationPermissionButton()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsDestinationsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupExpandableSections()
        setupScheduledSection()
        observeData()
        checkAndExpandScheduledSection()
    }
    
    override fun onResume() {
        super.onResume()
        updateScheduledNotificationPermissionButton()
    }

    /**
     * If SettingsActivity was launched from Browse with EXTRA_SOURCE_RESOURCE_ID,
     * auto-open ScheduledOperationDialog with that resource pre-selected as source.
     * The extra is consumed (removed) so it doesn't re-trigger on orientation change.
     */
    private fun checkAndOpenAutomateDialog() {
        if (!BuildConfig.ENABLE_SCHEDULED_OPERATIONS) return
        if (!viewModel.settings.value.enableScheduledOperations) return
        val sourceId = requireActivity().intent.getLongExtra(
            SettingsActivity.EXTRA_SOURCE_RESOURCE_ID, -1L
        )
        if (sourceId == -1L) return
        requireActivity().intent.removeExtra(SettingsActivity.EXTRA_SOURCE_RESOURCE_ID)
        val allResources = viewModel.resources.value
        val destinations = viewModel.destinations.value
        ScheduledOperationDialog(
            context = requireContext(),
            resources = allResources,
            destinations = destinations,
            existing = null,
            prefilledSourceId = sourceId,
            onSave = { op -> scheduledViewModel.upsert(op) }
        ).show()
    }

    /**
     * If SettingsActivity was launched from the Scheduled Tasks widget with EXTRA_OPEN_SCHEDULED,
     * expand the scheduled-operations section so the user lands directly on it. Extra is consumed
     * so it doesn't re-trigger on orientation change.
     */
    private fun checkAndExpandScheduledSection() {
        if (!BuildConfig.ENABLE_SCHEDULED_OPERATIONS) return
        if (!requireActivity().intent.getBooleanExtra(SettingsActivity.EXTRA_OPEN_SCHEDULED, false)) return
        requireActivity().intent.removeExtra(SettingsActivity.EXTRA_OPEN_SCHEDULED)
        Timber.d("S0353: settings opened to scheduled section from widget")
        binding.headerScheduled.setExpanded(true, notify = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupViews() {
        binding.rowUseTrash.setTrailingControl(binding.btnClearTrash)

        // Copying switches
        binding.rowEnableCopying.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableCopying = isChecked))
            updateCopyOptionsVisibility(isChecked)
        }
        
        binding.rowGoToNextAfterCopy.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(goToNextAfterCopy = isChecked))
        }
        
        binding.rowOverwriteOnCopy.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(overwriteOnCopy = isChecked))
        }
        
        // Moving switches
        binding.rowEnableMoving.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableMoving = isChecked))
            updateMoveOptionsVisibility(isChecked)
        }
        
        binding.rowOverwriteOnMove.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(overwriteOnMove = isChecked))
        }
        
        binding.iconHelpDestinations.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_destinations_title,
                R.string.tooltip_destinations_message
            )
        }

        // Safety & Confirmation group (moved from General settings)
        binding.rowEnableSafeMode.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableSafeMode = isChecked))
            binding.layoutConfirmDelete.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.layoutConfirmMove.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.rowConfirmDelete.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(confirmDelete = isChecked))
        }
        binding.rowConfirmMove.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(confirmMove = isChecked))
        }
        binding.rowUseTrash.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(useTrash = isChecked))
            binding.btnClearTrash.isVisible = isChecked
        }
        binding.btnClearTrash.setOnClickListener {
            viewModel.clearAllTrash(requireContext())
        }

        // RecyclerView setup
        adapter = DestinationsAdapter(
            onMoveUp = { position -> moveDestination(position, -1) },
            onMoveDown = { position -> moveDestination(position, 1) },
            onDelete = { position -> deleteDestination(position) },
            onColorClick = { resource -> showColorPicker(resource) }
        )
        
        // Use GridLayoutManager with 2 columns in landscape, 1 in portrait
        setupDestinationsLayoutManager()
        binding.rvDestinations.adapter = adapter
        
        binding.btnAddDestination.setOnClickListener {
            showAddDestinationDialog()
        }

        // Max Recipients
        val maxRecipientsOptions = arrayOf("5", "10", "15", "20", "25", "30")
        val maxRecipientsAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, maxRecipientsOptions)
        binding.etMaxRecipients.setAdapter(maxRecipientsAdapter)
        binding.etMaxRecipients.setOnItemClickListener { _, _, position, _ ->
            val limit = maxRecipientsOptions[position].toInt()
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(maxRecipients = limit))
        }

        binding.etMaxRecipients.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !isUpdatingFromSettings) {
                val text = binding.etMaxRecipients.text.toString()
                val limit = text.toIntOrNull()
                if (limit != null && limit in 1..30) {
                    val current = viewModel.settings.value
                    if (current.maxRecipients != limit) {
                        viewModel.updateSettings(current.copy(maxRecipients = limit))
                        binding.tilMaxRecipients.error = null
                    }
                } else {
                    // Invalid input
                    binding.tilMaxRecipients.error = getString(R.string.max_recipients_error)
                    // Restore previous valid value
                    binding.etMaxRecipients.setText(getString(R.string.number_format, viewModel.settings.value.maxRecipients))
                }
            }
        }
        
        binding.etMaxRecipients.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                binding.etMaxRecipients.clearFocus()
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.etMaxRecipients.windowToken, 0)
                true
            } else {
                false
            }
        }

        // ── Moved groups wiring ──

        applyFlavorRestrictions()

        // Capture group (camera photos, video recording, microphone).
        setupCaptureSection()

        // Screen-gesture overlay group (noLegal-only capability).
        setupSystemAppsSection()

        // Default-player registration buttons inside containerSystemApps.
        defaultPlayerSettingsManager.bind(this, binding, mediaCapabilities)

        // OCR/Translation toggles (containerOtherFeatures).
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

        // Behaviour group rows.
        binding.rowPreventSleep.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(preventSleep = isChecked))
        }
        binding.rowDetailedErrors.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showDetailedErrors = isChecked))
        }
        binding.rowResumeOnNextLaunch.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(resumeOnNextLaunch = isChecked))
        }
        binding.rowDefaultRememberFileList.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultRememberFileList = isChecked))
        }

        // Other features group rows.
        binding.rowShowBlackScreenButton.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showBlackScreenButton = isChecked))
        }
        binding.rowEnableCalculator.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableCalculator = isChecked))
        }
        binding.rowEmbeddedGame.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateEmbeddedGameEnabled(isChecked)
        }

        // System apps group rows.
        // S0162: hide rotation row on non-sensor devices.
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
        // Only destinations are valid targets: LinkDownloadWriter resolves the stored id via
        // GetDestinationsUseCase and falls back to Downloads when cleared/missing.
        binding.rowLinkAutodownloadResource.setOnClickListener {
            showDestinationPicker(
                currentResourceId = viewModel.settings.value.linkAutoDownloadResourceId
            ) { resource ->
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(linkAutoDownloadResourceId = resource?.id))
            }
        }

        // Controls & Keybindings entry row.
        binding.rowControlsKeybindings.setOnClickListener {
            SettingsActivity.openKeybindingRemap(requireContext())
        }

        // Reset Management settings button (Step 3.8).
        binding.btnResetOperationsSection.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_operations_section_title)
                .setMessage(R.string.reset_operations_section_message)
                .setPositiveButton(R.string.reset) { _, _ ->
                    viewModel.resetOperationsSection()
                    Snackbar.make(binding.root, R.string.reset_operations_section_success, Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
    
    private fun showAddDestinationDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val availableResources = viewModel.getWritableNonDestinationResources()
            
            if (availableResources.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    R.string.no_writable_resources_destinations,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            
            val items = availableResources.map { "${it.name} (${it.path})" }.toTypedArray()
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.select_destination_title)
                .setItems(items) { _, which ->
                    val selectedResource = availableResources[which]
                    viewModel.addDestination(selectedResource)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.destination_added, selectedResource.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
    
    private fun showColorPicker(resource: MediaResource) {
        ColorPickerDialog.newInstance(
            initialColor = resource.destinationColor,
            onColorSelected = { color ->
                viewModel.updateDestinationColor(resource, color)
            }
        ).show(parentFragmentManager, "ColorPickerDialog")
    }
    
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect { settings ->
                        withSettingsUpdate {
                            binding.rowEnableScheduledOps.setCheckedSilently(settings.enableScheduledOperations)
                            binding.containerScheduledContent.isVisible = settings.enableScheduledOperations
                            binding.layoutScheduledActions.isVisible = settings.enableScheduledOperations
                            binding.rowEnableCopying.setCheckedSilently(settings.enableCopying)
                            binding.rowGoToNextAfterCopy.setCheckedSilently(settings.goToNextAfterCopy)
                            binding.rowOverwriteOnCopy.setCheckedSilently(settings.overwriteOnCopy)
                            binding.rowEnableMoving.setCheckedSilently(settings.enableMoving)
                            binding.rowOverwriteOnMove.setCheckedSilently(settings.overwriteOnMove)

                            // Safety & Confirmation switches
                            binding.rowEnableSafeMode.setCheckedSilently(settings.enableSafeMode)
                            binding.rowConfirmDelete.setCheckedSilently(settings.confirmDelete)
                            binding.rowConfirmMove.setCheckedSilently(settings.confirmMove)
                            binding.rowUseTrash.setCheckedSilently(settings.useTrash)
                            binding.btnClearTrash.isVisible = settings.useTrash
                            binding.layoutConfirmDelete.visibility =
                                if (settings.enableSafeMode) View.VISIBLE else View.GONE
                            binding.layoutConfirmMove.visibility =
                                if (settings.enableSafeMode) View.VISIBLE else View.GONE

                            if (binding.etMaxRecipients.text.toString() != settings.maxRecipients.toString()) {
                                binding.etMaxRecipients.setText(getString(R.string.number_format, settings.maxRecipients))
                            }

                            // Behaviour group (moved from Player tab).
                            if (binding.rowPreventSleep.isChecked != settings.preventSleep) {
                                binding.rowPreventSleep.setCheckedSilently(settings.preventSleep)
                            }
                            if (binding.rowDetailedErrors.isChecked != settings.showDetailedErrors) {
                                binding.rowDetailedErrors.setCheckedSilently(settings.showDetailedErrors)
                            }
                            if (binding.rowResumeOnNextLaunch.isChecked != settings.resumeOnNextLaunch) {
                                binding.rowResumeOnNextLaunch.setCheckedSilently(settings.resumeOnNextLaunch)
                            }
                            if (binding.rowDefaultRememberFileList.isChecked != settings.defaultRememberFileList) {
                                binding.rowDefaultRememberFileList.setCheckedSilently(settings.defaultRememberFileList)
                            }

                            // OtherFeatures group (moved from Player tab).
                            val hasOcrAndTranslation = capabilityAvailability.isTranslationAvailable() &&
                                DeviceCapabilities.isOcrSupported(requireContext())
                            if (hasOcrAndTranslation) {
                                if (binding.rowCameraOcrTranslationEnabled.isChecked != settings.cameraOcrTranslationEnabled) {
                                    binding.rowCameraOcrTranslationEnabled.setCheckedSilently(settings.cameraOcrTranslationEnabled)
                                }
                                if (binding.rowCameraOcrOnly.isChecked != settings.cameraOcrOnly) {
                                    binding.rowCameraOcrOnly.setCheckedSilently(settings.cameraOcrOnly)
                                }
                                binding.layoutCameraOcrOnly.isVisible = settings.cameraOcrTranslationEnabled
                            }
                            // Camera Photos rows (inverted master/ask-filename flags).
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
                            // Video recording rows (master toggle inverted).
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
                            // Microphone recording rows (feature-gated).
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
                            if (binding.rowShowBlackScreenButton.isChecked != settings.showBlackScreenButton) {
                                binding.rowShowBlackScreenButton.setCheckedSilently(settings.showBlackScreenButton)
                            }
                            if (binding.rowEnableCalculator.isChecked != settings.enableCalculator) {
                                binding.rowEnableCalculator.setCheckedSilently(settings.enableCalculator)
                            }
                            if (binding.rowEmbeddedGame.isChecked != settings.embeddedGameEnabled) {
                                binding.rowEmbeddedGame.setCheckedSilently(settings.embeddedGameEnabled)
                            }

                            // SystemApps group (moved from Player tab).
                            if (binding.rowFollowSystemRotation.isChecked != settings.followSystemRotation) {
                                binding.rowFollowSystemRotation.setCheckedSilently(settings.followSystemRotation)
                            }
                            if (BuildConfig.SUPPORTS_DEFAULT_PLAYER) {
                                if (binding.rowPrimaryMediaPlayer.isChecked != settings.isPrimaryMediaPlayer) {
                                    binding.rowPrimaryMediaPlayer.setCheckedSilently(settings.isPrimaryMediaPlayer)
                                }
                                if (binding.rowAcceptSharedFiles.isChecked != settings.acceptSharedFiles) {
                                    binding.rowAcceptSharedFiles.setCheckedSilently(settings.acceptSharedFiles)
                                }
                            }
                            if (binding.rowLinkAutodownloadEnabled.isChecked != settings.linkAutoDownloadEnabled) {
                                binding.rowLinkAutodownloadEnabled.setCheckedSilently(settings.linkAutoDownloadEnabled)
                            }
                            if (binding.rowLinkAutodownloadOpenInPlayer.isChecked != settings.linkAutoDownloadOpenInPlayer) {
                                binding.rowLinkAutodownloadOpenInPlayer.setCheckedSilently(settings.linkAutoDownloadOpenInPlayer)
                            }
                            // Disable child controls when master link-download toggle is off.
                            binding.rowLinkAutodownloadOpenInPlayer.isEnabled = settings.linkAutoDownloadEnabled
                            binding.rowLinkAutodownloadResource.isEnabled = settings.linkAutoDownloadEnabled
                            binding.tvLinkAutodownloadResourceValue.isEnabled = settings.linkAutoDownloadEnabled
                            refreshDestinationLabel(
                                resourceId = settings.linkAutoDownloadResourceId?.toString(),
                                target = binding.tvLinkAutodownloadResourceValue,
                                fallbackRes = R.string.link_autodownload_resource_not_set,
                            )

                            // ScreenGestures group (noLegal-only).
                            if (screenGestureControllers.isNotEmpty()) {
                                if (binding.rowGestureOverlayEnabled.isChecked != settings.gestureOverlayEnabled) {
                                    binding.rowGestureOverlayEnabled.setCheckedSilently(settings.gestureOverlayEnabled)
                                }
                                binding.tvScreenshotGestureActionDownValue.text =
                                    gestureActionPickerManager.labelFor(requireContext(), settings.screenshotGestureActionDown)
                                binding.tvScreenshotGestureActionRightValue.text =
                                    gestureActionPickerManager.labelFor(requireContext(), settings.screenshotGestureActionRight)
                                binding.tvScreenshotGestureActionUpValue.text =
                                    gestureActionPickerManager.labelFor(requireContext(), settings.screenshotGestureActionUp)
                                refreshDestinationLabel(
                                    settings.screenshotDestinationResourceId,
                                    binding.tvScreenshotDestinationValue,
                                    R.string.setting_screenshot_destination_default
                                )
                            }
                        }

                        updateCopyOptionsVisibility(settings.enableCopying)
                        updateMoveOptionsVisibility(settings.enableMoving)
                    }
                }

                launch {
                    viewModel.destinations.collect { destinations ->
                        adapter.submitList(destinations)
                        destinationTargets = destinations
                        // Update visibility based on current destinations list
                        updateAddDestinationVisibility(destinations.isNotEmpty())
                        // "Add destinations first" hint - only when no destinations exist
                        binding.tvNoScheduledOps.isVisible = destinations.isEmpty()
                    }
                }

                // Keep resources StateFlow warm and trigger automate dialog once resources arrive.
                launch {
                    viewModel.resources.collect { resources ->
                        if (resources.isNotEmpty()) checkAndOpenAutomateDialog()
                    }
                }
                
                // Initial check for resources availability
                launch {
                    updateAddDestinationVisibility(viewModel.destinations.value.isNotEmpty())
                }
            }
        }
    }
    
    private fun updateCopyOptionsVisibility(enabled: Boolean) {
        binding.layoutCopyOptions.isVisible = enabled
        binding.layoutOverwriteCopyWrapper.isVisible = enabled
    }
    
    private fun updateMoveOptionsVisibility(enabled: Boolean) {
        binding.layoutOverwriteMoveWrapper.isVisible = enabled
    }
    
    private fun setupDestinationsLayoutManager() {
        val spanCount = resources.getInteger(R.integer.destinations_column_count)
        binding.rvDestinations.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), spanCount)
    }

    private fun setupExpandableSections() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sections = mutableListOf(
            ExpandableSection(binding.headerSafety, binding.containerSafety, KEY_SAFETY_EXPANDED, false),
            ExpandableSection(binding.headerCopyMove, binding.containerFileOperations, KEY_FILE_OPS_EXPANDED, false),
            ExpandableSection(binding.headerDestinations, binding.containerDestinations, KEY_DESTINATIONS_EXPANDED, false),
        )
        if (BuildConfig.ENABLE_SCHEDULED_OPERATIONS) {
            sections += ExpandableSection(binding.headerScheduled, binding.containerScheduled, KEY_SCHEDULED_EXPANDED, false)
        } else {
            binding.headerScheduled.isVisible = false
            binding.containerScheduled.isVisible = false
        }
        sections += ExpandableSection(binding.headerBehaviour, binding.containerBehaviour, KEY_BEHAVIOUR_EXPANDED, false)
        sections += ExpandableSection(binding.headerOtherFeatures, binding.containerOtherFeatures, KEY_OTHER_FEATURES_EXPANDED, false)
        sections += ExpandableSection(binding.headerSystemApps, binding.containerSystemApps, KEY_SYSTEM_APPS_EXPANDED, false)
        sections += ExpandableSection(binding.headerScreenGestures, binding.containerScreenGestures, KEY_SCREEN_GESTURES_EXPANDED, false)

        sections.forEach { section ->
            val expanded = prefs.getBoolean(section.prefKey, section.defaultExpanded)
            section.header.setExpanded(expanded, notify = false)
            section.container.isVisible = expanded
            section.header.setOnExpandedChangeListener { isExpanded ->
                section.container.isVisible = isExpanded
                requireContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(section.prefKey, isExpanded)
                    .apply()
            }
        }
    }

    private fun setupScheduledSection() {
        updateScheduledNotificationPermissionButton()
        binding.rowEnableScheduledOps.setTrailingControl(binding.layoutScheduledActions)

        binding.rowEnableScheduledOps.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableScheduledOperations = isChecked))
            binding.containerScheduledContent.isVisible = isChecked
            binding.layoutScheduledActions.isVisible = isChecked
            if (isChecked) checkAndRequestScheduledPermissions()
        }

        scheduledAdapter = ScheduledOperationsAdapter(
            onToggle = { op -> scheduledViewModel.toggleEnabled(op) },
            onEdit = { op -> showScheduledOperationDialog(op) },
            onDelete = { op -> confirmDeleteScheduledOp(op) },
            onRunNow = { op -> scheduledViewModel.runNow(op.id) },
            resourceNameProvider = { id ->
                viewModel.resources.value.find { it.id == id }?.name
            }
        )
        binding.rvScheduledOps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.rvScheduledOps.adapter = scheduledAdapter

        binding.btnAddScheduledOp.setOnClickListener { showScheduledOperationDialog(null) }

        binding.btnScheduledLog.setOnClickListener {
            // Reuses the shared ScrollableTextDialog; "clear" is an extra action that empties the log
            // in place (the dialog stays open) via the returned dialog's message TextView.
            val emptyMsg = getString(R.string.scheduled_ops_log_empty)
            val log = scheduledViewModel.getLog()
            var logDialog: androidx.appcompat.app.AlertDialog? = null
            logDialog = ScrollableTextDialog.show(
                context = requireContext(),
                title = getString(R.string.scheduled_ops_log_title),
                message = log.ifBlank { emptyMsg },
                monospace = true,
                showShare = false,
                showSave = false,
                extraAction = ScrollableTextDialog.ExtraAction(
                    icon = R.drawable.ic_delete_sweep,
                    contentDescription = getString(R.string.scheduled_ops_log_clear),
                    dismissOnClick = false,
                    onClick = {
                        scheduledViewModel.clearLog()
                        logDialog?.findViewById<android.widget.TextView>(R.id.tvErrorMessage)?.text = emptyMsg
                    }
                )
            )
        }

        binding.btnClearAllScheduled.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.scheduled_ops_confirm_clear)
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        scheduledViewModel.operations.value.forEach { scheduledViewModel.delete(it.id) }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        collectOnLifecycle(scheduledViewModel.operations) { ops ->
            scheduledAdapter.submitList(ops)
        }
    }

    private fun showScheduledOperationDialog(existing: ScheduledOperation?) {
        val allResources = viewModel.resources.value
        val destinations = viewModel.destinations.value
        ScheduledOperationDialog(
            context = requireContext(),
            resources = allResources,
            destinations = destinations,
            existing = existing,
            onSave = { op -> scheduledViewModel.upsert(op) }
        ).show()
    }

    private fun confirmDeleteScheduledOp(op: ScheduledOperation) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.scheduled_ops_confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ -> scheduledViewModel.delete(op.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        setupDestinationsLayoutManager()
    }
    
    private suspend fun updateAddDestinationVisibility(hasDestinations: Boolean) {
        val availableResources = viewModel.getWritableNonDestinationResources()
        val hasResources = availableResources.isNotEmpty()
        
        binding.btnAddDestination.isVisible = hasResources
        // Show message only if no destinations AND no available resources
        binding.tvNoResourcesMessage.isVisible = !hasResources && !hasDestinations
    }
    
    private fun moveDestination(position: Int, direction: Int) {
        val destinations = viewModel.destinations.value
        if (position < 0 || position >= destinations.size) return
        
        val resource = destinations[position]
        viewModel.moveDestination(resource, direction)
    }
    
    private fun deleteDestination(position: Int) {
        val destinations = viewModel.destinations.value
        if (position < 0 || position >= destinations.size) return
        
        val resource = destinations[position]
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_destination_title)
            .setMessage(getString(R.string.remove_destination_message, resource.name))
            .setPositiveButton(R.string.remove_action) { _, _ ->
                viewModel.removeDestination(resource)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.destination_removed, resource.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun applyFlavorRestrictions() {
        val hasOcrAndTranslation = capabilityAvailability.isTranslationAvailable() &&
            DeviceCapabilities.isOcrSupported(requireContext())
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

    /**
     * Wires the camera-photos, video-recording, and microphone-recording capture rows
     * plus their destination selectors. Inverted-flag semantics for the camera/video master
     * toggles preserve the original implementation from PlaybackSettingsFragment.
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

        // ── Video recording ──
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
     * Wires the screen-gesture overlay rows. The whole gestures card is hidden on flavors
     * without the capability (empty controller set). Enabling the overlay routes the user
     * to grant draw-over-apps permission.
     */
    private fun setupSystemAppsSection() {
        val controller = screenGestureControllers.firstOrNull()
        if (controller == null) {
            binding.groupScreenGestures.isVisible = false
            return
        }
        binding.rowGestureOverlayEnabled.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            if (isChecked) {
                if (!controller.isOverlayPermissionGranted(requireContext())) {
                    showGesturePermissionDialog(controller)
                    return@setOnCheckedChangeListener
                }
                controller.setEnabled(true)
                viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = true))
            } else {
                controller.setEnabled(false)
                viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = false))
            }
        }
        binding.rowScreenshotDestination.setOnClickListener {
            showDestinationPicker(
                currentResourceId = viewModel.settings.value.screenshotDestinationResourceId?.toLongOrNull()
            ) { resource ->
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(screenshotDestinationResourceId = resource?.id?.toString()))
            }
        }
        binding.rowScreenshotGestureActionDown.setOnClickListener {
            gestureActionPickerManager.showPicker(
                requireContext(),
                viewModel.settings.value.screenshotGestureActionDown
            ) { picked ->
                viewModel.updateSettings(viewModel.settings.value.copy(screenshotGestureActionDown = picked))
            }
        }
        binding.rowScreenshotGestureActionRight.setOnClickListener {
            gestureActionPickerManager.showPicker(
                requireContext(),
                viewModel.settings.value.screenshotGestureActionRight
            ) { picked ->
                viewModel.updateSettings(viewModel.settings.value.copy(screenshotGestureActionRight = picked))
            }
        }
        binding.rowScreenshotGestureActionUp.setOnClickListener {
            gestureActionPickerManager.showPicker(
                requireContext(),
                viewModel.settings.value.screenshotGestureActionUp
            ) { picked ->
                viewModel.updateSettings(viewModel.settings.value.copy(screenshotGestureActionUp = picked))
            }
        }
        binding.btnOpenAccessibilitySettings.setOnClickListener {
            Timber.d("S0449: accessibility shortcut tapped in screen-gestures group")
            try {
                overlayPermissionLauncher.launch(controller.permissionSettingsIntent(requireContext()))
            } catch (e: ActivityNotFoundException) {
                // Accessibility settings screen unreachable on this ROM: fall back to the
                // educational dialog, which routes to alternative entry points (S0449 ADR-1).
                Timber.w(e, "Accessibility settings intent unresolved; showing fallback dialog")
                showGesturePermissionDialog(controller)
            }
        }
    }

    /**
     * Instructional gate before sending the user to the permission screen. Sideloaded (noLegal)
     * builds cannot flip the accessibility toggle directly — the exact tap sequence is spelled out
     * here, with shortcuts to both the accessibility screen and App info. Any exit without the
     * grant reverts the row (handled in onDismiss so back-press / outside-tap are covered too).
     */
    private fun showGesturePermissionDialog(controller: ScreenGestureOverlayController) {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.screenshot_gesture_permission_dialog_title)
            .setMessage(controller.permissionRationaleResId())
            .setPositiveButton(R.string.screenshot_gesture_open_settings) { _, _ ->
                overlayPermissionLauncher.launch(controller.permissionSettingsIntent(requireContext()))
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener {
                if (!controller.isOverlayPermissionGranted(requireContext())) {
                    binding.rowGestureOverlayEnabled.setCheckedSilently(false)
                }
            }
        if (controller.isFallbackCaptureAvailable()) {
            builder.setNeutralButton(R.string.screenshot_gesture_use_old_method) { _, _ ->
                overlayPermissionLauncher.launch(controller.fallbackPermissionSettingsIntent(requireContext()))
            }
        }
        builder.show()
    }

    /**
     * Single-choice picker over recipient resources usable as a capture target. Restricted to
     * resources that are also destinations (a real folder). Includes an explicit "(clear)" entry
     * that resolves the setting back to its documented fallback.
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

    inner class DestinationsAdapter(
        private val onMoveUp: (Int) -> Unit,
        private val onMoveDown: (Int) -> Unit,
        private val onDelete: (Int) -> Unit,
        private val onColorClick: (MediaResource) -> Unit
    ) : ListAdapter<MediaResource, DestinationsAdapter.ViewHolder>(DestinationDiffCallback) {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDestinationBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position), position)
        }
        
        inner class ViewHolder(private val binding: ItemDestinationBinding) : 
            RecyclerView.ViewHolder(binding.root) {
            
            fun bind(resource: MediaResource, position: Int) {
                val order = resource.destinationOrder ?: -1
                // Display order for user: order+1 (0-based to 1-based)
                binding.tvDestinationNumber.text = if (order >= 0) (order + 1).toString() else ""
                binding.tvDestinationName.text = resource.name
                binding.tvDestinationPath.text = resource.path
                
                // Set color indicator from database
                binding.viewColorIndicator.setBackgroundColor(resource.destinationColor)
                binding.viewColorIndicator.setOnClickListener {
                    onColorClick(resource)
                }
                
                // Long click on item to change color
                binding.root.setOnLongClickListener {
                    onColorClick(resource)
                    true
                }
                
                // Move up button
                binding.btnMoveUp.isEnabled = position > 0
                binding.btnMoveUp.setOnClickListener { onMoveUp(position) }
                
                // Move down button
                binding.btnMoveDown.isEnabled = position < itemCount - 1
                binding.btnMoveDown.setOnClickListener { onMoveDown(position) }
                
                // Delete button
                binding.btnDelete.setOnClickListener { onDelete(position) }
            }
        }
    }
    
    private fun updateScheduledNotificationPermissionButton() {
        val btn = binding.btnScheduledNotificationPermission ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            btn.isVisible = false
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        btn.isVisible = !hasPermission
        btn.setOnClickListener {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkAndRequestScheduledPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotification = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasNotification) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } catch (_: Exception) { }
        }
    }

    private object DestinationDiffCallback : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem == newItem
        }
    }
}
