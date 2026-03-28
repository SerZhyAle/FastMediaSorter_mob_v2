package com.sza.fastmediasorter.ui.settings.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.databinding.ItemDestinationBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.ui.dialog.ColorPickerDialog
import com.sza.fastmediasorter.ui.dialog.ScheduledLogDialog
import com.sza.fastmediasorter.ui.dialog.ScheduledOperationDialog
import com.sza.fastmediasorter.ui.settings.ScheduledOperationsAdapter
import com.sza.fastmediasorter.ui.settings.ScheduledOperationsViewModel
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@android.annotation.SuppressLint("SetTextI18n")
class OperationsSettingsFragment : Fragment() {
    companion object {
        private const val PREFS_NAME = "settings_section_states"
        private const val KEY_FILE_OPS_EXPANDED = "destinations_file_ops_expanded"
        private const val KEY_DESTINATIONS_EXPANDED = "destinations_list_expanded"
        private const val KEY_SCHEDULED_EXPANDED = "scheduled_ops_expanded"
    }

    private var _binding: FragmentSettingsDestinationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    private val scheduledViewModel: ScheduledOperationsViewModel by activityViewModels()
    private lateinit var adapter: DestinationsAdapter
    private lateinit var scheduledAdapter: ScheduledOperationsAdapter
    private var isUpdatingFromSettings = false

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
    }
    
    override fun onResume() {
        super.onResume()
        updateScheduledNotificationPermissionButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupViews() {
        // Copying switches
        binding.switchEnableCopying.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableCopying = isChecked))
            updateCopyOptionsVisibility(isChecked)
        }
        
        binding.switchGoToNextAfterCopy.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(goToNextAfterCopy = isChecked))
        }
        
        binding.switchOverwriteOnCopy.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(overwriteOnCopy = isChecked))
        }
        
        // Moving switches
        binding.switchEnableMoving.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableMoving = isChecked))
            updateMoveOptionsVisibility(isChecked)
        }
        
        binding.switchOverwriteOnMove.setOnCheckedChangeListener { _, isChecked ->
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

        binding.iconHelpGoToNextAfterCopy.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_go_to_next_after_copy_title,
                R.string.tooltip_go_to_next_after_copy_message
            )
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
        
        // Add button
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
                        isUpdatingFromSettings = true
                        binding.switchEnableScheduledOps.isChecked = settings.enableScheduledOperations
                        binding.containerScheduledContent.isVisible = settings.enableScheduledOperations
                        // Update switches
                        binding.switchEnableCopying.isChecked = settings.enableCopying
                        binding.switchGoToNextAfterCopy.isChecked = settings.goToNextAfterCopy
                        binding.switchOverwriteOnCopy.isChecked = settings.overwriteOnCopy
                        binding.switchEnableMoving.isChecked = settings.enableMoving
                        binding.switchOverwriteOnMove.isChecked = settings.overwriteOnMove
                        
                        // Update Max Recipients
                        if (binding.etMaxRecipients.text.toString() != settings.maxRecipients.toString()) {
                            binding.etMaxRecipients.setText(getString(R.string.number_format, settings.maxRecipients))
                        }
                        
                        updateCopyOptionsVisibility(settings.enableCopying)
                        updateMoveOptionsVisibility(settings.enableMoving)
                        isUpdatingFromSettings = false
                    }
                }
                
                launch {
                    viewModel.destinations.collect { destinations ->
                        adapter.submitList(destinations)
                        // Update visibility based on current destinations list
                        updateAddDestinationVisibility(destinations.isNotEmpty())
                    }
                }

                // Keep resources StateFlow warm so viewModel.resources.value is populated
                // when the user opens the scheduled operation dialog.
                launch {
                    viewModel.resources.collect { /* no UI update needed here */ }
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
        bindSectionToggle(
            header = binding.headerFileOperations,
            content = binding.containerFileOperations,
            title = getString(R.string.settings_category_file_operations),
            prefKey = KEY_FILE_OPS_EXPANDED,
            initiallyExpanded = prefs.getBoolean(KEY_FILE_OPS_EXPANDED, false)
        )
        bindSectionToggle(
            header = binding.headerDestinations,
            content = binding.containerDestinations,
            title = getString(R.string.destinations_list_header),
            prefKey = KEY_DESTINATIONS_EXPANDED,
            initiallyExpanded = prefs.getBoolean(KEY_DESTINATIONS_EXPANDED, false)
        )
        if (BuildConfig.ENABLE_SCHEDULED_OPERATIONS) {
            bindSectionToggle(
                header = binding.headerScheduled,
                content = binding.containerScheduled,
                title = getString(R.string.scheduled_ops_section_title),
                prefKey = KEY_SCHEDULED_EXPANDED,
                initiallyExpanded = prefs.getBoolean(KEY_SCHEDULED_EXPANDED, false)
            )
        } else {
            binding.headerScheduled.isVisible = false
            binding.containerScheduled.isVisible = false
        }
    }

    private fun setupScheduledSection() {
        updateScheduledNotificationPermissionButton()

        binding.switchEnableScheduledOps.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableScheduledOperations = isChecked))
            binding.containerScheduledContent.isVisible = isChecked
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
            ScheduledLogDialog(
                requireContext(),
                logContent = scheduledViewModel.getLog(),
                onClearLog = { scheduledViewModel.clearLog() }
            ).show()
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                scheduledViewModel.operations.collect { ops ->
                    scheduledAdapter.submitList(ops)
                    binding.tvNoScheduledOps.isVisible = ops.isEmpty()
                }
            }
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

    private fun bindSectionToggle(
        header: android.widget.TextView,
        content: View,
        title: String,
        prefKey: String,
        initiallyExpanded: Boolean
    ) {
        content.isVisible = initiallyExpanded
        updateHeader(header, title, initiallyExpanded)

        header.setOnClickListener {
            val expanded = !content.isVisible
            content.isVisible = expanded
            updateHeader(header, title, expanded)
            requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(prefKey, expanded)
                .apply()
        }
    }

    private fun updateHeader(header: android.widget.TextView, title: String, expanded: Boolean) {
        val prefix = if (expanded) "▼" else "▶"
        header.text = getString(R.string.string_format_two_args, prefix, title)
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
