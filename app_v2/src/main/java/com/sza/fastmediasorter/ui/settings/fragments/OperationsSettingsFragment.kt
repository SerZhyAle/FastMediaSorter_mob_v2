package com.sza.fastmediasorter.ui.settings.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.TooltipCompat
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
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import com.sza.fastmediasorter.core.share.ShareTargetAvailabilityResolver
import com.sza.fastmediasorter.core.share.ShareTargetIconResolver
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.core.util.DeviceCapabilities
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.usecase.IsShareTargetEnabledUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.PlaceHomeWidgetOnLauncherDesktopUseCase
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog
import com.sza.fastmediasorter.ui.scheduledops.ScheduledOperationsActivity
import com.sza.fastmediasorter.ui.settings.DefaultAppsDialogFragment
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import com.sza.fastmediasorter.ui.settings.gesture.EdgeGestureConfigDialogFragment
import com.sza.fastmediasorter.ui.settings.helpers.DestinationLabelResolver
import com.sza.fastmediasorter.ui.settings.helpers.HomeWidgetSettingsHelper
import com.sza.fastmediasorter.ui.settings.helpers.LocalFolderDestinationPickerManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsCaptureManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsDestinationsManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsGesturesManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsProgramsManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsSectionsManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsSendCommandsManager
import com.sza.fastmediasorter.ui.settings.helpers.OperationsWearGroupManager
import com.sza.fastmediasorter.ui.stopwatch.StopwatchSettingsDialogFragment
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.widget.registry.HomeWidgetCatalog
import com.sza.fastmediasorter.widget.registry.HomeWidgetPinner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@android.annotation.SuppressLint("SetTextI18n")
@AndroidEntryPoint
class OperationsSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsDestinationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    // S1885: the paired-watch row reads Wear state, which belongs to the Wear view model rather
    // than to the settings one.
    private val wearSyncViewModel: WearSyncViewModel by activityViewModels()

    @Inject
    lateinit var capabilityAvailability: CapabilityAvailability

    @Inject
    lateinit var networkMonitorContract: NetworkMonitorContract

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    // S2390: Share targets registry, availability, icon resolvers and use case for «Send file to..» group
    @Inject
    lateinit var shareTargetRegistry: ShareTargetRegistry

    @Inject
    lateinit var shareTargetAvailabilityResolver: ShareTargetAvailabilityResolver

    @Inject
    lateinit var shareTargetIconResolver: ShareTargetIconResolver

    @Inject
    lateinit var isShareTargetEnabledUseCase: IsShareTargetEnabledUseCase

    // Empty on every flavor except noLegal, where the gesture-overlay capability contributes one.
    @Inject
    lateinit var screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>

    // S0774: empty except on standard + noLegal; gates the screen-recording settings rows.
    @Inject
    lateinit var screenVideoRecordingControllers: Set<@JvmSuppressWildcards ScreenVideoRecordingController>

    // Home-widget picker (relocated from General into the OS-interaction group).
    @Inject
    lateinit var homeWidgetCatalog: HomeWidgetCatalog

    @Inject
    lateinit var homeWidgetPinner: HomeWidgetPinner

    // S1170: the same picker's second destination - the app's own launcher desktop.
    @Inject
    lateinit var launcherModeContract: LauncherModeContract

    @Inject
    lateinit var launcherRoleManager: LauncherRoleManager

    @Inject
    lateinit var placeHomeWidgetOnLauncherDesktop: PlaceHomeWidgetOnLauncherDesktopUseCase

    @Inject
    lateinit var accessibilityControl: com.sza.fastmediasorter.core.screencapture.AccessibilityServiceControl

    private val sectionsHost by lazy { OperationsSectionsManager(binding, requireContext()) }
    private val destinationsManager by lazy { OperationsDestinationsManager(binding, viewModel, this) }
    private val sendCommandsManager by lazy {
        OperationsSendCommandsManager(
            fragment = this,
            binding = binding,
            viewModel = viewModel,
            shareTargetRegistry = shareTargetRegistry,
            shareTargetAvailabilityResolver = shareTargetAvailabilityResolver,
            shareTargetIconResolver = shareTargetIconResolver,
            isShareTargetEnabledUseCase = isShareTargetEnabledUseCase,
        )
    }
    private val localFolderDestinationPickerManager by lazy {
        LocalFolderDestinationPickerManager(this, viewModel, localFolderDestinationPickerLauncher)
    }

    // S2797: the scope is read per call - viewLifecycleOwner is a different object after recreation.
    private val destinationLabelResolver by lazy {
        DestinationLabelResolver({ viewLifecycleOwner.lifecycleScope }, viewModel.resourceRepository)
    }
    private val captureManager by lazy {
        OperationsCaptureManager(
            binding, viewModel, mediaCapabilities, screenVideoRecordingControllers.isNotEmpty(),
            recordAudioPermissionLauncher, locationPermissionLauncher,
            { isUpdatingFromSettings }, ::showDestinationPicker, ::refreshDestinationLabel, this
        )
    }
    private val wearGroupManager by lazy {
        OperationsWearGroupManager(
            binding,
            this,
            viewModel,
            mediaCapabilities,
            { isUpdatingFromSettings },
            wearSyncViewModel::refreshPairedWatchStatus,
        )
    }
    private val programsManager by lazy {
        OperationsProgramsManager(
            binding,
            viewModel,
            networkMonitorContract.isAvailableInBuild,
        ) { isUpdatingFromSettings }
    }
    private val gesturesManager by lazy {
        OperationsGesturesManager(
            binding,
            viewModel,
            this,
            screenGestureControllers,
            overlayPermissionLauncher,
            { isUpdatingFromSettings },
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
            val viewBinding = _binding ?: return@registerForActivityResult
            viewBinding.rowMicRecordingEnabled.setCheckedSilently(false)
            Snackbar.make(
                viewBinding.root,
                requireContext().permissionRationale(Manifest.permission.RECORD_AUDIO),
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    // S0766: ACCESS_FINE_LOCATION consent for the opt-in camera geotag toggle. Persist the flag only
    // on grant so coordinates are never embedded without explicit consent; revert the row on denial.
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.updateSettings(viewModel.settings.value.copy(cameraGeotagEnabled = true))
        } else {
            val viewBinding = _binding ?: return@registerForActivityResult
            viewBinding.rowCameraGeotag.setCheckedSilently(false)
            Snackbar.make(
                viewBinding.root,
                R.string.camera_geotag_permission_denied,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    // Returns from the system "draw over other apps" screen; enable the overlay only if granted.
    private val overlayPermissionLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            gesturesManager.onOverlayPermissionResult()
        }

    // S1010: separate SAF launcher for the "Local Folder" write-receiver option, kept apart from
    // S1009's scheduled-op launcher above so the two picks can never resolve into each other.
    private val localFolderDestinationPickerLauncher: ActivityResultLauncher<Uri?> =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            localFolderDestinationPickerManager.onFolderPicked(uri)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsDestinationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        sectionsHost.registerAll(wearGroupManager.isAvailableInBuild, BuildConfig.ENABLE_SCHEDULED_OPERATIONS)
        observeData()
        sectionsHost.handleIntentSection(requireActivity().intent)
    }

    /**
     * S1967: replaced the never-called `ensureSectionExpanded(sectionId)` that stood here. A section
     * name could not have worked - see the ticket - and the base now expands by the row's ancestors.
     */
    override fun collapsibleSections(): com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager =
        sectionsHost.sections()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupViews() {
        sendCommandsManager.setup()
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
        binding.rowEnableFileDoOperations.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableFileDoOperations = isChecked))
        }
        binding.btnClearTrash.setOnClickListener {
            viewModel.clearAllTrash(requireContext())
        }

        // Destinations list (RecyclerView, add/remove/reorder/color) owned by the manager.
        destinationsManager.setup()

        // Max Recipients
        val maxRecipientsOptions = arrayOf("5", "10", "15", "20", "25", "30")
        val maxRecipientsAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            maxRecipientsOptions
        )
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
                    binding.etMaxRecipients.setText(
                        getString(R.string.number_format, viewModel.settings.value.maxRecipients)
                    )
                }
            }
        }

        binding.etMaxRecipients.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                binding.etMaxRecipients.clearFocus()
                val imm = requireContext().getSystemService(
                    android.content.Context.INPUT_METHOD_SERVICE
                ) as android.view.inputmethod.InputMethodManager
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

        // Screen-gesture overlay group (noLegal-only capability).
        gesturesManager.setup()

        // S1883: Wear OS group - the master checkbox, the install-guide link and the companion button.
        wearGroupManager.setup()

        // S1885: the paired-watch row. Collected lifecycle-aware rather than in a bare launch, so the
        // bridge result is not delivered to a destroyed view (Rule 19).
        if (wearGroupManager.isAvailableInBuild) {
            collectOnLifecycle(wearSyncViewModel.pairedWatchStatus) { wearGroupManager.renderPairedWatch(it) }
        }

        // S0880: default-player registration UI now lives in DefaultAppsDialogFragment; this is its launcher.
        binding.btnOpenDefaultAppsDialog.setOnClickListener {
            DefaultAppsDialogFragment().show(childFragmentManager, DefaultAppsDialogFragment.TAG)
        }

        // Home-widget picker launcher (relocated from General into the OS-interaction group).
        HomeWidgetSettingsHelper(
            button = binding.buttonAddHomeWidget,
            fragment = this,
            catalog = homeWidgetCatalog,
            pinner = homeWidgetPinner,
            launcherButton = binding.buttonAddLauncherWidget,
            launcherModeContract = launcherModeContract,
            launcherRoleManager = launcherRoleManager,
            placeOnLauncherDesktop = placeHomeWidgetOnLauncherDesktop,
        ).setup()

        // S1035: launcher for the extracted edge-gesture configuration dialog (gated by OperationsGesturesManager).
        binding.btnOpenEdgeGestureConfig.setOnClickListener {
            EdgeGestureConfigDialogFragment().show(childFragmentManager, EdgeGestureConfigDialogFragment.TAG)
        }

        // S1411 ADR-5: the stopwatch's own options, the second way in beside its gear button. The row
        // above it only decides whether the tool is offered at all, so this one is never gated on it -
        // a user turning the tool on wants its options in the same place, not after a screen reload.
        binding.rowOpenStopwatchSettings.setOnRowClickListener {
            StopwatchSettingsDialogFragment.newInstance()
                .show(childFragmentManager, StopwatchSettingsDialogFragment.TAG)
        }

        // S3365: the settings keep exactly one entry point to the scheduled-operations program -
        // this link row; the card that used to sit here was deleted and the screen owns management.
        binding.rowOpenScheduledOpsScreen.setOnRowClickListener {
            startActivity(android.content.Intent(requireContext(), ScheduledOperationsActivity::class.java))
        }

        // OCR/Translation toggles (containerAdditionalPrograms).
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

        // S2516: the sub-program switches belong to their own manager now, so nothing about them is
        // bound inline here.
        programsManager.setup()

        // System apps group rows.
        // S1051: accessibility-shortcut control relocated here from the edge-gesture dialog. Visibility
        // follows the same silent-capture capability (noLegal); the click reuses the fragment's
        // overlayPermissionLauncher (the dialog used that very launcher before the move).
        val a11yController = screenGestureControllers.firstOrNull()
        val supportsA11ySilent = a11yController?.isFallbackCaptureAvailable() == true
        binding.tvAccessibilityShortcutHint.isVisible = supportsA11ySilent
        val isA11yActive = accessibilityControl.isServiceActive()
        binding.btnOpenAccessibilitySettings.isVisible = supportsA11ySilent && !isA11yActive
        binding.btnDisableAccessibilityService.isVisible = supportsA11ySilent && isA11yActive
        if (a11yController != null) {
            binding.btnOpenAccessibilitySettings.setOnClickListener {
                try {
                    overlayPermissionLauncher.launch(a11yController.permissionSettingsIntent(requireContext()))
                } catch (e: android.content.ActivityNotFoundException) {
                    Timber.w(e, "Accessibility settings intent unresolved; showing fallback dialog")
                    showAccessibilityFallbackDialog(a11yController)
                }
            }
        }
        binding.btnDisableAccessibilityService.setOnClickListener {
            if (accessibilityControl.disableSelf()) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.accessibility_service_status_disabled),
                    Snackbar.LENGTH_SHORT
                ).show()
                binding.btnDisableAccessibilityService.isVisible = false
                binding.btnOpenAccessibilitySettings.isVisible = supportsA11ySilent
            }
        }
        binding.btnOpenDevSettings.setOnClickListener {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (e: android.content.ActivityNotFoundException) {
                Timber.w(e, "Dev options intent unresolved")
            }
        }
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
        // S0880: default-player toggle listeners moved into DefaultAppsDialogFragment.
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
        // S0842: icon-only "select resource" button; tooltip backports the label (S0810 pattern).
        TooltipCompat.setTooltipText(
            binding.btnSelectLinkAutodownloadResource,
            binding.btnSelectLinkAutodownloadResource.contentDescription,
        )
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
            MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive
            )
                .setTitle(R.string.reset_operations_section_title)
                .setMessage(R.string.reset_operations_section_message)
                .setPositiveButton(R.string.reset) { _, _ ->
                    viewModel.resetOperationsSection()
                    Snackbar.make(binding.root, R.string.reset_operations_section_success, Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .showBoundTo(this@OperationsSettingsFragment)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect { settings ->
                        withSettingsUpdate {
                            // S2797: every label below is re-rendered here, so a lookup left over
                            // from the previous pass can only write a stale name.
                            destinationLabelResolver.cancelPending()
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
                            binding.rowEnableFileDoOperations
                                .setCheckedSilently(settings.enableFileDoOperations)
                            binding.layoutConfirmDelete.visibility =
                                if (settings.enableSafeMode) View.VISIBLE else View.GONE
                            binding.layoutConfirmMove.visibility =
                                if (settings.enableSafeMode) View.VISIBLE else View.GONE

                            if (binding.etMaxRecipients.text.toString() != settings.maxRecipients.toString()) {
                                binding.etMaxRecipients.setText(
                                    getString(R.string.number_format, settings.maxRecipients)
                                )
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
                            val hasOcrAndTranslation =
                                capabilityAvailability.isTranslationAvailable(requireContext()) &&
                                    DeviceCapabilities.isOcrSupported(requireContext())
                            if (hasOcrAndTranslation) {
                                if (binding.rowCameraOcrTranslationEnabled.isChecked != settings.cameraOcrTranslationEnabled) {
                                    binding.rowCameraOcrTranslationEnabled.setCheckedSilently(
                                        settings.cameraOcrTranslationEnabled
                                    )
                                }
                                if (binding.rowCameraOcrOnly.isChecked != settings.cameraOcrOnly) {
                                    binding.rowCameraOcrOnly.setCheckedSilently(settings.cameraOcrOnly)
                                }
                                binding.layoutCameraOcrOnly.isVisible = settings.cameraOcrTranslationEnabled
                            }
                            captureManager.render(settings)
                            // S2516: every sub-program switch is rendered by its own manager.
                            programsManager.render(settings)
                            // S1883: the whole Wear OS group - its checkbox, its button and its gates -
                            // belongs to its own manager now, so nothing about it is rendered inline here.
                            wearGroupManager.render(settings)

                            // SystemApps group (moved from Player tab).
                            if (binding.rowFollowSystemRotation.isChecked != settings.programFollowSystemRotation) {
                                binding.rowFollowSystemRotation.setCheckedSilently(settings.programFollowSystemRotation)
                            }
                            // S0880: default-player toggle state is rendered inside DefaultAppsDialogFragment.
                            if (binding.rowLinkAutodownloadEnabled.isChecked != settings.linkAutoDownloadEnabled) {
                                binding.rowLinkAutodownloadEnabled.setCheckedSilently(settings.linkAutoDownloadEnabled)
                            }
                            if (binding.rowLinkAutodownloadOpenInPlayer.isChecked != settings.linkAutoDownloadOpenInPlayer) {
                                binding.rowLinkAutodownloadOpenInPlayer.setCheckedSilently(
                                    settings.linkAutoDownloadOpenInPlayer
                                )
                            }
                            // Disable child controls when master link-download toggle is off.
                            binding.rowLinkAutodownloadOpenInPlayer.isEnabled = settings.linkAutoDownloadEnabled
                            binding.btnSelectLinkAutodownloadResource.isEnabled = settings.linkAutoDownloadEnabled
                            binding.tvLinkAutodownloadResource.isEnabled = settings.linkAutoDownloadEnabled
                            refreshDestinationLabel(
                                resourceId = settings.linkAutoDownloadResourceId?.toString(),
                                fallbackRes = R.string.link_autodownload_resource_not_set,
                            ) { binding.tvLinkAutodownloadResource.text = it }

                            sendCommandsManager.updateAvailability(settings)
                            gesturesManager.render(settings)
                        }

                        updateCopyOptionsVisibility(settings.enableCopying)
                        updateMoveOptionsVisibility(settings.enableMoving)
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
        if (_binding != null) {
            sendCommandsManager.onConfigurationChanged(newConfig)
        }
    }

    private fun applyFlavorRestrictions() {
        val hasOcrAndTranslation = capabilityAvailability.isTranslationAvailable(requireContext()) &&
            DeviceCapabilities.isOcrSupported(requireContext())
        binding.rowCameraOcrTranslationEnabled.isVisible = hasOcrAndTranslation
        binding.layoutCameraOcrOnly.isVisible = hasOcrAndTranslation
        if (!hasOcrAndTranslation) {
            binding.rowCameraOcrTranslationEnabled.setCheckedSilently(false)
            binding.rowCameraOcrOnly.setCheckedSilently(false)
            val current = viewModel.settings.value
            if (current.cameraOcrTranslationEnabled || current.cameraOcrOnly) {
                viewModel.updateSettings(
                    current.copy(
                        cameraOcrTranslationEnabled = false,
                        cameraOcrOnly = false
                    )
                )
            }
        }

        // S0880: the toggle UI moved into DefaultAppsDialogFragment; the launcher carries the same gate.
        val supportsDefaultPlayer = mediaCapabilities.supportsDefaultPlayer
        binding.btnOpenDefaultAppsDialog.isVisible = supportsDefaultPlayer
        if (!supportsDefaultPlayer) {
            // S0602: keep default-player state cleared on flavors that cannot register as default. The
            // dialog is unreachable here (launcher hidden), so this reset must stay in the fragment.
            val current = viewModel.settings.value
            if (current.isPrimaryMediaPlayer || current.acceptSharedFiles) {
                viewModel.updateSettings(
                    current.copy(
                        isPrimaryMediaPlayer = false,
                        acceptSharedFiles = false
                    )
                )
            }
        }
    }

    /**
     * S1051: educational gate shown when the direct accessibility-settings intent is unavailable
     * (relocated from EdgeGestureConfigManager). Sideloaded (noLegal) builds cannot flip the toggle
     * directly - the exact tap sequence is spelled out, with a shortcut to the fallback capture method.
     */
    private fun showAccessibilityFallbackDialog(controller: ScreenGestureOverlayController) {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.screenshot_gesture_permission_dialog_title)
            .setMessage(controller.permissionRationaleResId())
            .setPositiveButton(R.string.screenshot_gesture_open_settings) { _, _ ->
                overlayPermissionLauncher.launch(controller.permissionSettingsIntent(requireContext()))
            }
            .setNegativeButton(R.string.cancel, null)
        if (controller.isFallbackCaptureAvailable()) {
            builder.setNeutralButton(R.string.screenshot_gesture_use_old_method) { _, _ ->
                overlayPermissionLauncher.launch(controller.fallbackPermissionSettingsIntent(requireContext()))
            }
        }
        builder.showBoundTo(this@OperationsSettingsFragment)
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
                loader = {
                    listOf(LocalFolderDestinationPickerManager.sentinelItem(requireContext())) +
                        destinationsManager.currentDestinations
                },
                formatter = object : ListSelectionAdapter.ItemFormatter<MediaResource> {
                    override fun getDisplayName(item: MediaResource): String = item.name
                },
                hasSelection = currentResourceId != null,
                isSelected = { it.id == currentResourceId },
                allowClear = true,
                emptyMessageRes = R.string.no_resources_available,
                errorMessageRes = R.string.no_resources_available,
                onSelected = localFolderDestinationPickerManager.wrapOnSelected(currentResourceId, onPicked),
            ),
        ).show()
    }

    /**
     * Resolves a destination resource label and writes it via [setLabel], falling back to [fallbackRes]
     * when unset/missing. S0567: takes a setter lambda so both plain TextViews (capture selectors) and
     * SettingsSelectionRow targets (link-autodownload, screenshot destination) share one resolver.
     */
    private fun refreshDestinationLabel(resourceId: String?, fallbackRes: Int, setLabel: (CharSequence) -> Unit) =
        destinationLabelResolver.render(resourceId?.toLongOrNull(), getString(fallbackRes), setLabel = setLabel)
}
