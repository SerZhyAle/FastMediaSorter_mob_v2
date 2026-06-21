package com.sza.fastmediasorter.ui.resourceeditor

import androidx.activity.result.contract.ActivityResultContracts
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.utils.collectOnLifecycle
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.databinding.FragmentResourceEditorBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceEditorMode
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.strategy.ResourceFieldSchema
import com.sza.fastmediasorter.utils.PermissionChecker
import com.sza.fastmediasorter.widget.ResourceLaunchWidgetProvider
import com.sza.fastmediasorter.widget.registry.HomeWidgetPinner
import com.sza.fastmediasorter.ui.icon.ResourceIconDefaults
import com.sza.fastmediasorter.ui.icon.ResourceIconRegistry
import com.sza.fastmediasorter.ui.icon.picker.IconPickerBottomSheet
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.ui.common.installTextInputTapFocusBridge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class ResourceEditorFragment : Fragment() {

    private var _binding: FragmentResourceEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResourceFormViewModel by viewModels()

    @Inject lateinit var homeWidgetPinner: HomeWidgetPinner

    private var mode: ResourceEditorMode = ResourceEditorMode.CREATE
    private var resourceId: Long? = null
    private var resourceType: ResourceType? = null
    private val shownWarnings = mutableSetOf<ResourceEditorWarning>()
    private var credentialsDialogShown = false
    private var pendingSaveAfterPermissionGrant = false
    private var hasMediaTypesBySchema = false
    private var toolbarBaseTitle: String = ""
    // S0535: section state goes through the unified orchestrator + consolidated store.
    private val sectionsManager by lazy { CollapsibleSectionsManager(requireContext()) }
    private lateinit var outcomeRenderer: ResourceEditorOutcomeRenderer
    private val mediaPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted || PermissionChecker.hasMediaPermissions(requireContext())) {
            if (pendingSaveAfterPermissionGrant) {
                pendingSaveAfterPermissionGrant = false
                viewModel.onSave()
            }
        } else {
            pendingSaveAfterPermissionGrant = false
            Snackbar.make(binding.root, R.string.permissions_denied_warning, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mode = ResourceEditorMode.valueOf(it.getString(ARG_MODE, ResourceEditorMode.CREATE.name))
            resourceId = it.getLong(ARG_RESOURCE_ID, -1L).takeIf { id -> id != -1L }
            resourceType = it.getString(ARG_RESOURCE_TYPE)?.let { type -> ResourceType.valueOf(type) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResourceEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        outcomeRenderer = ResourceEditorOutcomeRenderer(
            context = requireContext(),
            binding = binding,
            getMode = { mode },
            getCurrentResourceType = { viewModel.uiState.value.formData.type }
        )

        reorderScanningAboveMediaTypes()
        applyOfficeDocumentVisibility()
        setupToolbar()
        setupFieldListeners()
        setupFieldTapBridges()
        setupButtons()
        setupIconPicker()
        setupCollapsibleSections()
        observeUiState()
        observeEvents()

        viewModel.initialize(mode, resourceType ?: ResourceType.LOCAL, resourceId)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatistics()
    }

    private fun setupToolbar() {
        toolbarBaseTitle = when (mode) {
            ResourceEditorMode.CREATE -> getString(R.string.title_add_resource)
            ResourceEditorMode.EDIT -> getString(R.string.title_edit_resource)
            ResourceEditorMode.COPY -> getString(R.string.title_copy_resource)
        }
        binding.toolbar.subtitle = null
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        updateToolbarTypeSubtitle(resourceType ?: ResourceType.LOCAL)
    }

    private fun updateToolbarTypeSubtitle(type: ResourceType) {
        val typeLabel = when (type) {
            ResourceType.LOCAL -> getString(R.string.resource_type_local)
            ResourceType.SMB -> getString(R.string.resource_type_smb)
            ResourceType.SFTP -> getString(R.string.resource_type_sftp)
            ResourceType.FTP -> getString(R.string.resource_type_ftp)
            ResourceType.CLOUD -> getString(R.string.resource_type_cloud)
            ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> getString(R.string.resource_type_stream)
        }
        binding.toolbar.title = "$toolbarBaseTitle · $typeLabel"
    }

    private fun setupCollapsibleSections() {
        // Keys drop the type+orientation composition (the consolidated store is orientation-agnostic).
        sectionsManager.register(binding.headerConnectionSettings, binding.contentConnectionSettings, "resource_editor__connection")
        sectionsManager.register(binding.headerMediaTypes, binding.gridMediaTypes, "resource_editor__media_types")
        sectionsManager.register(binding.headerScanning, binding.contentScanning, "resource_editor__scanning")
        sectionsManager.register(binding.headerDestination, binding.contentDestination, "resource_editor__destination")
        sectionsManager.register(binding.headerAdvanced, binding.contentAdvanced, "resource_editor__advanced")
        sectionsManager.register(binding.headerStatistics, binding.groupStatistics, "resource_editor__statistics")
    }

    private fun reorderScanningAboveMediaTypes() {
        val parent = binding.cardMediaTypes.parent as? LinearLayout ?: return

        val scanningCard = binding.cardScanning
        val mediaCard = binding.cardMediaTypes
        val destinationCard = binding.cardDestination

        val destinationHeaderIndex = parent.indexOfChild(destinationCard)
        if (destinationHeaderIndex <= 0) {
            return
        }

        parent.removeView(scanningCard)
        parent.removeView(mediaCard)

        val insertIndex = parent.indexOfChild(destinationCard)
        parent.addView(scanningCard, insertIndex)
        parent.addView(mediaCard, insertIndex + 1)
    }

    private fun setupFieldListeners() {
        binding.etName.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.NAME, text?.toString().orEmpty())
        }

        binding.etPath.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.PATH, text?.toString().orEmpty())
        }

        binding.etHost.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.HOST, text?.toString().orEmpty())
        }

        binding.etPort.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.PORT, text?.toString().orEmpty())
        }

        binding.etUsername.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.USERNAME, text?.toString().orEmpty())
        }

        binding.etPassword.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.PASSWORD, text?.toString().orEmpty())
        }

        binding.etServerPath.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.PATH, text?.toString().orEmpty())
        }

        binding.etCloudFolderId.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.CLOUD_FOLDER, text?.toString().orEmpty())
        }

        binding.etComment.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.COMMENT, text?.toString().orEmpty())
        }

        binding.etAccessPin.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.ACCESS_PIN, text?.toString().orEmpty())
        }

        binding.etSlideshowInterval.addTextChangedListener { text ->
            viewModel.onFieldChanged(ResourceFieldKey.SLIDESHOW_INTERVAL, text?.toString().orEmpty())
        }

        // Scanning & behavior checkboxes
        binding.cbScanSubdirectories.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onFieldChanged(ResourceFieldKey.SCAN_SUBDIRECTORIES, isChecked)
        }

        binding.cbAllFiles.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onFieldChanged(ResourceFieldKey.ALL_FILES, isChecked)
        }

        binding.cbDisableThumbnails.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onFieldChanged(ResourceFieldKey.DISABLE_THUMBNAILS, isChecked)
        }
        binding.cbShowHiddenFiles.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onFieldChanged(ResourceFieldKey.SHOW_HIDDEN_FILES, isChecked)
        }

        binding.cbShowSubfoldersAsItems.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onFieldChanged(ResourceFieldKey.SHOW_SUBFOLDERS_AS_ITEMS, isChecked)
        }

        binding.cbRememberFileList.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onFieldChanged(ResourceFieldKey.REMEMBER_FILE_LIST, isChecked)
        }

        binding.btnHelpRememberFileListEditor.setOnClickListener {
            showRememberFileListHelpDialog()
        }

        // Destination & read-only
        binding.cbIsDestination.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onFieldChanged(ResourceFieldKey.IS_DESTINATION, isChecked)
        }

        binding.cbIsReadOnly.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onFieldChanged(ResourceFieldKey.IS_READ_ONLY, isChecked)
        }

        // Media types checkboxes (8 individual types)
        binding.cbVideo.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbAudio.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbImage.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbGif.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbText.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbPdf.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbEpub.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbOffice.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
    }

    private fun setupFieldTapBridges() {
        val context = requireContext()

        installTextInputTapFocusBridge(context, binding.tilName, binding.etName)
        installTextInputTapFocusBridge(context, binding.tilPath, binding.etPath)
        installTextInputTapFocusBridge(context, binding.tilHost, binding.etHost)
        installTextInputTapFocusBridge(context, binding.tilPort, binding.etPort)
        installTextInputTapFocusBridge(context, binding.tilUsername, binding.etUsername)
        installTextInputTapFocusBridge(context, binding.tilPassword, binding.etPassword)
        installTextInputTapFocusBridge(context, binding.tilServerPath, binding.etServerPath)
        installTextInputTapFocusBridge(context, binding.tilCloudFolderId, binding.etCloudFolderId)
        installTextInputTapFocusBridge(context, binding.tilComment, binding.etComment)
        installTextInputTapFocusBridge(context, binding.tilAccessPin, binding.etAccessPin)
        installTextInputTapFocusBridge(context, binding.tilSlideshowInterval, binding.etSlideshowInterval)
    }

    private fun updateMediaTypes() {
        val types = mutableSetOf<MediaType>()
        if (binding.cbVideo.isChecked) types.add(MediaType.VIDEO)
        if (binding.cbAudio.isChecked) types.add(MediaType.AUDIO)
        if (binding.cbImage.isChecked) types.add(MediaType.IMAGE)
        if (binding.cbGif.isChecked) types.add(MediaType.GIF)
        if (binding.cbText.isChecked) types.add(MediaType.TEXT)
        if (binding.cbPdf.isChecked) types.add(MediaType.PDF)
        if (binding.cbEpub.isChecked) types.add(MediaType.EPUB)
        if (binding.cbOffice.isVisible && binding.cbOffice.isChecked) types.add(MediaType.OFFICE_DOCUMENT)
        viewModel.onFieldChanged(ResourceFieldKey.MEDIA_TYPES, types)
    }

    fun performSave() {
        if (shouldCheckMediaPermissionBeforeSave()) showPermissionRequiredDialog()
        else viewModel.onSave()
    }

    private fun setupButtons() {
        binding.btnTestConnection.setOnClickListener {
            viewModel.onTestConnection()
        }

        binding.btnSave.setOnClickListener {
            if (shouldCheckMediaPermissionBeforeSave()) {
                showPermissionRequiredDialog()
            } else {
                viewModel.onSave()
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.onRetry()
        }

        binding.btnResetChanges.setOnClickListener {
            viewModel.onResetChanges()
        }

        binding.btnSaveAsCopy.setOnClickListener {
            viewModel.onSaveAsCopy()
        }

        binding.btnUseFirstSuggestion.setOnClickListener {
            val suggestion = viewModel.uiState.value.nameSuggestions.firstOrNull() ?: return@setOnClickListener
            viewModel.onUseNameSuggestion(suggestion)
        }

        binding.btnProfileSelector.setOnClickListener {
            showProfileSelectorDialog()
        }

        binding.btnAddToHomeScreen.setOnClickListener {
            pinWidgetForCurrentResource()
        }
    }

    /** Wires the icon-picker button: opens [IconPickerBottomSheet] and handles its result (S0034 Phase 06). */
    private fun setupIconPicker() {
        // Show the pick-icon button; it is hidden by default in the include layout
        binding.pickIconAction.btnPickIcon.visibility = View.VISIBLE

        binding.pickIconAction.btnPickIcon.setOnClickListener {
            val state = viewModel.uiState.value.formData
            val currentIconId = state.iconId
            val defaultSet = ResourceIconDefaults.setForResource(state.profile, state.type)
            IconPickerBottomSheet.newInstance(currentIconId, defaultSet)
                .show(childFragmentManager, "icon_picker")
        }

        // Listen for the icon the user picked; update ViewModel + preview button
        childFragmentManager.setFragmentResultListener(
            IconPickerBottomSheet.KEY, viewLifecycleOwner
        ) { _, bundle ->
            val pickedId = bundle.getString(IconPickerBottomSheet.RESULT_ICON_ID) ?: return@setFragmentResultListener
            viewModel.onIconPicked(pickedId)
            updateIconPickerPreview(pickedId)
        }
    }

    private fun updateIconPickerPreview(iconId: String?) {
        val drawableRes = ResourceIconRegistry.resolveDrawable(iconId)
        if (drawableRes != null) {
            binding.pickIconAction.btnPickIcon.setImageResource(drawableRes)
        }
    }

    private fun shouldCheckMediaPermissionBeforeSave(): Boolean {
        val state = viewModel.uiState.value
        val isCreateMode = mode == ResourceEditorMode.CREATE
        val isLocalResource = state.formData.type == ResourceType.LOCAL
        return isCreateMode && isLocalResource && !PermissionChecker.hasMediaPermissions(requireContext())
    }

    private fun showPermissionRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.permissions_required_title)
            .setMessage(R.string.permissions_required_for_local_resource)
            .setPositiveButton(R.string.grant_permissions) { _, _ ->
                pendingSaveAfterPermissionGrant = true
                requestMediaPermissions()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestMediaPermissions() {
        val permissions = PermissionChecker.getRequiredMediaPermissions()
        if (permissions.isEmpty() || PermissionChecker.hasMediaPermissions(requireContext())) {
            if (pendingSaveAfterPermissionGrant) {
                pendingSaveAfterPermissionGrant = false
                viewModel.onSave()
            }
            return
        }
        mediaPermissionsLauncher.launch(permissions)
    }

    private fun showProfileSelectorDialog() {
        val profiles = ResourceProfile.values()
        val labels = profiles.map { getString(getProfileLabelResId(it)) }.toTypedArray()
        val currentIndex = profiles.indexOf(viewModel.uiState.value.formData.profile).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_select_profile)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                viewModel.onProfileSelected(profiles[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pinWidgetForCurrentResource() {
        val ctx = requireContext()
        val currentResourceId = resourceId ?: return

        if (!homeWidgetPinner.isSupported()) {
            Toast.makeText(ctx, R.string.widget_pin_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        if (homeWidgetPinner.isKeyguardLocked()) {
            MaterialAlertDialogBuilder(ctx)
                .setMessage(R.string.widget_unlock_screen_prompt)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val manager = AppWidgetManager.getInstance(ctx)
        val provider = ComponentName(ctx, ResourceLaunchWidgetProvider::class.java)
        val existingIds = manager.getAppWidgetIds(provider)
        val prefs = ctx.getSharedPreferences(ResourceLaunchWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyExists = existingIds.any { id ->
            prefs.getLong("resource_id_$id", -1L) == currentResourceId
        }
        if (alreadyExists) {
            Toast.makeText(ctx, R.string.widget_already_added, Toast.LENGTH_SHORT).show()
            return
        }

        val formData = viewModel.uiState.value.formData
        prefs.edit()
            .putLong(ResourceLaunchWidgetProvider.KEY_PENDING_RESOURCE_ID, currentResourceId)
            .putString(ResourceLaunchWidgetProvider.KEY_PENDING_RESOURCE_NAME, formData.name)
            .putString(ResourceLaunchWidgetProvider.KEY_PENDING_RESOURCE_PATH, formData.path)
            .putString(ResourceLaunchWidgetProvider.KEY_PENDING_RESOURCE_TYPE, formData.type.name)
            .apply()

        val successCallback = PendingIntent.getBroadcast(
            ctx, 0,
            android.content.Intent(ResourceLaunchWidgetProvider.ACTION_WIDGET_PINNED).apply {
                setPackage(ctx.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        homeWidgetPinner.requestPin(provider, successCallback)
    }

    private fun getProfileLabelResId(profile: ResourceProfile): Int = when (profile) {
        ResourceProfile.NONE -> R.string.label_profile_none
        ResourceProfile.AUDIO_LIBRARY -> R.string.label_profile_audio_library
        ResourceProfile.VIDEO_LIBRARY -> R.string.label_profile_video_library
        ResourceProfile.PHOTO_STORAGE -> R.string.label_profile_photo_storage
        ResourceProfile.DOCUMENTS -> R.string.label_profile_documents
        ResourceProfile.ALL_FILES -> R.string.label_profile_all_files
    }

    private fun observeUiState() {
        collectOnLifecycle(viewModel.uiState) { state ->
            renderFieldSchema(state.fieldSchema, state.formData.type)
            renderFormData(state.formData)
            renderFieldStates(state.fieldStates)
            renderConnectionResult(state.connectionResult)
            renderLoadingStates(state.isTestingConnection, state.isSaving)
            renderSaveButton(state)
            renderEditActions(state)
            renderContextWarnings(state)
            renderNameCollision(state)
            renderCredentialChoice(state)
            renderStatistics(state.statistics)
        }
    }

    private fun renderNameCollision(state: ResourceEditorUiState) {
        if (!state.hasNameCollision) {
            binding.tvNameCollision.isVisible = false
            binding.tvNameSuggestions.isVisible = false
            binding.btnUseFirstSuggestion.isVisible = false
            return
        }

        binding.tvNameCollision.isVisible = true
        binding.tvNameCollision.text = getString(R.string.error_resource_name_exists)

        val suggestions = state.nameSuggestions
        if (suggestions.isNotEmpty()) {
            binding.tvNameSuggestions.isVisible = true
            binding.tvNameSuggestions.text = getString(
                R.string.name_suggestions,
                suggestions.joinToString(", ")
            )
            binding.btnUseFirstSuggestion.isVisible = true
            binding.btnUseFirstSuggestion.text = getString(R.string.btn_use_name_format, suggestions.first())
        } else {
            binding.tvNameSuggestions.isVisible = false
            binding.btnUseFirstSuggestion.isVisible = false
        }
    }

    private fun renderCredentialChoice(state: ResourceEditorUiState) {
        if (!state.requiresCredentialChoice || credentialsDialogShown) return
        credentialsDialogShown = true

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.copy_credentials_title)
            .setMessage(R.string.copy_credentials_message)
            .setPositiveButton(R.string.keep_credentials) { _, _ ->
                viewModel.onCredentialBehaviorSelected(keepCredentials = true)
            }
            .setNegativeButton(R.string.use_new_credentials) { _, _ ->
                viewModel.onCredentialBehaviorSelected(keepCredentials = false)
            }
            .setCancelable(false)
            .show()
    }

    private fun renderEditActions(state: ResourceEditorUiState) {
        binding.btnResetChanges.isVisible = state.hasChanges
        binding.btnResetChanges.isEnabled = !state.isSaving && !state.isTestingConnection

        binding.btnSaveAsCopy.isVisible = state.showSaveAsCopy
        binding.btnSaveAsCopy.isEnabled = state.isFormValid && !state.isSaving && !state.isTestingConnection
    }

    private fun renderContextWarnings(state: ResourceEditorUiState) {
        if (state.warnings.isEmpty()) {
            binding.tvContextWarning.isVisible = false
            return
        }

        val warningText = when {
            state.warnings.contains(ResourceEditorWarning.PATH_DUPLICATE_EXISTING) -> {
                getString(R.string.warning_path_duplicate_existing)
            }
            state.warnings.contains(ResourceEditorWarning.ENDPOINT_CHANGED_RESCAN) -> {
                getString(R.string.warning_endpoint_changed_rescan)
            }
            state.warnings.contains(ResourceEditorWarning.READ_ONLY_DESTINATION) -> {
                getString(R.string.warning_read_only_destination)
            }
            else -> ""
        }

        if (warningText.isNotBlank()) {
            binding.tvContextWarning.isVisible = true
            binding.tvContextWarning.text = warningText
        }

        val newWarnings = state.warnings - shownWarnings
        newWarnings.forEach { warning ->
            showWarningDialog(warning)
        }
        shownWarnings.addAll(newWarnings)
    }

    private fun showWarningDialog(warning: ResourceEditorWarning) {
        val message = when (warning) {
            ResourceEditorWarning.READ_ONLY_DESTINATION -> getString(R.string.warning_read_only_destination)
            ResourceEditorWarning.ENDPOINT_CHANGED_RESCAN -> getString(R.string.warning_endpoint_changed_rescan)
            ResourceEditorWarning.PATH_DUPLICATE_EXISTING -> getString(R.string.warning_path_duplicate_existing)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.warning_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun renderFormData(formData: ResourceFormData) {
        // Always keep toolbar subtitle in sync with type
        updateToolbarTypeSubtitle(formData.type)

        if (binding.etName.text.toString() != formData.name) {
            binding.etName.setText(formData.name)
        }
        if (binding.etPath.text.toString() != formData.path) {
            binding.etPath.setText(formData.path)
        }
        if (binding.etHost.text.toString() != formData.host) {
            binding.etHost.setText(formData.host)
        }
        if (binding.etPort.text.toString() != formData.port?.toString().orEmpty()) {
            binding.etPort.setText(formData.port?.toString().orEmpty())
        }
        if (binding.etUsername.text.toString() != formData.username) {
            binding.etUsername.setText(formData.username)
        }
        if (binding.etPassword.text.toString() != formData.password) {
            binding.etPassword.setText(formData.password)
        }
        if (binding.etServerPath.text.toString() != formData.path) {
            binding.etServerPath.setText(formData.path)
        }
        if (binding.etCloudFolderId.text.toString() != formData.cloudFolderId.orEmpty()) {
            binding.etCloudFolderId.setText(formData.cloudFolderId.orEmpty())
        }
        if (binding.etComment.text.toString() != formData.comment) {
            binding.etComment.setText(formData.comment)
        }
        if (binding.etAccessPin.text.toString() != formData.accessPin) {
            binding.etAccessPin.setText(formData.accessPin)
        }
        if (binding.etSlideshowInterval.text.toString() != formData.slideshowInterval.toString()) {
            binding.etSlideshowInterval.setText(formData.slideshowInterval.toString())
        }

        // Media type checkboxes (8 individual types)
        // Guard: remove listeners before programmatic setChecked to avoid spurious onFieldChanged calls.
        binding.cbVideo.setOnCheckedChangeListener(null)
        binding.cbAudio.setOnCheckedChangeListener(null)
        binding.cbImage.setOnCheckedChangeListener(null)
        binding.cbGif.setOnCheckedChangeListener(null)
        binding.cbText.setOnCheckedChangeListener(null)
        binding.cbPdf.setOnCheckedChangeListener(null)
        binding.cbEpub.setOnCheckedChangeListener(null)
        binding.cbOffice.setOnCheckedChangeListener(null)
        binding.cbVideo.isChecked = formData.supportedMediaTypes.contains(MediaType.VIDEO)
        binding.cbAudio.isChecked = formData.supportedMediaTypes.contains(MediaType.AUDIO)
        binding.cbImage.isChecked = formData.supportedMediaTypes.contains(MediaType.IMAGE)
        binding.cbGif.isChecked = formData.supportedMediaTypes.contains(MediaType.GIF)
        binding.cbText.isChecked = formData.supportedMediaTypes.contains(MediaType.TEXT)
        binding.cbPdf.isChecked = formData.supportedMediaTypes.contains(MediaType.PDF)
        binding.cbEpub.isChecked = formData.supportedMediaTypes.contains(MediaType.EPUB)
        binding.cbOffice.isChecked = supportsOfficeDocuments() &&
            formData.supportedMediaTypes.contains(MediaType.OFFICE_DOCUMENT)
        binding.cbVideo.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbAudio.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbImage.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbGif.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbText.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbPdf.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbEpub.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbOffice.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }

        // Profile selector button label
        binding.btnProfileSelector.setText(getProfileLabelResId(formData.profile))

        // Update icon picker preview to match the current iconId (S0034 Phase 06)
        updateIconPickerPreview(formData.iconId)

        // Scanning checkboxes (guarded from triggering listeners on programmatic updates)
        binding.cbScanSubdirectories.setOnCheckedChangeListener(null)
        binding.cbAllFiles.setOnCheckedChangeListener(null)
        binding.cbDisableThumbnails.setOnCheckedChangeListener(null)
        binding.cbShowHiddenFiles.setOnCheckedChangeListener(null)
        binding.cbShowSubfoldersAsItems.setOnCheckedChangeListener(null)
        binding.cbRememberFileList.setOnCheckedChangeListener(null)
        binding.cbScanSubdirectories.isChecked = formData.scanSubdirectories
        binding.cbAllFiles.isChecked = formData.allFiles
        binding.cbDisableThumbnails.isChecked = formData.disableThumbnails
        binding.cbShowHiddenFiles.isChecked = formData.showHiddenFiles
        binding.cbShowSubfoldersAsItems.isChecked = formData.showSubfoldersAsItems
        binding.cbRememberFileList.isChecked = formData.rememberFileList
        binding.cbScanSubdirectories.setOnCheckedChangeListener { _, isChecked -> viewModel.onFieldChanged(ResourceFieldKey.SCAN_SUBDIRECTORIES, isChecked) }
        binding.cbAllFiles.setOnCheckedChangeListener { _, isChecked -> viewModel.onFieldChanged(ResourceFieldKey.ALL_FILES, isChecked) }
        binding.cbDisableThumbnails.setOnCheckedChangeListener { _, isChecked -> viewModel.onFieldChanged(ResourceFieldKey.DISABLE_THUMBNAILS, isChecked) }
        binding.cbShowHiddenFiles.setOnCheckedChangeListener { _, isChecked -> viewModel.onFieldChanged(ResourceFieldKey.SHOW_HIDDEN_FILES, isChecked) }
        binding.cbShowSubfoldersAsItems.setOnCheckedChangeListener { _, isChecked -> viewModel.onFieldChanged(ResourceFieldKey.SHOW_SUBFOLDERS_AS_ITEMS, isChecked) }
        binding.cbRememberFileList.setOnCheckedChangeListener { _, isChecked -> viewModel.onFieldChanged(ResourceFieldKey.REMEMBER_FILE_LIST, isChecked) }

        // Destination checkboxes (guarded: programmatic setChecked must NOT fire onFieldChanged)
        binding.cbIsDestination.setOnCheckedChangeListener(null)
        binding.cbIsReadOnly.setOnCheckedChangeListener(null)
        binding.cbIsDestination.isChecked = formData.isDestination
        binding.cbIsReadOnly.isChecked = formData.isReadOnly
        binding.cbIsDestination.setOnCheckedChangeListener { _, isChecked -> viewModel.onFieldChanged(ResourceFieldKey.IS_DESTINATION, isChecked) }
        binding.cbIsReadOnly.setOnCheckedChangeListener { _, isChecked -> viewModel.onFieldChanged(ResourceFieldKey.IS_READ_ONLY, isChecked) }

        // Dependent visibility: showHiddenFiles depends on allFiles, showSubfoldersAsItems depends on scanSubdirectories
        binding.cbShowHiddenFiles.isEnabled = formData.allFiles
        binding.cbShowSubfoldersAsItems.isEnabled = formData.scanSubdirectories

        // Media type checkboxes disabled when allFiles is on
        val mediaEnabled = !formData.allFiles
        binding.cbVideo.isEnabled = mediaEnabled
        binding.cbAudio.isEnabled = mediaEnabled
        binding.cbImage.isEnabled = mediaEnabled
        binding.cbGif.isEnabled = mediaEnabled
        binding.cbText.isEnabled = mediaEnabled
        binding.cbPdf.isEnabled = mediaEnabled
        binding.cbEpub.isEnabled = mediaEnabled
        binding.cbOffice.isEnabled = mediaEnabled

        updateMediaTypesSectionVisibility(formData.allFiles)

        binding.tilDomain.isVisible = false
        binding.tilShareName.isVisible = false
    }

    private fun updateMediaTypesSectionVisibility(allFilesEnabled: Boolean) {
        val shouldShowMediaTypes = hasMediaTypesBySchema && !allFilesEnabled
        binding.cardMediaTypes.isVisible = shouldShowMediaTypes
        binding.headerMediaTypes.isVisible = shouldShowMediaTypes

        if (!shouldShowMediaTypes) {
            binding.gridMediaTypes.isVisible = false
            return
        }

        // Card just became visible again - re-sync its body to the section's restored expanded state.
        binding.gridMediaTypes.isVisible = binding.headerMediaTypes.isExpanded()
    }

    private fun applyOfficeDocumentVisibility() {
        binding.cbOffice.isVisible = supportsOfficeDocuments()
    }

    private fun supportsOfficeDocuments(): Boolean =
        MediaTypeUtils.OFFICE_DOCUMENT_EXTENSIONS.isNotEmpty()

    private fun updateConnectionSectionVisibility(
        visibleKeys: Set<ResourceFieldKey>,
        type: ResourceType
    ) {
        val isLocal = type == ResourceType.LOCAL
        val isNetwork = type == ResourceType.SMB || type == ResourceType.SFTP || type == ResourceType.FTP
        val isCloud = type == ResourceType.CLOUD

        val localFieldsVisible = isLocal && visibleKeys.contains(ResourceFieldKey.PATH)
        val networkFieldsVisible = isNetwork && (
            visibleKeys.contains(ResourceFieldKey.HOST) ||
                visibleKeys.contains(ResourceFieldKey.PORT) ||
                visibleKeys.contains(ResourceFieldKey.USERNAME) ||
                visibleKeys.contains(ResourceFieldKey.PASSWORD) ||
                visibleKeys.contains(ResourceFieldKey.PATH)
            )
        val cloudFieldsVisible = isCloud && visibleKeys.contains(ResourceFieldKey.CLOUD_FOLDER)

        val shouldShowConnectionSection = localFieldsVisible ||
            networkFieldsVisible ||
            cloudFieldsVisible ||
            isNetwork ||
            isCloud

        binding.cardConnectionSettings.isVisible = shouldShowConnectionSection
        binding.headerConnectionSettings.isVisible = shouldShowConnectionSection

        if (!shouldShowConnectionSection) {
            binding.contentConnectionSettings.isVisible = false
            return
        }

        // Card just became visible again - re-sync its body to the section's restored expanded state.
        binding.contentConnectionSettings.isVisible = binding.headerConnectionSettings.isExpanded()
    }

    private fun renderFieldSchema(schema: List<ResourceFieldSchema>, type: ResourceType) {
        val visibleKeys = schema.filter { it.visible }.map { it.key }.toSet()

        val isLocal = type == ResourceType.LOCAL
        val isNetwork = type == ResourceType.SMB || type == ResourceType.SFTP || type == ResourceType.FTP
        val isCloud = type == ResourceType.CLOUD

        binding.groupLocal.isVisible = isLocal
        binding.groupNetwork.isVisible = isNetwork
        binding.groupCloud.isVisible = isCloud

        binding.tilName.isVisible = visibleKeys.contains(ResourceFieldKey.NAME)
        binding.tilPath.isVisible = isLocal && visibleKeys.contains(ResourceFieldKey.PATH)
        binding.tilServerPath.isVisible = isNetwork && visibleKeys.contains(ResourceFieldKey.PATH)

        binding.tilHost.isVisible = isNetwork && visibleKeys.contains(ResourceFieldKey.HOST)
        binding.tilPort.isVisible = isNetwork && visibleKeys.contains(ResourceFieldKey.PORT)
        binding.tilUsername.isVisible = isNetwork && visibleKeys.contains(ResourceFieldKey.USERNAME)
        binding.tilPassword.isVisible = isNetwork && visibleKeys.contains(ResourceFieldKey.PASSWORD)

        binding.tilCloudFolderId.isVisible = isCloud && visibleKeys.contains(ResourceFieldKey.CLOUD_FOLDER)
        binding.btnTestConnection.isVisible = isNetwork || isCloud

        updateConnectionSectionVisibility(visibleKeys, type)

        // Media types section (collapsible header + content)
        hasMediaTypesBySchema = visibleKeys.contains(ResourceFieldKey.MEDIA_TYPES)
        updateMediaTypesSectionVisibility(viewModel.uiState.value.formData.allFiles)

        // Scanning section (collapsible header + content)
        val hasScanSettings = visibleKeys.contains(ResourceFieldKey.SCAN_SUBDIRECTORIES) ||
            visibleKeys.contains(ResourceFieldKey.ALL_FILES) ||
            visibleKeys.contains(ResourceFieldKey.DISABLE_THUMBNAILS) ||
            visibleKeys.contains(ResourceFieldKey.REMEMBER_FILE_LIST)
        binding.cardScanning.isVisible = hasScanSettings
        binding.headerScanning.isVisible = hasScanSettings
        binding.cbScanSubdirectories.isVisible = visibleKeys.contains(ResourceFieldKey.SCAN_SUBDIRECTORIES)
        binding.cbAllFiles.isVisible = visibleKeys.contains(ResourceFieldKey.ALL_FILES)
        binding.cbDisableThumbnails.isVisible = visibleKeys.contains(ResourceFieldKey.DISABLE_THUMBNAILS)
        binding.cbShowHiddenFiles.isVisible = visibleKeys.contains(ResourceFieldKey.SHOW_HIDDEN_FILES)
        binding.cbShowSubfoldersAsItems.isVisible = visibleKeys.contains(ResourceFieldKey.SHOW_SUBFOLDERS_AS_ITEMS)
        binding.cbRememberFileList.isVisible = hasScanSettings
        binding.btnHelpRememberFileListEditor.isVisible = hasScanSettings

        // Destination section (collapsible header + content)
        val hasDestination = visibleKeys.contains(ResourceFieldKey.IS_DESTINATION) ||
            visibleKeys.contains(ResourceFieldKey.IS_READ_ONLY)
        binding.cardDestination.isVisible = hasDestination
        binding.headerDestination.isVisible = hasDestination
        binding.cbIsDestination.isVisible = visibleKeys.contains(ResourceFieldKey.IS_DESTINATION)
        binding.cbIsReadOnly.isVisible = visibleKeys.contains(ResourceFieldKey.IS_READ_ONLY)

        // Advanced section (collapsible header + content)
        binding.cardAdvanced.isVisible = true
        binding.headerAdvanced.isVisible = true
        binding.tilComment.isVisible = visibleKeys.contains(ResourceFieldKey.COMMENT)
        binding.tilAccessPin.isVisible = visibleKeys.contains(ResourceFieldKey.ACCESS_PIN)
        binding.tilSlideshowInterval.isVisible = visibleKeys.contains(ResourceFieldKey.SLIDESHOW_INTERVAL)

        // Hide path, media types, scanning, connection and destination sections for virtual resources
        val currentPath = viewModel.uiState.value.formData.path
        if (com.sza.fastmediasorter.util.VirtualPathUtils.isVirtualPath(currentPath)) {
            binding.tilPath.isVisible = false
            binding.tilServerPath.isVisible = false
            binding.cardConnectionSettings.isVisible = false
            binding.headerConnectionSettings.isVisible = false
            binding.cardScanning.isVisible = false
            binding.headerScanning.isVisible = false
            hasMediaTypesBySchema = false
            updateMediaTypesSectionVisibility(false)
            binding.cardDestination.isVisible = false
            binding.headerDestination.isVisible = false
        }
    }

    private fun renderFieldStates(fieldStates: Map<ResourceFieldKey, ResourceFieldState>) {
        fieldStates.forEach { (key, state) ->
            val inputLayout = getInputLayoutForField(key)
            inputLayout?.error = state.errorCode?.let { getErrorMessage(it) }
        }
    }

    private fun getInputLayoutForField(key: ResourceFieldKey): TextInputLayout? {
        return when (key) {
            ResourceFieldKey.NAME -> binding.tilName
            ResourceFieldKey.PATH -> if (binding.tilPath.isVisible) binding.tilPath else binding.tilServerPath
            ResourceFieldKey.HOST -> binding.tilHost
            ResourceFieldKey.PORT -> binding.tilPort
            ResourceFieldKey.USERNAME -> binding.tilUsername
            ResourceFieldKey.PASSWORD -> binding.tilPassword
            ResourceFieldKey.ACCESS_PIN -> binding.tilAccessPin
            ResourceFieldKey.COMMENT -> binding.tilComment
            ResourceFieldKey.CLOUD_PROVIDER -> null
            ResourceFieldKey.CLOUD_FOLDER -> binding.tilCloudFolderId
            ResourceFieldKey.MEDIA_TYPES -> null
            ResourceFieldKey.SLIDESHOW_INTERVAL -> binding.tilSlideshowInterval
            ResourceFieldKey.DESTINATION_COLOR -> null
            ResourceFieldKey.IS_DESTINATION -> null
            ResourceFieldKey.IS_READ_ONLY -> null
            ResourceFieldKey.SCAN_SUBDIRECTORIES -> null
            ResourceFieldKey.ALL_FILES -> null
            ResourceFieldKey.DISABLE_THUMBNAILS -> null
            ResourceFieldKey.SHOW_HIDDEN_FILES -> null
            ResourceFieldKey.SHOW_SUBFOLDERS_AS_ITEMS -> null
            ResourceFieldKey.REMEMBER_FILE_LIST -> null
            ResourceFieldKey.SHOW_COMMAND_PANEL -> null
            else -> null
        }
    }

    private fun showRememberFileListHelpDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remember_file_list_help_title)
            .setMessage(R.string.remember_file_list_help_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun getErrorMessage(errorCode: ResourceErrorCode): String =
        outcomeRenderer.getErrorMessage(errorCode)

    private fun renderConnectionResult(result: ResourceConnectionTestResult?) =
        outcomeRenderer.renderConnectionResult(result)

    private fun renderLoadingStates(isTestingConnection: Boolean, isSaving: Boolean) =
        outcomeRenderer.renderLoadingStates(isTestingConnection, isSaving)

    private fun renderSaveButton(state: ResourceEditorUiState) =
        outcomeRenderer.renderSaveButton(state.canSave)

    private fun observeEvents() {
        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is ResourceEditorUiEvent.ShowError -> {
                    val message = event.messageResId?.let { getString(it) }
                        ?: event.message
                        ?: getString(R.string.error_unknown)
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
                is ResourceEditorUiEvent.ShowInfo -> {
                    val message = event.messageResId?.let { getString(it) }
                        ?: event.message
                        ?: getString(R.string.error_unknown)
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                }
                is ResourceEditorUiEvent.Saved -> {
                    Snackbar.make(binding.root, getString(R.string.resource_saved), Snackbar.LENGTH_SHORT).show()
                    requireActivity().setResult(android.app.Activity.RESULT_OK)
                    requireActivity().finish()
                }
            }
        }
    }

    private fun renderStatistics(statistics: ResourceStatistics?) {
        outcomeRenderer.renderStatistics(statistics)
        if (!binding.cardStatistics.isVisible || !binding.headerStatistics.isVisible) {
            return
        }

        // OutcomeRenderer controls whether statistics exist in EDIT mode; the header still owns
        // the expanded state so persistence is not lost on every UI-state emission.
        binding.groupStatistics.isVisible = binding.headerStatistics.isExpanded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_MODE = "mode"
        private const val ARG_RESOURCE_ID = "resource_id"
        private const val ARG_RESOURCE_TYPE = "resource_type"

        fun newInstance(
            mode: ResourceEditorMode,
            resourceType: ResourceType? = null,
            resourceId: Long? = null
        ): ResourceEditorFragment {
            return ResourceEditorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, mode.name)
                    resourceId?.let { putLong(ARG_RESOURCE_ID, it) }
                    resourceType?.let { putString(ARG_RESOURCE_TYPE, it.name) }
                }
            }
        }
    }
}
