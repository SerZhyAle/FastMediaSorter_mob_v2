package com.sza.fastmediasorter.ui.main

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemResourceBinding
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import com.sza.fastmediasorter.utils.setOnLongClickListenerDebounced
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import timber.log.Timber

class ResourceAdapter(
    private val onItemClick: (MediaResource) -> Unit,
    private val onItemLongClick: (MediaResource) -> Unit,
    private val onEditClick: (MediaResource) -> Unit,
    private val onCopyFromClick: (MediaResource) -> Unit,
    private val onDeleteClick: (MediaResource) -> Unit,
    private val onMoveUpClick: (MediaResource) -> Unit,
    private val onMoveDownClick: (MediaResource) -> Unit
) : ListAdapter<MediaResource, RecyclerView.ViewHolder>(ResourceDiffCallback()) {

    companion object {
        const val VIEW_TYPE_LIST = 0
        const val VIEW_TYPE_GRID = 1
        
        /**
         * Formats supported media types as IVAGTPE string
         */
        fun formatMediaTypes(types: Set<MediaType>): String = buildString {
            if (MediaType.IMAGE in types) append("I")
            if (MediaType.VIDEO in types) append("V")
            if (MediaType.AUDIO in types) append("A")
            if (MediaType.GIF in types) append("G")
            if (MediaType.TEXT in types) append("T")
            if (MediaType.PDF in types) append("P")
            if (MediaType.EPUB in types) append("E")
        }
    }

    private var isGridMode: Boolean = false

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

    fun setViewMode(isGrid: Boolean) {
        if (this.isGridMode != isGrid) {
            this.isGridMode = isGrid
            notifyDataSetChanged() // Full refresh needed for view type change
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = com.sza.fastmediasorter.databinding.ItemResourceGridBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            GridViewHolder(binding)
        } else {
            val binding = ItemResourceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ResourceViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val resource = getItem(position)
        if (holder is GridViewHolder) {
            holder.bind(resource, selectedResourceId)
        } else if (holder is ResourceViewHolder) {
            holder.bind(resource, selectedResourceId)
        }
    }

    inner class GridViewHolder(
        private val binding: com.sza.fastmediasorter.databinding.ItemResourceGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(resource: MediaResource, selectedId: Long?) {
            binding.apply {
                tvResourceName.text = resource.name
                
                // Dynamic font size based on name length
                val textSize = when {
                    resource.name.length <= 10 -> binding.root.context.resources.getDimension(R.dimen.text_size_huge)
                    resource.name.length <= 16 -> binding.root.context.resources.getDimension(R.dimen.text_size_large)
                    else -> binding.root.context.resources.getDimension(R.dimen.text_size_normal)
                }
                tvResourceName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize)
                
                // Set icon based on resource type
                val iconRes = if (resource.id == -100L) { // Favorites
                    R.drawable.ic_resource_favorites
                } else {
                    when (resource.type) {
                        ResourceType.LOCAL -> R.drawable.ic_resource_local
                        ResourceType.SMB -> R.drawable.ic_resource_smb
                        ResourceType.SFTP -> R.drawable.ic_resource_sftp
                        ResourceType.FTP -> R.drawable.ic_resource_ftp
                        ResourceType.CLOUD -> {
                            // Use provider-specific icon for cloud resources
                            when (resource.cloudProvider?.name) {
                                "GOOGLE_DRIVE" -> R.drawable.ic_provider_google_drive
                                "ONEDRIVE" -> R.drawable.ic_provider_onedrive
                                "DROPBOX" -> R.drawable.ic_provider_dropbox
                                else -> R.drawable.ic_resource_cloud
                            }
                        }
                    }
                }
                ivResourceTypeIcon.setImageResource(iconRes)
                
                // Destination border (quick sort)
                if (resource.isDestination) {
                    // Show colored border
                    viewDestinationBorder.visibility = android.view.View.VISIBLE
                    val borderDrawable = ContextCompat.getDrawable(
                        binding.root.context,
                        R.drawable.destination_border
                    )?.mutate() as? android.graphics.drawable.GradientDrawable
                    borderDrawable?.setStroke(
                        binding.root.context.resources.getDimensionPixelSize(R.dimen.destination_border_width),
                        resource.destinationColor
                    )
                    viewDestinationBorder.background = borderDrawable
                } else {
                    viewDestinationBorder.visibility = android.view.View.GONE
                }
                
                // Background logic
                if (!resource.isAvailable) {
                    val bgColor = ContextCompat.getColor(root.context, R.color.unavailable_resource_bg)
                    root.setBackgroundColor(bgColor)
                } else {
                    // Default background / Selection state
                   val bgColor = if (resource.id == selectedId) {
                        ContextCompat.getColor(root.context, android.R.color.holo_blue_light)
                    } else if (resource.id == -100L) {
                         ContextCompat.getColor(root.context, R.color.resource_item_bg_odd)
                    } else {
                        // Zebra striping for grid
                        if (bindingAdapterPosition % 2 == 0) {
                            ContextCompat.getColor(root.context, R.color.resource_item_bg_even)
                        } else {
                            ContextCompat.getColor(root.context, R.color.resource_item_bg_odd)
                        }
                    }
                    root.setBackgroundColor(bgColor)
                }
                
                // Writable/Lock indicator
                tvWritableIndicator.visibility = if (!resource.isDestination && !resource.isWritable && resource.id != -100L) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }

                // Media Types Indicator (IVAGTPE)
                tvMediaTypes.text = if (resource.id == -100L) "" else formatMediaTypes(resource.supportedMediaTypes)

                // Interaction with debounce protection
                root.isSelected = resource.id == selectedId
                root.setOnClickListenerDebounced { onItemClick(resource) }
                root.setOnLongClickListenerDebounced { 
                    if (resource.id != -100L) {
                        onItemLongClick(resource)
                        true
                    } else {
                        false
                    }
                }
                
                // Mouse right-click support (triggers long click action)
                root.setOnGenericMotionListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_BUTTON_PRESS &&
                        event.buttonState == android.view.MotionEvent.BUTTON_SECONDARY) {
                        if (resource.id != -100L) {
                            onItemLongClick(resource)
                        }
                        true
                    } else {
                        false
                    }
                }
                
                // Focus support for keyboard navigation
                root.isFocusable = true
                root.isFocusableInTouchMode = false
            }
        }
    }

    inner class ResourceViewHolder(
        private val binding: ItemResourceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(resource: MediaResource, selectedId: Long?) {
            binding.apply {
                tvResourceName.text = resource.name
                // For cloud resources, show provider name instead of folder ID
                tvResourcePath.text = if (resource.type == ResourceType.CLOUD && resource.cloudProvider != null) {
                    "${resource.cloudProvider.name} / ${resource.name}"
                } else {
                    resource.path
                }
                
                if (!resource.comment.isNullOrBlank()) {
                    tvResourceComment.text = resource.comment
                    tvResourceComment.visibility = android.view.View.VISIBLE
                } else {
                    tvResourceComment.visibility = android.view.View.GONE
                }

                // Set resource type text using localized string
                tvResourceType.text = when (resource.type) {
                    ResourceType.LOCAL -> root.context.getString(R.string.resource_type_local)
                    ResourceType.SMB -> root.context.getString(R.string.resource_type_smb)
                    ResourceType.SFTP -> root.context.getString(R.string.resource_type_sftp)
                    ResourceType.FTP -> root.context.getString(R.string.resource_type_ftp)
                    ResourceType.CLOUD -> root.context.getString(R.string.resource_type_cloud)
                }
                
                // Set icon based on resource type
                val iconRes = if (resource.id == -100L) { // Favorites
                    R.drawable.ic_resource_favorites
                } else {
                    when (resource.type) {
                        ResourceType.LOCAL -> R.drawable.ic_resource_local
                        ResourceType.SMB -> R.drawable.ic_resource_smb
                        ResourceType.SFTP -> R.drawable.ic_resource_sftp
                        ResourceType.FTP -> R.drawable.ic_resource_ftp
                        ResourceType.CLOUD -> R.drawable.ic_resource_cloud
                    }
                }
                ivResourceTypeIcon.setImageResource(iconRes)
                
                // Format file count with ">1000" for resources with 1000+ files
                // For favorites, we might want to show "N/A" or "All" until we implement counting
                tvFileCount.text = when {
                    resource.id == -100L -> "" // Don't show count for now, or show "Favorites"
                    resource.fileCount >= 1000 -> root.context.getString(R.string.file_count_over_1000)
                    else -> root.context.getString(R.string.file_count_format, resource.fileCount)
                }
                
                tvMediaTypes.text = if (resource.id == -100L) "" else formatMediaTypes(resource.supportedMediaTypes)
                
                tvDestinationMark.text = if (resource.isDestination) "→" else ""
                
                // Destination badge and border
                Timber.d("ResourceAdapter: resource=${resource.name}, isDestination=${resource.isDestination}, destinationOrder=${resource.destinationOrder}, color=${resource.destinationColor}")
                
                if (resource.isDestination) {
                    // Show colored border for all destination resources
                    binding.viewDestinationBorder.visibility = android.view.View.VISIBLE
                    val borderDrawable = ContextCompat.getDrawable(
                        binding.root.context,
                        R.drawable.destination_border
                    )?.mutate() as? android.graphics.drawable.GradientDrawable
                    borderDrawable?.setStroke(
                        binding.root.context.resources.getDimensionPixelSize(R.dimen.destination_border_width),
                        resource.destinationColor
                    )
                    binding.viewDestinationBorder.background = borderDrawable
                    Timber.d("ResourceAdapter: Border set for ${resource.name}, borderDrawable=$borderDrawable")
                    
                    // Show badge only if destinationOrder is set (quick sort)
                    if (resource.destinationOrder != null) {
                        binding.tvDestinationBadge.visibility = android.view.View.VISIBLE
                        binding.tvDestinationBadge.text = (resource.destinationOrder + 1).toString()
                        
                        val badgeDrawable = ContextCompat.getDrawable(
                            binding.root.context,
                            R.drawable.badge_destination_background
                        )?.mutate()
                        badgeDrawable?.setTint(resource.destinationColor)
                        binding.tvDestinationBadge.background = badgeDrawable
                    } else {
                        binding.tvDestinationBadge.visibility = android.view.View.GONE
                    }
                } else {
                    binding.tvDestinationBadge.visibility = android.view.View.GONE
                    binding.viewDestinationBorder.visibility = android.view.View.GONE
                }
                
                // Show lock icon only for non-destination resources without write access
                // Destinations are expected to be writable, so no lock icon needed
                tvWritableIndicator.visibility = if (!resource.isDestination && !resource.isWritable && resource.id != -100L) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
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
                    rootLayout.setBackgroundColor(bgColor)
                } else {
                    // Zebra striping for available resources
                    // Highlight Favorites specifically?
                    val bgColor = if (resource.id == -100L) {
                        ContextCompat.getColor(rootLayout.context, R.color.resource_item_bg_odd) // Or special color
                    } else if (bindingAdapterPosition % 2 == 0) {
                        // Even rows - slightly darker/different
                        ContextCompat.getColor(rootLayout.context, R.color.resource_item_bg_even)
                    } else {
                        // Odd rows - default
                        ContextCompat.getColor(rootLayout.context, R.color.resource_item_bg_odd)
                    }
                    rootLayout.setBackgroundColor(bgColor)
                }
                
                // Show last sync time for network resources (SMB, SFTP, FTP)
                val isNetworkResource = resource.type == ResourceType.SMB || 
                                        resource.type == ResourceType.SFTP || 
                                        resource.type == ResourceType.FTP ||
                                        resource.type == ResourceType.CLOUD
                
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
                
                // Simple click and long click with debounce protection
                root.setOnClickListenerDebounced {
                    onItemClick(resource)
                }
                
                root.setOnLongClickListenerDebounced {
                    if (resource.id != -100L) {
                        onItemLongClick(resource)
                        true
                    } else {
                        false
                    }
                }
                
                // Mouse right-click support (triggers long click action)
                root.setOnGenericMotionListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_BUTTON_PRESS &&
                        event.buttonState == android.view.MotionEvent.BUTTON_SECONDARY) {
                        if (resource.id != -100L) {
                            onItemLongClick(resource)
                        }
                        true
                    } else {
                        false
                    }
                }
                
                // Focus support for keyboard navigation
                root.isFocusable = true
                root.isFocusableInTouchMode = false
                
                // Hide actions for Favorites
                if (resource.id == -100L) {
                    btnMoreActions.visibility = android.view.View.GONE
                    layoutInlineActions.visibility = android.view.View.GONE
                } else {
                    // Check if we should show inline actions (Landscape/Tablet)
                    val showInlineActions = root.resources.getBoolean(R.bool.is_resource_actions_inline)
                    
                    if (showInlineActions) {
                        btnMoreActions.visibility = android.view.View.GONE
                        layoutInlineActions.visibility = android.view.View.VISIBLE
                        
                        // Bind inline button listeners with debounce protection
                        btnEdit.setOnClickListenerDebounced { onEditClick(resource) }
                        btnCopy.setOnClickListenerDebounced { onCopyFromClick(resource) }
                        btnMoveUp.setOnClickListenerDebounced { onMoveUpClick(resource) }
                        btnMoveDown.setOnClickListenerDebounced { onMoveDownClick(resource) }
                        btnDelete.setOnClickListenerDebounced { onDeleteClick(resource) }
                        
                    } else {
                        btnMoreActions.visibility = android.view.View.VISIBLE
                        layoutInlineActions.visibility = android.view.View.GONE
                        
                        btnMoreActions.setOnClickListenerDebounced { view ->
                            val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
                            popup.menuInflater.inflate(R.menu.resource_item_actions, popup.menu)
                            popup.setForceShowIcon(true)
                            
                            popup.setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    R.id.action_edit -> {
                                        onEditClick(resource)
                                        true
                                    }
                                    R.id.action_copy -> {
                                        onCopyFromClick(resource)
                                        true
                                    }
                                    R.id.action_move_up -> {
                                        onMoveUpClick(resource)
                                        true
                                    }
                                    R.id.action_move_down -> {
                                        onMoveDownClick(resource)
                                        true
                                    }
                                    R.id.action_delete -> {
                                        onDeleteClick(resource)
                                        true
                                    }
                                    else -> false
                                }
                            }
                            popup.show()
                        }
                    }
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
