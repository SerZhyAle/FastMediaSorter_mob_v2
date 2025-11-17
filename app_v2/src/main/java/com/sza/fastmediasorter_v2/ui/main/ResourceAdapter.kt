package com.sza.fastmediasorter_v2.ui.main

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.databinding.ItemResourceBinding
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType

class ResourceAdapter(
    private val onItemClick: (MediaResource) -> Unit,
    private val onItemLongClick: (MediaResource) -> Unit,
    private val onEditClick: (MediaResource) -> Unit,
    private val onCopyFromClick: (MediaResource) -> Unit,
    private val onDeleteClick: (MediaResource) -> Unit,
    private val onMoveUpClick: (MediaResource) -> Unit,
    private val onMoveDownClick: (MediaResource) -> Unit
) : ListAdapter<MediaResource, ResourceAdapter.ResourceViewHolder>(ResourceDiffCallback()) {

    private var selectedResourceId: Long? = null

    fun setSelectedResource(resourceId: Long?) {
        val previousId = selectedResourceId
        selectedResourceId = resourceId
        
        currentList.forEachIndexed { index, resource ->
            if (resource.id == previousId || resource.id == resourceId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResourceViewHolder {
        val binding = ItemResourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ResourceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResourceViewHolder, position: Int) {
        holder.bind(getItem(position), selectedResourceId)
    }

    inner class ResourceViewHolder(
        private val binding: ItemResourceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(resource: MediaResource, selectedId: Long?) {
            binding.apply {
                tvResourceName.text = resource.name
                tvResourcePath.text = resource.path
                tvResourceType.text = resource.type.name
                
                // Format file count with ">10000" for large numbers
                tvFileCount.text = when {
                    resource.fileCount > 10000 -> ">10000 files"
                    else -> "${resource.fileCount} files"
                }
                
                val mediaTypesText = buildString {
                    if (MediaType.IMAGE in resource.supportedMediaTypes) append("I")
                    if (MediaType.VIDEO in resource.supportedMediaTypes) append("V")
                    if (MediaType.AUDIO in resource.supportedMediaTypes) append("A")
                    if (MediaType.GIF in resource.supportedMediaTypes) append("G")
                }
                tvMediaTypes.text = mediaTypesText
                
                tvDestinationMark.text = if (resource.isDestination) "→" else ""
                
                tvWritableIndicator.visibility = if (resource.isWritable) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
                
                // Update availability indicator - show N/A text and set background color
                tvAvailabilityIndicator.visibility = if (resource.isAvailable) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
                
                // Set background tint for unavailable resources
                if (!resource.isAvailable) {
                    val bgColor = ContextCompat.getColor(
                        rootLayout.context,
                        R.color.unavailable_resource_bg
                    )
                    rootLayout.backgroundTintList = ColorStateList.valueOf(bgColor)
                } else {
                    rootLayout.backgroundTintList = null
                }
                
                // Show last sync time for network resources (SMB, SFTP, FTP)
                val isNetworkResource = resource.type == ResourceType.SMB || 
                                        resource.type == ResourceType.SFTP || 
                                        resource.type == ResourceType.FTP
                
                if (isNetworkResource && resource.lastSyncDate != null) {
                    val syncTimeAgo = DateUtils.getRelativeTimeSpanString(
                        resource.lastSyncDate,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    )
                    tvLastSync.text = root.context.getString(R.string.last_sync_time, syncTimeAgo)
                    tvLastSync.visibility = android.view.View.VISIBLE
                } else if (isNetworkResource) {
                    tvLastSync.text = root.context.getString(R.string.never_synced)
                    tvLastSync.visibility = android.view.View.VISIBLE
                } else {
                    tvLastSync.visibility = android.view.View.GONE
                }
                
                root.isSelected = resource.id == selectedId
                
                // Simple click and long click - no gesture detection needed
                root.setOnClickListener {
                    onItemClick(resource)
                }
                
                root.setOnLongClickListener {
                    onItemLongClick(resource)
                    true
                }
                
                btnEdit.setOnClickListener {
                    onEditClick(resource)
                }
                
                btnCopyFrom.setOnClickListener {
                    onCopyFromClick(resource)
                }
                
                btnDelete.setOnClickListener {
                    onDeleteClick(resource)
                }
                
                btnMoveUp.setOnClickListener {
                    onMoveUpClick(resource)
                }
                
                btnMoveDown.setOnClickListener {
                    onMoveDownClick(resource)
                }
            }
        }
    }

    private class ResourceDiffCallback : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem == newItem
        }
    }
}
