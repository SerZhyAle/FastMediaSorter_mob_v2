package com.sza.fastmediasorter.ui.settings.helpers

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.databinding.ItemDestinationBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.dialog.ColorPickerDialog
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle

/**
 * Owns the destinations list embedded in the Operations tab: the RecyclerView + adapter, add/remove/
 * reorder/color actions, the adaptive grid/single-column layout, and the destinations StateFlow
 * observation. [currentDestinations] exposes the latest list so the capture/gesture destination
 * pickers can target real folders.
 */
class OperationsDestinationsManager(
    private val binding: FragmentSettingsDestinationsBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
) {

    private lateinit var adapter: DestinationsAdapter

    var currentDestinations: List<MediaResource> = emptyList()
        private set

    fun setup() {
        adapter = DestinationsAdapter(
            onMoveUp = { position -> moveDestination(position, -1) },
            onMoveDown = { position -> moveDestination(position, 1) },
            onDelete = { position -> deleteDestination(position) },
            onColorClick = { resource -> showColorPicker(resource) }
        )
        setupDestinationsLayoutManager()
        binding.rvDestinations.adapter = adapter

        binding.btnAddDestination.setOnClickListener { showAddDestinationDialog() }

        binding.iconHelpDestinations.setOnClickListener {
            TooltipDialog.show(
                fragment.requireContext(),
                R.string.tooltip_destinations_title,
                R.string.tooltip_destinations_message
            )
        }

        registerColorPickerResultListener()
    }

    /**
     * Registered from [setup] - which the host runs in `onViewCreated` - so a settings screen recreated
     * behind the open picker re-registers before the restored dialog is resumed. The picker is opened
     * for every destination row, so the row id rides in the dialog arguments and comes back in the
     * result bundle: one listener serves them all.
     */
    private fun registerColorPickerResultListener() {
        fragment.parentFragmentManager.setFragmentResultListener(
            ColorPickerDialog.RESULT_KEY,
            fragment.viewLifecycleOwner
        ) { _, bundle ->
            val subjectId = bundle.getString(ColorPickerDialog.RESULT_SUBJECT_ID).orEmpty()
            val color = bundle.getInt(ColorPickerDialog.RESULT_COLOR)
            // Resolved from the StateFlow rather than currentDestinations: after a recreation the
            // result can arrive before the collector has replayed its first value.
            val resource = viewModel.destinations.value.firstOrNull { it.id.toString() == subjectId }
            if (resource != null) {
                viewModel.updateDestinationColor(resource, color)
            }
        }
    }

    fun observe() {
        fragment.collectOnLifecycle(viewModel.destinations) { destinations ->
            adapter.submitList(destinations)
            currentDestinations = destinations
            updateAddDestinationVisibility(destinations.isNotEmpty())
            // "Add destinations first" hint - only when no destinations exist.
            binding.tvNoScheduledOps.isVisible = destinations.isEmpty()
        }
    }

    fun onConfigurationChanged() {
        setupDestinationsLayoutManager()
    }

    private fun setupDestinationsLayoutManager() {
        val spanCount = fragment.resources.getInteger(R.integer.destinations_column_count)
        binding.rvDestinations.layoutManager = GridLayoutManager(fragment.requireContext(), spanCount)
    }

    private fun showAddDestinationDialog() {
        ListSelectionDialog<MediaResource>(
            fragment.requireContext(),
            ListSelectionConfig(
                title = fragment.getString(R.string.select_destination_title),
                lifecycleOwner = fragment.viewLifecycleOwner,
                loader = { viewModel.getWritableNonDestinationResources() },
                formatter = object : ListSelectionAdapter.ItemFormatter<MediaResource> {
                    override fun getDisplayName(item: MediaResource): String =
                        "${item.name} (${item.path})"
                },
                hasSelection = false,
                isSelected = { false },
                allowClear = false,
                emptyMessageRes = R.string.no_writable_resources_destinations,
                errorMessageRes = R.string.no_writable_resources_destinations,
                onSelected = { selected ->
                    selected?.let { res ->
                        viewModel.addDestination(res)
                        Toast.makeText(
                            fragment.requireContext(),
                            fragment.getString(R.string.destination_added, res.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
            ),
        ).show()
    }

    private fun showColorPicker(resource: MediaResource) {
        ColorPickerDialog.newInstance(
            initialColor = resource.destinationColor,
            subjectId = resource.id.toString()
        ).show(fragment.parentFragmentManager, ColorPickerDialog.TAG)
    }

    private suspend fun updateAddDestinationVisibility(hasDestinations: Boolean) {
        val availableResources = viewModel.getWritableNonDestinationResources()
        val hasResources = availableResources.isNotEmpty()

        binding.btnAddDestination.isVisible = hasResources
        // Show message only if no destinations AND no available resources.
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

        MaterialAlertDialogBuilder(fragment.requireContext(), R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.remove_destination_title)
            .setMessage(fragment.getString(R.string.remove_destination_message, resource.name))
            .setPositiveButton(R.string.remove_action) { _, _ ->
                viewModel.removeDestination(resource)
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.destination_removed, resource.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundTo(fragment)
    }

    class DestinationsAdapter(
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
                // Display order for user: order+1 (0-based to 1-based).
                binding.tvDestinationNumber.text = if (order >= 0) (order + 1).toString() else ""
                binding.tvDestinationName.text = resource.name
                binding.tvDestinationPath.text = resource.path

                binding.viewColorIndicator.setBackgroundColor(resource.destinationColor)
                binding.viewColorIndicator.setOnClickListener {
                    onColorClick(resource)
                }

                binding.root.setOnLongClickListener {
                    onColorClick(resource)
                    true
                }

                binding.btnMoveUp.isEnabled = position > 0
                binding.btnMoveUp.setOnClickListener { onMoveUp(position) }

                binding.btnMoveDown.isEnabled = position < itemCount - 1
                binding.btnMoveDown.setOnClickListener { onMoveDown(position) }

                binding.btnDelete.setOnClickListener { onDelete(position) }
            }
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
