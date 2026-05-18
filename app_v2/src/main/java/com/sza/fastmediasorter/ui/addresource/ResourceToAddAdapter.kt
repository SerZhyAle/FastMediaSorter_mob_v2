package com.sza.fastmediasorter.ui.addresource

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemResourceToAddBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType


class ResourceToAddAdapter(
    private val onSelectionChanged: (MediaResource, Boolean) -> Unit,
    private val onNameChanged: (MediaResource, String) -> Unit,
    private val onDestinationChanged: (MediaResource, Boolean) -> Unit,
    private val onScanSubdirectoriesChanged: (MediaResource, Boolean) -> Unit,
    private val onReadOnlyChanged: (MediaResource, Boolean) -> Unit,
    private val onMediaTypeToggled: (MediaResource, MediaType) -> Unit,
    private val onAllFilesChanged: ((MediaResource, Boolean) -> Unit)? = null
) : ListAdapter<MediaResource, ResourceToAddAdapter.ViewHolder>(ResourceDiffCallback()) {

    private var selectedPaths: Set<String> = emptySet()

    fun setSelectedPaths(paths: Set<String>) {
        val oldSelected = selectedPaths
        selectedPaths = paths
        
        // Only notify changed items
        currentList.forEachIndexed { index, resource ->
            if (resource.path in oldSelected || resource.path in paths) {
                notifyItemChanged(index)
            }
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

        init {
            binding.etName.addTextChangedListener(nameWatcher)
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
                
                tvPath.text = resource.path
                tvFileCount.text = when {
                    resource.fileCount >= 1000 -> itemView.context.getString(R.string.file_count_over_1000)
                    else -> itemView.context.getString(R.string.file_count_format, resource.fileCount)
                }
                
                // --- Media Type Toggles ---
                val isDownloads = resource.path == android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
                
                // Use standard color (white/primary) for all active types instead of rainbow colors
                val activeColor = android.graphics.Color.WHITE
                
                setupMediaTypeButton(btnTypeImage, resource, MediaType.IMAGE, activeColor, isDownloads)
                setupMediaTypeButton(btnTypeVideo, resource, MediaType.VIDEO, activeColor, isDownloads)
                setupMediaTypeButton(btnTypeAudio, resource, MediaType.AUDIO, activeColor, isDownloads)
                setupMediaTypeButton(btnTypeGif, resource, MediaType.GIF, activeColor, isDownloads)
                setupMediaTypeButton(btnTypeText, resource, MediaType.TEXT, activeColor, isDownloads)
                setupMediaTypeButton(btnTypePdf, resource, MediaType.PDF, activeColor, isDownloads)

                // Disable destination checkbox if read-only or not writable
                val canBeDestination = resource.isWritable && !resource.isReadOnly
                cbDestination.isEnabled = canBeDestination
                cbDestination.isVisible = true // Always visible to maintain layout
                if (!canBeDestination) {
                    cbDestination.isChecked = false
                }
                cbDestination.setOnCheckedChangeListener(null)
                cbDestination.isChecked = resource.isDestination
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

        
        private fun setupMediaTypeButton(
            view: android.widget.TextView, 
            resource: MediaResource, 
            type: MediaType, 
            activeColor: Int, 
            isLocked: Boolean
        ) {
            val isActive = type in resource.supportedMediaTypes
            
            view.setTextColor(if (isActive) activeColor else android.graphics.Color.LTGRAY)
            view.alpha = if (isActive) 1.0f else 0.5f
            
            if (isLocked) {
                view.isClickable = false
                view.isEnabled = false // Visual indication of disabled
                // Keep visually active if it's there, but non-interactive
                view.alpha = if (isActive) 0.7f else 0.3f
            } else {
                view.isClickable = true
                view.isEnabled = true
                view.setOnClickListener {
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
