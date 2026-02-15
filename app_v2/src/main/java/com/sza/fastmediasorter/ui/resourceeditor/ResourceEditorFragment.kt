package com.sza.fastmediasorter.ui.resourceeditor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentResourceEditorBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceEditorMode
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.strategy.ResourceFieldSchema
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ResourceEditorFragment : Fragment() {

    private var _binding: FragmentResourceEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResourceFormViewModel by viewModels()

    private var mode: ResourceEditorMode = ResourceEditorMode.CREATE
    private var resourceId: Long? = null
    private var resourceType: ResourceType? = null
    private val shownWarnings = mutableSetOf<ResourceEditorWarning>()
    private var credentialsDialogShown = false

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

        setupToolbar()
        setupFieldListeners()
        setupButtons()
        observeUiState()
        observeEvents()

        viewModel.initialize(mode, resourceType ?: ResourceType.LOCAL, resourceId)
    }

    private fun setupToolbar() {
        val title = when (mode) {
            ResourceEditorMode.CREATE -> getString(R.string.title_add_resource)
            ResourceEditorMode.EDIT -> getString(R.string.title_edit_resource)
            ResourceEditorMode.COPY -> getString(R.string.title_copy_resource)
        }
        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
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

        // Media types checkboxes
        binding.cbVideo.setOnCheckedChangeListener { _, _ ->
            updateMediaTypes()
        }
        binding.cbAudio.setOnCheckedChangeListener { _, _ ->
            updateMediaTypes()
        }
        binding.cbImage.setOnCheckedChangeListener { _, _ ->
            updateMediaTypes()
        }
        binding.cbDocument.setOnCheckedChangeListener { _, _ ->
            updateMediaTypes()
        }
    }

    private fun updateMediaTypes() {
        val types = mutableSetOf<MediaType>()
        if (binding.cbVideo.isChecked) types.add(MediaType.VIDEO)
        if (binding.cbAudio.isChecked) types.add(MediaType.AUDIO)
        if (binding.cbImage.isChecked) types.add(MediaType.IMAGE)
        if (binding.cbDocument.isChecked) {
            types.add(MediaType.TEXT)
            types.add(MediaType.PDF)
            types.add(MediaType.EPUB)
        }
        viewModel.onFieldChanged(ResourceFieldKey.MEDIA_TYPES, types)
    }

    private fun setupButtons() {
        binding.btnTestConnection.setOnClickListener {
            viewModel.onTestConnection()
        }

        binding.btnSave.setOnClickListener {
            viewModel.onSave()
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
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
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
                }
            }
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

        binding.cbVideo.isChecked = formData.supportedMediaTypes.contains(MediaType.VIDEO)
        binding.cbAudio.isChecked = formData.supportedMediaTypes.contains(MediaType.AUDIO)
        binding.cbImage.isChecked = formData.supportedMediaTypes.contains(MediaType.IMAGE)
        binding.cbDocument.isChecked = formData.supportedMediaTypes.any {
            it == MediaType.TEXT || it == MediaType.PDF || it == MediaType.EPUB
        }

        binding.tilDomain.isVisible = false
        binding.tilShareName.isVisible = false
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
            ResourceFieldKey.CLOUD_PROVIDER -> null
            ResourceFieldKey.CLOUD_FOLDER -> binding.tilCloudFolderId
            ResourceFieldKey.MEDIA_TYPES -> null
            else -> null
        }
    }

    private fun getErrorMessage(errorCode: ResourceErrorCode): String {
        return when (errorCode) {
            ResourceErrorCode.EMPTY -> getString(R.string.error_field_required)
            ResourceErrorCode.INVALID -> getString(R.string.error_invalid_path)
            ResourceErrorCode.TOO_SHORT -> getString(R.string.error_invalid_username)
            ResourceErrorCode.TOO_LONG -> getString(R.string.error_invalid_username)
            ResourceErrorCode.OUT_OF_RANGE -> getString(R.string.error_invalid_port)
            ResourceErrorCode.UNSUPPORTED -> getString(R.string.error_unknown)
            ResourceErrorCode.UNREACHABLE -> getString(R.string.error_connection_failed)
            ResourceErrorCode.ACCESS_DENIED -> getString(R.string.error_connection_failed)
            ResourceErrorCode.TIMEOUT -> getString(R.string.error_connection_failed)
            ResourceErrorCode.CONFLICT -> getString(R.string.error_save_failed)
            ResourceErrorCode.UNKNOWN -> getString(R.string.error_unknown)
        }
    }

    private fun renderConnectionResult(result: ResourceConnectionTestResult?) {
        result ?: return

        binding.groupConnectionResult.isVisible = true
        binding.tvConnectionStatus.text = when (result.status) {
            ResourceConnectionStatus.SUCCESS -> getString(R.string.connection_success)
            ResourceConnectionStatus.FAILED -> getString(R.string.connection_test_failed_detail, result.diagnosticMessage ?: "Unknown error")
            ResourceConnectionStatus.PARTIAL -> getString(R.string.connection_success)
            ResourceConnectionStatus.NOT_SUPPORTED -> getString(R.string.connection_test_not_supported)
        }

        val statusColor = when (result.status) {
            ResourceConnectionStatus.SUCCESS -> requireContext().getColor(R.color.success_green)
            ResourceConnectionStatus.FAILED -> requireContext().getColor(R.color.error_red)
            ResourceConnectionStatus.PARTIAL -> requireContext().getColor(R.color.warning_color)
            ResourceConnectionStatus.NOT_SUPPORTED -> requireContext().getColor(R.color.text_color_secondary)
        }
        binding.tvConnectionStatus.setTextColor(statusColor)
        binding.btnRetry.isVisible = result.status == ResourceConnectionStatus.FAILED
    }

    private fun renderLoadingStates(isTestingConnection: Boolean, isSaving: Boolean) {
        binding.progressConnection.isVisible = isTestingConnection
        binding.btnTestConnection.isEnabled = !isTestingConnection && !isSaving
        binding.btnSave.isEnabled = !isTestingConnection && !isSaving
        binding.progressSave.isVisible = isSaving
    }

    private fun renderSaveButton(state: ResourceEditorUiState) {
        val buttonText = when (mode) {
            ResourceEditorMode.CREATE -> getString(R.string.btn_add_resource)
            ResourceEditorMode.EDIT -> getString(R.string.btn_save_changes)
            ResourceEditorMode.COPY -> getString(R.string.btn_save_copy)
        }
        binding.btnSave.text = buttonText
        binding.btnSave.isEnabled = state.canSave
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        is ResourceEditorUiEvent.ShowError -> {
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                        }
                        is ResourceEditorUiEvent.ShowInfo -> {
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
                        }
                        is ResourceEditorUiEvent.Saved -> {
                            Snackbar.make(binding.root, getString(R.string.resource_saved), Snackbar.LENGTH_SHORT).show()
                            requireActivity().setResult(android.app.Activity.RESULT_OK)
                            requireActivity().finish()
                        }
                    }
                }
            }
        }
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
