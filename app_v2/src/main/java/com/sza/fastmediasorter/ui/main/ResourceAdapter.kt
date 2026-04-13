package com.sza.fastmediasorter.ui.main

import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.ui.common.MediaGroupPalette
import com.sza.fastmediasorter.util.VirtualPathUtils
import timber.log.Timber

@android.annotation.SuppressLint("SetTextI18n")
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

        private val DOCUMENT_TYPES = setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB)
        private val IMAGE_TYPES = setOf(MediaType.IMAGE, MediaType.GIF)

        private data class SingleCategoryIndicator(
            val iconRes: Int,
            val color: Int
        )
        
        /**
         * Formats supported media types as colored IVAGTPE string or "ALL" for allFiles mode
         */
        fun formatMediaTypes(context: android.content.Context, types: Set<MediaType>, allFiles: Boolean): CharSequence {
            // If allFiles flag is set, show "ALL" instead of type letters
            if (allFiles) {
                return "ALL"
            }

            // Single-category shortcuts as icons:
            // - Audio only -> note
            // - Documents only (T/P/E subset) -> book
            // - Video only -> video icon
            // - Images only (I/G subset) -> image icon
            val indicator = when {
                types == setOf(MediaType.AUDIO) -> SingleCategoryIndicator(
                    iconRes = R.drawable.ic_music_note,
                    color = MediaGroupPalette.AUDIO_PRIMARY
                )
                types.isNotEmpty() && types.all { it in DOCUMENT_TYPES } -> SingleCategoryIndicator(
                    iconRes = R.drawable.ic_book,
                    color = MediaGroupPalette.DOCUMENT_PRIMARY
                )
                types == setOf(MediaType.VIDEO) -> SingleCategoryIndicator(
                    iconRes = R.drawable.ic_video,
                    color = MediaGroupPalette.VIDEO_PRIMARY
                )
                types.isNotEmpty() && types.all { it in IMAGE_TYPES } -> SingleCategoryIndicator(
                    iconRes = R.drawable.ic_image,
                    color = MediaGroupPalette.IMAGE_PRIMARY
                )
                else -> null
            }

            if (indicator != null) {
                return createIconSpan(context, indicator.iconRes, indicator.color)
            }
            
            // Build colored string for each media type
            val text = buildString {
                if (MediaType.IMAGE in types) append("I")
                if (MediaType.VIDEO in types) append("V")
                if (MediaType.AUDIO in types) append("A")
                if (MediaType.GIF in types) append("G")
                if (MediaType.TEXT in types) append("T")
                if (MediaType.PDF in types) append("P")
                if (MediaType.EPUB in types) append("E")
            }
            
            if (text.isEmpty()) return ""
            
            val spannable = SpannableString(text)
            var position = 0
            
            if (MediaType.IMAGE in types) {
                spannable.setSpan(ForegroundColorSpan(MediaGroupPalette.colorForType(MediaType.IMAGE)), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                position++
            }
            if (MediaType.VIDEO in types) {
                spannable.setSpan(ForegroundColorSpan(MediaGroupPalette.colorForType(MediaType.VIDEO)), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                position++
            }
            if (MediaType.AUDIO in types) {
                spannable.setSpan(ForegroundColorSpan(MediaGroupPalette.colorForType(MediaType.AUDIO)), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                position++
            }
            if (MediaType.GIF in types) {
                spannable.setSpan(ForegroundColorSpan(MediaGroupPalette.colorForType(MediaType.GIF)), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                position++
            }
            if (MediaType.TEXT in types) {
                spannable.setSpan(ForegroundColorSpan(MediaGroupPalette.colorForType(MediaType.TEXT)), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                position++
            }
            if (MediaType.PDF in types) {
                spannable.setSpan(ForegroundColorSpan(MediaGroupPalette.colorForType(MediaType.PDF)), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                position++
            }
            if (MediaType.EPUB in types) {
                spannable.setSpan(ForegroundColorSpan(MediaGroupPalette.colorForType(MediaType.EPUB)), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            return spannable
        }

        private fun createIconSpan(context: android.content.Context, iconRes: Int, tintColor: Int): CharSequence {
            val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate()
                ?: return ""

            // Match indicator text height (~12sp) with slight extra for readability
            @Suppress("DEPRECATION")
            val sizePx = (12f * context.resources.displayMetrics.scaledDensity).toInt()
            drawable.setBounds(0, 0, sizePx, sizePx)
            DrawableCompat.setTint(drawable, ColorStateList.valueOf(tintColor).defaultColor)

            val builder = SpannableStringBuilder(" ")
            builder.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return builder
        }
    }

    private var isGridMode: Boolean = false
    private var useCompactElements: Boolean = false
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

    fun setUseCompactElements(enabled: Boolean) {
        if (this.useCompactElements != enabled) {
            this.useCompactElements = enabled
            notifyDataSetChanged()
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
                var textSize = when {
                    resource.name.length <= 10 -> binding.root.context.resources.getDimension(R.dimen.text_size_huge)
                    resource.name.length <= 16 -> binding.root.context.resources.getDimension(R.dimen.text_size_large)
                    else -> binding.root.context.resources.getDimension(R.dimen.text_size_normal)
                }
                if (useCompactElements) textSize *= 0.8f
                tvResourceName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize)

                if (useCompactElements) {
                    val iconSize = (24 * root.resources.displayMetrics.density).toInt()
                    ivResourceTypeIcon.layoutParams.width = iconSize
                    ivResourceTypeIcon.layoutParams.height = iconSize
                    tvMediaTypes.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                } else {
                    val iconSize = (40 * root.resources.displayMetrics.density).toInt()
                    ivResourceTypeIcon.layoutParams.width = iconSize
                    ivResourceTypeIcon.layoutParams.height = iconSize
                    tvMediaTypes.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                }
                
                // Set icon based on resource type
                val iconRes = when {
                    resource.path == LocalMediaScanner.VIRTUAL_PATH_RECENT -> R.drawable.ic_virtual_recent
                    resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO -> R.drawable.ic_virtual_music
                    resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO -> R.drawable.ic_virtual_video
                    resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS -> R.drawable.ic_virtual_docs
                    resource.id == -100L -> R.drawable.ic_resource_favorites
                    else -> when (resource.type) {
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
                
                // Writable/Lock indicator — not shown for virtual aggregate paths
                val isVirtualResource = resource.path.startsWith("virtual://")
                tvWritableIndicator.visibility = if (!resource.isDestination && !resource.isWritable && !isVirtualResource && resource.id != -100L) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }

                // Media Types Indicator (IVAGTPE or ALL)
                tvMediaTypes.text = if (resource.id == -100L) "" else formatMediaTypes(root.context, resource.supportedMediaTypes, resource.allFiles)

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
                if (useCompactElements) {
                    tvResourceName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                    tvResourcePath.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                    tvResourceComment.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                } else {
                    tvResourceName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                    tvResourcePath.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                    tvResourceComment.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                }

                // For cloud resources, show provider name instead of folder ID
                tvResourcePath.text = if (resource.type == ResourceType.CLOUD && resource.cloudProvider != null) {
                    // Show provider name and account email (accountId) for cloud resources
                    val account = resource.accountId?.takeIf { it.isNotEmpty() }
                    if (account != null) {
                        "${resource.cloudProvider.name} ($account)"
                    } else {
                        "${resource.cloudProvider.name} / ${resource.name}"
                    }
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

                // Set chip background tint per resource type
                val chipColorRes = when (resource.type) {
                    ResourceType.LOCAL -> R.color.chip_local_bg
                    ResourceType.SMB -> R.color.chip_smb_bg
                    ResourceType.SFTP -> R.color.chip_sftp_bg
                    ResourceType.FTP -> R.color.chip_ftp_bg
                    ResourceType.CLOUD -> R.color.chip_cloud_bg
                }
                val chipColor = ContextCompat.getColor(root.context, chipColorRes)
                tvResourceType.backgroundTintList = ColorStateList.valueOf(chipColor)
                // Ensure text contrast: dark text on light bg (light theme), white on dark bg (dark theme)
                val r = android.graphics.Color.red(chipColor)
                val g = android.graphics.Color.green(chipColor)
                val b = android.graphics.Color.blue(chipColor)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                tvResourceType.setTextColor(
                    if (luminance > 0.4) android.graphics.Color.parseColor("#1A1A1A")
                    else android.graphics.Color.WHITE
                )
                val iconRes = when {
                    resource.path == LocalMediaScanner.VIRTUAL_PATH_RECENT -> R.drawable.ic_virtual_recent
                    resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO -> R.drawable.ic_virtual_music
                    resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO -> R.drawable.ic_virtual_video
                    resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS -> R.drawable.ic_virtual_docs
                    resource.id == -100L -> R.drawable.ic_resource_favorites
                    else -> when (resource.type) {
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
                
                if (useCompactElements) {
                    tvFileCount.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                    tvMediaTypes.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                    tvLastSync.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                    tvResourceType.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                    
                    val p4 = (4 * root.resources.displayMetrics.density).toInt()
                    val p8 = (8 * root.resources.displayMetrics.density).toInt()
                    rootLayout.setPadding(p8, p4, p8, p4)
                    
                    val iconSize = (24 * root.resources.displayMetrics.density).toInt()
                    ivResourceTypeIcon.layoutParams.width = iconSize
                    ivResourceTypeIcon.layoutParams.height = iconSize
                } else {
                    tvFileCount.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    tvMediaTypes.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    tvLastSync.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    tvResourceType.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    
                    val p12 = (12 * root.resources.displayMetrics.density).toInt()
                    val p16 = (16 * root.resources.displayMetrics.density).toInt()
                    rootLayout.setPadding(p16, p12, p16, p12)
                    
                    val iconSize = (48 * root.resources.displayMetrics.density).toInt()
                    ivResourceTypeIcon.layoutParams.width = iconSize
                    ivResourceTypeIcon.layoutParams.height = iconSize
                }
                
                tvMediaTypes.text = if (resource.id == -100L) "" else formatMediaTypes(root.context, resource.supportedMediaTypes, resource.allFiles)
                
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
                
                // Show lock icon only for non-destination, non-virtual resources without write access
                // Destinations are expected to be writable; virtual paths are aggregate views, not folders
                val isVirtualRes = resource.path.startsWith("virtual://")
                tvWritableIndicator.visibility = if (!resource.isDestination && !resource.isWritable && !isVirtualRes && resource.id != -100L) {
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
                    val isPredefinedVirtualResource = resource.path in VirtualPathUtils.ALL_VIRTUAL_PATHS

                    // Check if we should show inline actions (Landscape/Tablet)
                    val showInlineActions = root.resources.getBoolean(R.bool.is_resource_actions_inline)
                    
                    if (showInlineActions) {
                        btnMoreActions.visibility = android.view.View.GONE
                        layoutInlineActions.visibility = android.view.View.VISIBLE
                        
                        // Bind inline button listeners with debounce protection
                        btnEdit.setOnClickListenerDebounced { onEditClick(resource) }
                        btnCopy.visibility = if (isPredefinedVirtualResource) android.view.View.GONE else android.view.View.VISIBLE
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
                            popup.menu.findItem(R.id.action_copy)?.isVisible = !isPredefinedVirtualResource
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
