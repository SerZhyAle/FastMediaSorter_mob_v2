package com.sza.fastmediasorter.ui.settings.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.core.util.DeviceCapabilities
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog
import com.sza.fastmediasorter.ui.settings.ScheduledOperationsViewModel
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerSettingsManager
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.ui.settings.helpers.OperationsCaptureManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsDestinationsManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsGesturesManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsScheduledManager
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.ui.settings.helpers.ScreenshotGestureActionPickerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@android.annotation.SuppressLint("SetTextI18n")
@AndroidEntryPoint
class OperationsSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsDestinationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    private val scheduledViewModel: ScheduledOperationsViewModel by activityViewModels()

    @Inject
    lateinit var capabilityAvailability: CapabilityAvailability

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    // Empty on every flavor except noLegal, where the gesture-overlay capability contributes one.
    @Inject
    lateinit var screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>

    // Empty except on standard + noLegal, where the shared capture engine binds the menu launcher.
    @Inject
    lateinit var menuScreenshotLaunchers: Set<@JvmSuppressWildcards MenuScreenshotLauncher>

    private val defaultPlayerSettingsManager = DefaultPlayerSettingsManager()

    private val gestureActionPickerManager by lazy {
        ScreenshotGestureActionPickerManager(capabilityAvailability)
    }

    private val sectionsManager by lazy { CollapsibleSectionsManager(requireContext()) }
    private val destinationsManager by lazy { OperationsDestinationsManager(binding, viewModel, this) }
    private val scheduledManager by lazy {
        OperationsScheduledManager(
            binding, viewModel, scheduledViewModel, this, mediaCapabilities, notificationsPermissionLauncher
        ) { isUpdatingFromSettings }
    }
    private val captureManager by lazy {
        OperationsCaptureManager(
            binding, viewModel, mediaCapabilities, recordAudioPermissionLauncher,
            { isUpdatingFromSettings }, ::showDestinationPicker, ::refreshDestinationLabel, this
        )
    }
    private val gesturesManager by lazy {
        OperationsGesturesManager(
            binding, viewModel, this, screenGestureControllers, gestureActionPickerManager,
            overlayPermissionLauncher, { isUpdatingFromSettings }, ::showDestinationPicker, ::refreshDestinationLabel
        )
    }

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
    private val overlayPermissionLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            gesturesManager.onOverlayPermissionResult()
        }

    private val notificationsPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            scheduledManager.onResume()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsDestinationsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("S0651: Operations settings opened - mic-recording ask-filename shown as child of enable-recording")
        setupViews()
        setupCollapsibleSections()
        scheduledManager.setup()
        observeData()
        scheduledManager.checkAndExpandFromIntent()
    }

    // S0535: unified collapsible groups - one orchestrator + consolidated store, default collapsed.
    // The Scheduled group is hidden when the flavor disables scheduled operations.
    private fun setupCollapsibleSections() {
        fun register(header: CollapsibleSectionHeader, container: View, key: String) {
            sectionsManager.register(header, container, key, defaultExpanded = false)
        }
        register(binding.headerSafety, binding.containerSafety, "operations__safety")
        register(binding.headerCopyMove, binding.containerFileOperations, "operations__file_ops")
        register(binding.headerDestinations, binding.containerDestinations, "operations__destinations")
        if (BuildConfig.ENABLE_SCHEDULED_OPERATIONS) {
            register(binding.headerScheduled, binding.containerScheduled, "operations__scheduled")
        } else {
            binding.headerScheduled.isVisible = false
            binding.containerScheduled.isVisible = false
        }
        register(binding.headerBehaviour, binding.containerBehaviour, "operations__behaviour")
        register(binding.headerOtherFeatures, binding.containerOtherFeatures, "operations__other_features")
        register(binding.headerAdditionalPrograms, binding.containerAdditionalPrograms, "operations__additional_programs")
        register(binding.headerSystemApps, binding.containerSystemApps, "operations__system_apps")
        register(binding.headerScreenGestures, binding.containerScreenGestures, "operations__screen_gestures")
    }

    override fun onResume() {
        super.onResume()
        scheduledManager.onResume()
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

        // Destinations list (RecyclerView, add/remove/reorder/color) owned by the manager.
        destinationsManager.setup()

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
        captureManager.setup()
        captureManager.setupScreenshotAction(menuScreenshotLaunchers, requireActivity())

        // Screen-gesture overlay group (noLegal-only capability).
        gesturesManager.setup()

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
        binding.rowKeepScreenOnPlayer.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(keepScreenOnPlayer = isChecked))
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
                viewModel.updateSettings(current.copy(programFollowSystemRotation = isChecked))
            }
        }
        // S0602: visibility and persisted-state reset of the default-player toggles are owned by
        // applyFlavorRestrictions(); here we only attach listeners on flavors that support them.
        if (mediaCapabilities.supportsDefaultPlayer) {
            binding.rowPrimaryMediaPlayer.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                DefaultPlayerManager.applyPrimaryPlayerState(requireContext(), isChecked, mediaCapabilities)
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(isPrimaryMediaPlayer = isChecked))
            }
            binding.rowAcceptSharedFiles.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                DefaultPlayerManager.applyShareReceiverState(requireContext(), isChecked, mediaCapabilities)
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
        binding.btnSelectLinkAutodownloadResource.setOnClickListener {
            showDestinationPicker(
                currentResourceId = viewModel.settings.value.linkAutoDownloadResourceId
            ) { resource ->
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(linkAutoDownloadResourceId = resource?.id))
            }
        }

        // Controls & Keybindings entry row.
        binding.rowControlsKeybindings.setOnRowClickListener {
            SettingsActivity.openKeybindingRemap(requireContext())
        }

        // Reset Management settings button (Step 3.8).
        binding.btnResetOperationsSection.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
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
    
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect { settings ->
                        withSettingsUpdate {
                            scheduledManager.render(settings)
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
                            if (binding.rowKeepScreenOnPlayer.isChecked != settings.keepScreenOnPlayer) {
                                binding.rowKeepScreenOnPlayer.setCheckedSilently(settings.keepScreenOnPlayer)
                            }
                            // S0438: dependent row visible only when global Prevent Sleep is off.
                            binding.rowKeepScreenOnPlayer.visibility =
                                if (!settings.preventSleep) View.VISIBLE else View.GONE
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
                            captureManager.render(settings)
                            if (binding.rowEnableCalculator.isChecked != settings.enableCalculator) {
                                binding.rowEnableCalculator.setCheckedSilently(settings.enableCalculator)
                            }
                            if (binding.rowEmbeddedGame.isChecked != settings.embeddedGameEnabled) {
                                binding.rowEmbeddedGame.setCheckedSilently(settings.embeddedGameEnabled)
                            }

                            // SystemApps group (moved from Player tab).
                            if (binding.rowFollowSystemRotation.isChecked != settings.programFollowSystemRotation) {
                                binding.rowFollowSystemRotation.setCheckedSilently(settings.programFollowSystemRotation)
                            }
                            if (mediaCapabilities.supportsDefaultPlayer) {
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
                            binding.btnSelectLinkAutodownloadResource.isEnabled = settings.linkAutoDownloadEnabled
                            binding.tvLinkAutodownloadResource.isEnabled = settings.linkAutoDownloadEnabled
                            refreshDestinationLabel(
                                resourceId = settings.linkAutoDownloadResourceId?.toString(),
                                fallbackRes = R.string.link_autodownload_resource_not_set,
                            ) { binding.tvLinkAutodownloadResource.text = it }

                            gesturesManager.render(settings)
                        }

                        updateCopyOptionsVisibility(settings.enableCopying)
                        updateMoveOptionsVisibility(settings.enableMoving)
                    }
                }

                // Keep resources StateFlow warm and trigger automate dialog once resources arrive.
                launch {
                    viewModel.resources.collect { resources ->
                        if (resources.isNotEmpty()) scheduledManager.onResourcesReady()
                    }
                }
            }
        }
        destinationsManager.observe()
    }
    
    private fun updateCopyOptionsVisibility(enabled: Boolean) {
        binding.layoutCopyOptions.isVisible = enabled
        binding.layoutOverwriteCopyWrapper.isVisible = enabled
    }
    
    private fun updateMoveOptionsVisibility(enabled: Boolean) {
        binding.layoutOverwriteMoveWrapper.isVisible = enabled
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        destinationsManager.onConfigurationChanged()
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

        val supportsDefaultPlayer = mediaCapabilities.supportsDefaultPlayer
        binding.rowPrimaryMediaPlayer.isVisible = supportsDefaultPlayer
        binding.rowAcceptSharedFiles.isVisible = supportsDefaultPlayer
        binding.layoutDefaultPlayerToggles.isVisible = supportsDefaultPlayer
        if (!supportsDefaultPlayer) {
            binding.rowPrimaryMediaPlayer.setCheckedSilently(false)
            binding.rowAcceptSharedFiles.setCheckedSilently(false)
            val current = viewModel.settings.value
            if (current.isPrimaryMediaPlayer || current.acceptSharedFiles) {
                viewModel.updateSettings(current.copy(
                    isPrimaryMediaPlayer = false,
                    acceptSharedFiles = false
                ))
            }
        }
    }

    /**
     * Single-choice picker over recipient resources usable as a capture target. Restricted to
     * resources that are also destinations (a real folder). The Clear action resolves the setting
     * back to its documented fallback.
     */
    private fun showDestinationPicker(
        currentResourceId: Long?,
        onPicked: (MediaResource?) -> Unit
    ) {
        ListSelectionDialog<MediaResource>(
            requireContext(),
            ListSelectionConfig(
                title = getString(R.string.setting_select_destination),
                lifecycleOwner = viewLifecycleOwner,
                loader = { destinationsManager.currentDestinations },
                formatter = object : ListSelectionAdapter.ItemFormatter<MediaResource> {
                    override fun getDisplayName(item: MediaResource): String = item.name
                },
                hasSelection = currentResourceId != null,
                isSelected = { it.id == currentResourceId },
                allowClear = true,
                emptyMessageRes = R.string.no_resources_available,
                errorMessageRes = R.string.no_resources_available,
                onSelected = onPicked,
            ),
        ).show()
    }

    /**
     * Resolves a destination resource label and writes it via [setLabel], falling back to [fallbackRes]
     * when unset/missing. S0567: takes a setter lambda so both plain TextViews (capture selectors) and
     * SettingsSelectionRow targets (link-autodownload, screenshot destination) share one resolver.
     */
    private fun refreshDestinationLabel(resourceId: String?, fallbackRes: Int, setLabel: (CharSequence) -> Unit) {
        val id = resourceId?.toLongOrNull()
        if (id == null) {
            setLabel(getString(fallbackRes))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val resource = viewModel.resourceRepository.getResourceById(id)
            setLabel(resource?.name ?: getString(fallbackRes))
        }
    }

}
