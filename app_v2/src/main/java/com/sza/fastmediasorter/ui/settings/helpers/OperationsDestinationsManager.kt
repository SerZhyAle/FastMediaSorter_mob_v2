package com.sza.fastmediasorter.ui.settings.helpers

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.databinding.ItemDestinationBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.dialog.ColorPickerDialog
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.launch
import timber.log.Timber

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
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val availableResources = viewModel.getWritableNonDestinationResources()

            if (availableResources.isEmpty()) {
                Toast.makeText(
                    fragment.requireContext(),
                    R.string.no_writable_resources_destinations,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val items = availableResources.map { "${it.name} (${it.path})" }.toTypedArray()

            androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.select_destination_title)
                .setItems(items) { _, which ->
                    val selectedResource = availableResources[which]
                    viewModel.addDestination(selectedResource)
                    Toast.makeText(
                        fragment.requireContext(),
                        fragment.getString(R.string.destination_added, selectedResource.name),
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
        ).show(fragment.parentFragmentManager, "ColorPickerDialog")
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

        androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
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
            .show()
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
