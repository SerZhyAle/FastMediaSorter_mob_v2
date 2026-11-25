package com.sza.fastmediasorter.ui.addresource

import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemResourceToAddBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.allowsWriteOperations
import com.sza.fastmediasorter.ui.common.MediaTypeColorCatalog
import com.sza.fastmediasorter.ui.common.recycler.notifyChangedRuns
import com.sza.fastmediasorter.ui.main.ResourceAdapter
import timber.log.Timber

private const val PAYLOAD_SELECTION = "payload_selection"
private const val CHIP_STROKE_ACTIVE_DP = 1.5f
private const val CHIP_STROKE_INACTIVE_DP = 1f
private const val CHIP_ALPHA_ACTIVE = 1.0f
private const val CHIP_ALPHA_LOCKED = 0.6f

class ResourceToAddAdapter(
    private val onSelectionChanged: (MediaResource, Boolean) -> Unit,
    private val onNameChanged: (MediaResource, String) -> Unit,
    private val onDestinationChanged: (MediaResource, Boolean) -> Unit,
    private val onScanSubdirectoriesChanged: (MediaResource, Boolean) -> Unit,
    private val onReadOnlyChanged: (MediaResource, Boolean) -> Unit,
    private val onMediaTypeToggled: (MediaResource, MediaType) -> Unit,
    private val onAllFilesChanged: ((MediaResource, Boolean) -> Unit)? = null,
    private val mediaTypeEntries: List<MediaTypeColorCatalog.BadgeEntry> = MediaTypeColorCatalog.badgeEntries,
) : ListAdapter<MediaResource, ResourceToAddAdapter.ViewHolder>(ResourceDiffCallback()) {

    private var selectedPaths: Set<String> = emptySet()

    fun setSelectedPaths(paths: Set<String>) {
        val oldSelected = selectedPaths
        selectedPaths = paths

        // Only notify rows whose selection membership flipped; payload triggers
        // checkbox-only rebind to avoid disrupting active EditText editing.
        notifyChangedRuns(PAYLOAD_SELECTION) { resource ->
            (resource.path in oldSelected) != (resource.path in paths)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResourceToAddBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SELECTION }) {
            holder.bindSelection(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(
        private val binding: ItemResourceToAddBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentResource: MediaResource? = null
        private val nameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentResource?.let { resource ->
                    val newName = s.toString()
                    if (newName != resource.name) {
                        onNameChanged(resource, newName)
                    }
                }
            }
        }

        private val mediaTypeChips: Map<MediaType, Chip>

        init {
            binding.etName.addTextChangedListener(nameWatcher)
            val context = binding.root.context
            mediaTypeChips = mediaTypeEntries.associate { entry ->
                val chip = Chip(context).apply {
                    isCheckable = true
                    isClickable = true
                    isFocusable = true
                    setEnsureMinTouchTargetSize(true)
                    text = MediaTypeColorCatalog.formatChipLabel(context, entry)
                }
                binding.cgMediaTypes.addView(chip)
                entry.type to chip
            }
        }

        fun bind(resource: MediaResource) {
            currentResource = resource
            
            binding.apply {
                cbAdd.setOnCheckedChangeListener(null)
                cbAdd.isChecked = resource.path in selectedPaths
                cbAdd.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(resource, isChecked)
                }
                
                etName.removeTextChangedListener(nameWatcher)
                // Skip setText when field has focus - user is actively editing,
                // resetting text would jump the cursor to position 0.
                if (!etName.hasFocus() && etName.text.toString() != resource.name) {
                    etName.setText(resource.name)
                }
                etName.addTextChangedListener(nameWatcher)
                
                tvPath.text = ResourceAdapter.displayPath(resource)
                Timber.d("S4847: staging path=${tvPath.text} types=${resource.supportedMediaTypes.size}")
                tvFileCount.text = when {
                    resource.fileCount >= 1000 -> itemView.context.getString(R.string.file_count_over_1000)
                    else -> itemView.resources.getQuantityString(
                        R.plurals.file_count_format_plural,
                        resource.fileCount,
                        resource.fileCount
                    )
                }
                
                // --- Media Type Toggles ---
                val downloadsPath = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    .absolutePath
                val isDownloads = resource.path == downloadsPath

                mediaTypeChips.forEach { (type, chip) ->
                    bindMediaTypeChip(chip, resource, type, isDownloads)
                }

                // S1019: destination eligibility via the shared write-policy resolver (a destination
                // must accept writes) - consistent with the browse/player write gates.
                val canBeDestination = resource.allowsWriteOperations()
                cbDestination.isEnabled = canBeDestination
                cbDestination.isVisible = true // Always visible to maintain layout
                // Detach first: a recycled row still carries the listener bound to the resource it showed before.
                cbDestination.setOnCheckedChangeListener(null)
                cbDestination.isChecked = canBeDestination && resource.isDestination
                cbDestination.setOnCheckedChangeListener { _, isChecked ->
                    onDestinationChanged(resource, isChecked)
                }
                
                cbScanSubdirectories.setOnCheckedChangeListener(null)
                cbScanSubdirectories.isChecked = resource.scanSubdirectories
                cbScanSubdirectories.setOnCheckedChangeListener { _, isChecked ->
                    onScanSubdirectoriesChanged(resource, isChecked)
                }

                // New Read-Only checkbox
                cbReadOnly.setOnCheckedChangeListener(null)
                cbReadOnly.isChecked = resource.isReadOnly
                cbReadOnly.setOnCheckedChangeListener { _, isChecked ->
                    onReadOnlyChanged(resource, isChecked)
                }
                
                // All Files checkbox
                cbAllFiles.setOnCheckedChangeListener(null)
                cbAllFiles.isChecked = resource.allFiles
                cbAllFiles.setOnCheckedChangeListener { _, isChecked ->
                    onAllFilesChanged?.invoke(resource, isChecked)
                }
            }
        }

        fun bindSelection(resource: MediaResource) {
            currentResource = resource

            binding.apply {
                cbAdd.setOnCheckedChangeListener(null)
                cbAdd.isChecked = resource.path in selectedPaths
                cbAdd.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(resource, isChecked)
                }
            }
        }


        private fun bindMediaTypeChip(
            chip: Chip,
            resource: MediaResource,
            type: MediaType,
            isLocked: Boolean,
        ) {
            val isActive = type in resource.supportedMediaTypes
            val density = chip.resources.displayMetrics.density
            chip.setOnCheckedChangeListener(null)
            chip.isChecked = isActive
            chip.isCheckedIconVisible = isActive
            if (isActive) {
                chip.chipBackgroundColor = ColorStateList.valueOf(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSecondaryContainer)
                )
                chip.chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(chip.context, MediaTypeColorCatalog.colorFor(type))
                )
                chip.chipStrokeWidth = CHIP_STROKE_ACTIVE_DP * density
                chip.setTextColor(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
            } else {
                chip.chipBackgroundColor = ColorStateList.valueOf(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurface)
                )
                chip.chipStrokeColor = ColorStateList.valueOf(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOutline)
                )
                chip.chipStrokeWidth = CHIP_STROKE_INACTIVE_DP * density
                chip.setTextColor(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
            }
            chip.isEnabled = !isLocked
            chip.isClickable = !isLocked
            chip.alpha = if (isLocked) CHIP_ALPHA_LOCKED else CHIP_ALPHA_ACTIVE
            if (!isLocked) {
                chip.setOnCheckedChangeListener { _, _ ->
                    onMediaTypeToggled(resource, type)
                }
            }
        }
    }

    private class ResourceDiffCallback : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem == newItem
        }
    }
}
