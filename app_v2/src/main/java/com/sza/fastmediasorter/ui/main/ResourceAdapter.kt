package com.sza.fastmediasorter.ui.main

import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.menu.MenuActionSurface
import com.sza.fastmediasorter.core.menu.ResourceActionCatalog
import com.sza.fastmediasorter.core.menu.ResourceMenuAction
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.databinding.ItemResourceBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.isAllFilesPredefined
import com.sza.fastmediasorter.ui.common.MediaGroupPalette
import com.sza.fastmediasorter.ui.icon.ResourceIconComposer
import com.sza.fastmediasorter.util.VirtualPathUtils
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import com.sza.fastmediasorter.utils.setOnLongClickListenerDebounced

/** Callback from the adapter to the host (MainActivity) to start an ItemTouchHelper drag. */
interface DragStartListener {
    fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
}

@android.annotation.SuppressLint("SetTextI18n")
class ResourceAdapter(
    private val onItemClick: (MediaResource) -> Unit,
    private val onIconClick: (MediaResource) -> Unit,
    private val onItemLongClick: (MediaResource) -> Unit,
    private val onEditClick: (MediaResource) -> Unit,
    private val onCopyFromClick: (MediaResource) -> Unit,
    private val onDeleteClick: (MediaResource) -> Unit,
    private val onMoveUpClick: (MediaResource) -> Unit,
    private val onMoveDownClick: (MediaResource) -> Unit,
    private val onMoveToTopClick: (MediaResource) -> Unit,
    private val onMoveToBottomClick: (MediaResource) -> Unit,
    private val onScanClick: (MediaResource) -> Unit = {},
    // S0422: export a single resource to a share file.
    private val onExportClick: (MediaResource) -> Unit = {},
    // S0984: share an SFTP resource's access as a .fmscfg config file (SFTP resources only).
    private val onShareSftpAccessClick: (MediaResource) -> Unit = {},
    // S0661: pin the configurable resource-launch widget for this resource from the row overflow.
    private val onAddToHomeScreenClick: (MediaResource) -> Unit = {},
    // S0293 Phase 08: per-resource "Open in new window" entry on the main list. Optional - when
    // null, the menu item is hidden (e.g. on devices where allowSeparateWindow=false).
    private val onOpenInNewWindowClick: ((MediaResource) -> Unit)? = null,
    // S0727: mutable so MainActivity can fold in the persisted allowSeparateWindow preference off the
    // Main thread (the initial value is the non-blocking runtime multi-window capability).
    private var isOpenInNewWindowVisible: Boolean = false,
    // S0963 (Pillar 2): per-resource "Open in VR Cinema" entry. Optional - when null the item is
    // hidden; visibility is also gated by isOpenInVrCinemaVisible (XR availability mirror).
    private val onOpenInVrCinemaClick: ((MediaResource) -> Unit)? = null,
    private var isOpenInVrCinemaVisible: Boolean = false
) : ListAdapter<MediaResource, RecyclerView.ViewHolder>(ResourceDiffCallback()) {

    /**
     * S1424: one pass over [ResourceActionCatalog] decides which rows an inflated resource menu
     * shows. Both view holders call this - each used to carry its own copy of these six rules, and a
     * copy is what lets the main window and the launcher desktop drift apart about what a resource
     * offers (strategic ADR-1).
     */
    private fun applyActionVisibility(menu: Menu, resource: MediaResource) {
        val facts = ResourceActionCatalog.Facts(
            isPredefinedVirtual = resource.path in VirtualPathUtils.ALL_VIRTUAL_PATHS,
            isSftp = resource.type == ResourceType.SFTP,
            isQuickSlideshowEligible = isQuickSlideshowEligible(resource),
            // The callback being null is as good a reason to hide the row as the capability being
            // absent: without it the row would have nothing to call.
            isNewWindowAvailable = isOpenInNewWindowVisible && onOpenInNewWindowClick != null,
            isVrCinemaAvailable = isOpenInVrCinemaVisible && onOpenInVrCinemaClick != null,
        )
        val visible = ResourceActionCatalog.actionsFor(MenuActionSurface.MAIN_WINDOW, facts).toSet()
        ResourceMenuAction.entries.forEach { action ->
            menu.findItem(action.menuItemId)?.isVisible = action in visible
        }
    }

    /**
     * S1424: one routing table for both view holders, replacing two hand-kept `when` blocks.
     *
     * The grid copy was missing "Add to home screen" outright, so in tile mode that row drew, took
     * the tap and did nothing (strategic 7). One table cannot be missing an entry for a row it also
     * decides the visibility of.
     */
    private fun onActionSelected(itemId: Int, resource: MediaResource): Boolean {
        val action = ResourceMenuAction.byMenuItemId(itemId) ?: return false
        perform(action, resource)
        return true
    }

    private fun perform(action: ResourceMenuAction, resource: MediaResource) {
        when (action) {
            ResourceMenuAction.OPEN -> onItemClick(resource)
            ResourceMenuAction.LAUNCH_PLAYER -> onIconClick(resource)
            ResourceMenuAction.OPEN_IN_VR_CINEMA -> onOpenInVrCinemaClick?.invoke(resource)
            ResourceMenuAction.ADD_TO_HOME_SCREEN -> onAddToHomeScreenClick(resource)
            ResourceMenuAction.EDIT -> onEditClick(resource)
            ResourceMenuAction.COPY -> onCopyFromClick(resource)
            ResourceMenuAction.EXPORT -> onExportClick(resource)
            ResourceMenuAction.SHARE_SFTP_ACCESS -> onShareSftpAccessClick(resource)
            ResourceMenuAction.SCAN -> onScanClick(resource)
            ResourceMenuAction.MOVE_UP -> onMoveUpClick(resource)
            ResourceMenuAction.MOVE_DOWN -> onMoveDownClick(resource)
            ResourceMenuAction.MOVE_TO_TOP -> onMoveToTopClick(resource)
            ResourceMenuAction.MOVE_TO_BOTTOM -> onMoveToBottomClick(resource)
            ResourceMenuAction.DELETE -> onDeleteClick(resource)
            ResourceMenuAction.OPEN_IN_SEPARATE_WINDOW -> onOpenInNewWindowClick?.invoke(resource)
        }
    }

    companion object {
        const val VIEW_TYPE_LIST = 0
        const val VIEW_TYPE_GRID = 1

        // C-213: partial-rebind payload to refresh only the selection visual on a selection change.
        private const val PAYLOAD_SELECTION = "payload_selection"

        // B-514: dark text color for the resource-type chip on light backgrounds (was per-bind Color.parseColor).
        private const val DARK_TEXT_COLOR = 0xFF1A1A1A.toInt()

        private val DOCUMENT_TYPES = setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT)
        private val IMAGE_TYPES = setOf(MediaType.IMAGE, MediaType.GIF)

        private const val MEDIA_TYPE_CACHE_INITIAL = 64
        private const val MEDIA_TYPE_CACHE_LOAD = 0.75f
        private const val MEDIA_TYPE_CACHE_MAX = 128

        // Bounded main-thread memo for formatMediaTypes; the built SpannableString is immutable after
        // build, so the same instance is safely shared across TextViews for identical type-sets.
        private val mediaTypeFormatCache =
            object : LinkedHashMap<Pair<Set<MediaType>, Boolean>, CharSequence>(
                MEDIA_TYPE_CACHE_INITIAL,
                MEDIA_TYPE_CACHE_LOAD,
                true,
            ) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<Pair<Set<MediaType>, Boolean>, CharSequence>?
                ): Boolean = size > MEDIA_TYPE_CACHE_MAX
            }

        private data class SingleCategoryIndicator(
            val iconRes: Int,
            val color: Int
        )
        
        /** Formats supported media types as colored IVAGTPE string, or "ALL" for allFiles mode. */
        fun formatMediaTypes(context: android.content.Context, types: Set<MediaType>, allFiles: Boolean): CharSequence {
            val key = types to allFiles
            mediaTypeFormatCache[key]?.let { return it }

            if (allFiles) {
                return "ALL"
            }

            // Single category: audio=note, docs=book, video=video icon, images=image icon.
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

            val text = buildString {
                if (MediaType.IMAGE in types) append("I")
                if (MediaType.VIDEO in types) append("V")
                if (MediaType.AUDIO in types) append("A")
                if (MediaType.GIF in types) append("G")
                if (MediaType.TEXT in types) append("T")
                if (MediaType.PDF in types) append("P")
                if (MediaType.EPUB in types) append("E")
                if (MediaType.OFFICE_DOCUMENT in types) append("O")
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
                position++
            }
            if (MediaType.OFFICE_DOCUMENT in types) {
                spannable.setSpan(ForegroundColorSpan(MediaGroupPalette.colorForType(MediaType.OFFICE_DOCUMENT)), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            mediaTypeFormatCache[key] = spannable
            return spannable
        }

        fun isQuickSlideshowEligible(resource: MediaResource): Boolean =
            resource.profile == ResourceProfile.AUDIO_LIBRARY ||
            resource.profile == ResourceProfile.VIDEO_LIBRARY ||
            resource.profile == ResourceProfile.PHOTO_STORAGE

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

    /** Optional drag listener; when non-null, drag handles become visible. */
    var dragStartListener: DragStartListener? = null

    // S0160: when true, ⋮ button is forced visible on every resource row; inline buttons hidden.
    private var overflowModeEnabled: Boolean = false

    fun setOverflowModeEnabled(enabled: Boolean) {
        if (this.overflowModeEnabled != enabled) {
            this.overflowModeEnabled = enabled
            notifyDataSetChanged()
        }
    }

    /** Live-reorder shadow of currentList (ADR-2); reconciled via submitList() in clearView(). */
    private val _items = mutableListOf<MediaResource>()

    override fun submitList(list: List<MediaResource>?) {
        _items.clear()
        if (list != null) _items.addAll(list)
        super.submitList(list)
    }

    /** Moves an item in _items for live animation; commit via submitList() in clearView(). */
    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        val item = _items.removeAt(from)
        _items.add(to, item)
        notifyItemMoved(from, to)
    }

    /** Returns the current in-memory order after a drag, for persistence in clearView(). */
    fun getDragOrderedList(): List<MediaResource> = _items.toList()

    fun setSelectedResource(resourceId: Long?) {
        val previousId = selectedResourceId
        selectedResourceId = resourceId
        currentList.forEachIndexed { index, resource ->
            if (resource.id == previousId || resource.id == resourceId) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
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

    // S0727: toggle the per-row "Open in new window" menu entry after construction, so the persisted
    // allowSeparateWindow preference can be applied off the Main thread.
    fun setOpenInNewWindowVisible(visible: Boolean) {
        if (this.isOpenInNewWindowVisible != visible) {
            this.isOpenInNewWindowVisible = visible
            notifyDataSetChanged()
        }
    }

    // S0963: toggle the per-row "Open in VR Cinema" entry after construction, mirroring XR availability.
    fun setOpenInVrCinemaVisible(visible: Boolean) {
        if (this.isOpenInVrCinemaVisible != visible) {
            this.isOpenInVrCinemaVisible = visible
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int) = if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST

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

    // C-213: refresh only the selection visual on a selection change instead of rebinding the whole row.
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val resource = getItem(position)
            when (holder) {
                is GridViewHolder -> holder.applySelectionVisual(resource, selectedResourceId)
                is ResourceViewHolder -> holder.applySelectionVisual(resource, selectedResourceId)
            }
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val resource = getItem(position)
        when (holder) {
            is GridViewHolder -> holder.bind(resource, selectedResourceId)
            is ResourceViewHolder -> holder.bind(resource, selectedResourceId)
        }
    }

    inner class GridViewHolder(
        private val binding: com.sza.fastmediasorter.databinding.ItemResourceGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(resource: MediaResource, selectedId: Long?) {
            binding.apply {
                tvResourceName.text = resource.name
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
                
                // Set icon using S0034 composer (falls back to legacy type-based icon when no custom icon)
                val iconDrawable = ResourceIconComposer.compose(root.context, resource)
                ivResourceTypeIcon.setImageDrawable(iconDrawable)

                val quickEligibleGrid = isQuickSlideshowEligible(resource)
                if (quickEligibleGrid) {
                    ivResourceTypeIcon.isClickable = true
                    ivResourceTypeIcon.isFocusable = true
                    ivResourceTypeIcon.foreground = ContextCompat.getDrawable(
                        root.context,
                        R.drawable.ripple_icon_quick_slideshow
                    )
                    ivResourceTypeIcon.contentDescription =
                        root.context.getString(R.string.cd_resource_icon_quick_slideshow)
                    ivResourceTypeIcon.setOnClickListenerDebounced { onIconClick(resource) }
                    ivResourceTypeIcon.background = ContextCompat.getDrawable(
                        root.context,
                        R.drawable.bg_icon_media_storage_frame
                    )
                } else {
                    // Non-library resources: tapping the icon opens the resource in Browse, identical
                    // to tapping the card body. The listener must be set explicitly - setOnClickListener(null)
                    // leaves the view clickable (View.setOnClickListener calls setClickable(true)) with no
                    // handler, so the icon would swallow the tap instead of letting it bubble to the row.
                    ivResourceTypeIcon.isClickable = true
                    ivResourceTypeIcon.isFocusable = false
                    ivResourceTypeIcon.foreground = null
                    ivResourceTypeIcon.background = null
                    ivResourceTypeIcon.setOnClickListenerDebounced { onItemClick(resource) }
                    ivResourceTypeIcon.contentDescription =
                        root.context.getString(R.string.resource_type_icon)
                }

                if (resource.isDestination) {
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
                
                this@GridViewHolder.applySelectionVisual(resource, selectedId)

                // Writable/Lock indicator - not shown for virtual aggregate paths
                val isVirtualResource = resource.path.startsWith("virtual://")
                tvWritableIndicator.visibility = if (!resource.isDestination && !resource.isWritable && !isVirtualResource && resource.id != -100L) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }

                tvMediaTypes.text = if (resource.id == -100L) "" else formatMediaTypes(root.context, resource.supportedMediaTypes, resource.allFiles)
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

                root.isFocusable = true
                root.isFocusableInTouchMode = false

                // Drag handle - visible for real resources when drag is wired up.
                // S0488: the pinned All-files row stays first, so it is non-draggable like Favorites (-100L).
                val isDraggable = dragStartListener != null && resource.id != -100L && !resource.isAllFilesPredefined
                ivDragHandle.visibility = if (isDraggable) android.view.View.VISIBLE else android.view.View.GONE
                ivDragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        dragStartListener?.onStartDrag(this@GridViewHolder)
                    }
                    false
                }

                // Overflow menu for grid items (S0160)
                if (resource.id == -100L) {
                    btnMoreActions.visibility = android.view.View.GONE
                } else if (overflowModeEnabled) {
                    btnMoreActions.visibility = android.view.View.VISIBLE
                    // S0977: per-card E2E handle so a specific resource's overflow is uniquely targetable
                    btnMoreActions.contentDescription = "more_options:${resource.name}"
                    btnMoreActions.setOnClickListener { view ->
                        val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
                        popup.menuInflater.inflate(R.menu.resource_item_actions, popup.menu)
                        applyActionVisibility(popup.menu, resource)
                        popup.setForceShowIcon(true)
                        tintPopupMenuIcons(view.context, popup.menu)
                        popup.setOnMenuItemClickListener { item -> onActionSelected(item.itemId, resource) }
                        popup.show()
                    }
                } else {
                    btnMoreActions.visibility = android.view.View.GONE
                }
            }
        }

        // C-213: the grid row's selection visual is both root.isSelected and the row background
        // (selected -> holo_blue_light, otherwise zebra/favorites/unavailable). Shared by the full
        // bind and the PAYLOAD_SELECTION partial rebind so both produce identical state.
        fun applySelectionVisual(resource: MediaResource, selectedId: Long?) {
            val root = binding.root
            root.isSelected = resource.id == selectedId
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

                tvResourceType.text = when (resource.type) {
                    ResourceType.LOCAL -> root.context.getString(R.string.resource_type_local)
                    ResourceType.SMB -> root.context.getString(R.string.resource_type_smb)
                    ResourceType.SFTP -> root.context.getString(R.string.resource_type_sftp)
                    ResourceType.FTP -> root.context.getString(R.string.resource_type_ftp)
                    ResourceType.CLOUD -> root.context.getString(R.string.resource_type_cloud)
                    ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> root.context.getString(R.string.resource_type_stream)
                    ResourceType.WEAR_WATCH -> root.context.getString(R.string.resource_type_wear_watch)
                }

                val chipColorRes = when (resource.type) {
                    ResourceType.LOCAL -> R.color.chip_local_bg
                    ResourceType.SMB -> R.color.chip_smb_bg
                    ResourceType.SFTP -> R.color.chip_sftp_bg
                    ResourceType.FTP -> R.color.chip_ftp_bg
                    ResourceType.CLOUD -> R.color.chip_cloud_bg
                    ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> R.color.chip_cloud_bg
                    ResourceType.WEAR_WATCH -> R.color.chip_wear_watch_bg
                }
                val chipColor = ContextCompat.getColor(root.context, chipColorRes)
                tvResourceType.backgroundTintList = ColorStateList.valueOf(chipColor)
                // Ensure text contrast: dark text on light bg (light theme), white on dark bg (dark theme)
                val r = android.graphics.Color.red(chipColor)
                val g = android.graphics.Color.green(chipColor)
                val b = android.graphics.Color.blue(chipColor)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                tvResourceType.setTextColor(
                    if (luminance > 0.4) {
                        DARK_TEXT_COLOR
                    } else {
                        android.graphics.Color.WHITE
                    }
                )
                val iconDrawable = ResourceIconComposer.compose(root.context, resource)
                ivResourceTypeIcon.setImageDrawable(iconDrawable)

                val quickEligibleList = isQuickSlideshowEligible(resource)
                if (quickEligibleList) {
                    ivResourceTypeIcon.isClickable = true
                    ivResourceTypeIcon.isFocusable = true
                    ivResourceTypeIcon.foreground = ContextCompat.getDrawable(
                        root.context,
                        R.drawable.ripple_icon_quick_slideshow
                    )
                    ivResourceTypeIcon.contentDescription =
                        root.context.getString(R.string.cd_resource_icon_quick_slideshow)
                    ivResourceTypeIcon.setOnClickListenerDebounced { onIconClick(resource) }
                    ivResourceTypeIcon.background = ContextCompat.getDrawable(
                        root.context,
                        R.drawable.bg_icon_media_storage_frame
                    )
                } else {
                    // Non-library resources: tapping the icon opens the resource in Browse, identical
                    // to tapping the card body. The listener must be set explicitly - setOnClickListener(null)
                    // leaves the view clickable (View.setOnClickListener calls setClickable(true)) with no
                    // handler, so the icon would swallow the tap instead of letting it bubble to the row.
                    ivResourceTypeIcon.isClickable = true
                    ivResourceTypeIcon.isFocusable = false
                    ivResourceTypeIcon.foreground = null
                    ivResourceTypeIcon.background = null
                    ivResourceTypeIcon.setOnClickListenerDebounced { onItemClick(resource) }
                    ivResourceTypeIcon.contentDescription =
                        root.context.getString(R.string.resource_type_icon)
                }

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
                if (resource.isDestination) {
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
                
                // S0200 Phase 06: needs-sign-in indicator for Drive resources whose primary account
                // is unbound or stale. Takes priority over the generic isAvailable indicator.
                val driveNeedsSignIn = resource.type == ResourceType.CLOUD &&
                    resource.cloudProvider == com.sza.fastmediasorter.data.cloud.CloudProvider.GOOGLE_DRIVE &&
                    resource.needsSignIn
                // S1861: an unreachable watch is named in words rather than left as an empty folder
                // and a tinted row - the state must read without relying on colour (strategic 3.2).
                val watchOffline = resource.type == ResourceType.WEAR_WATCH && !resource.isAvailable
                when {
                    driveNeedsSignIn -> {
                        tvAvailabilityIndicator.visibility = android.view.View.VISIBLE
                        tvAvailabilityIndicator.text =
                            root.context.getString(R.string.s0200_resource_needs_sign_in_label)
                    }
                    watchOffline -> {
                        tvAvailabilityIndicator.visibility = android.view.View.VISIBLE
                        tvAvailabilityIndicator.text =
                            root.context.getString(R.string.paired_watch_not_connected)
                    }
                    else -> {
                        // Restored explicitly: the two branches above overwrite the text, and a
                        // recycled row would otherwise keep their wording for an unrelated resource.
                        tvAvailabilityIndicator.setText(R.string.item_resource_tvAvailabilityIndicator_text)
                        tvAvailabilityIndicator.visibility = if (resource.isAvailable) {
                            android.view.View.GONE
                        } else {
                            android.view.View.VISIBLE
                        }
                    }
                }

                if (!resource.isAvailable) {
                    val bgColor = ContextCompat.getColor(
                        rootLayout.context,
                        R.color.unavailable_resource_bg
                    )
                    rootLayout.setBackgroundColor(bgColor)
                } else {
                    // Zebra striping for available resources
                    val bgColor = if (resource.id == -100L) {
                        ContextCompat.getColor(rootLayout.context, R.color.resource_item_bg_odd)
                    } else if (bindingAdapterPosition % 2 == 0) {
                        // Even rows - slightly darker/different
                        ContextCompat.getColor(rootLayout.context, R.color.resource_item_bg_even)
                    } else {
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

                applySelectionVisual(resource, selectedId)
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
                
                root.isFocusable = true
                root.isFocusableInTouchMode = false

                // Hide actions for Favorites
                if (resource.id == -100L) {
                    btnMoreActions.visibility = android.view.View.GONE
                    layoutInlineActions.visibility = android.view.View.GONE
                    ivDragHandle.visibility = android.view.View.GONE
                } else {
                    val isPredefinedVirtualResource = resource.path in VirtualPathUtils.ALL_VIRTUAL_PATHS

                    val showInlineActions = !overflowModeEnabled &&
                        root.resources.getBoolean(R.bool.is_resource_actions_inline)
                    if (showInlineActions) {
                        btnMoreActions.visibility = android.view.View.GONE
                        layoutInlineActions.visibility = android.view.View.VISIBLE
                        btnEdit.setOnClickListenerDebounced { onEditClick(resource) }
                        btnCopy.visibility = if (isPredefinedVirtualResource) android.view.View.GONE else android.view.View.VISIBLE
                        btnCopy.setOnClickListenerDebounced { onCopyFromClick(resource) }
                        btnMoveUp.setOnClickListenerDebounced { onMoveUpClick(resource) }
                        btnMoveDown.setOnClickListenerDebounced { onMoveDownClick(resource) }
                        btnDelete.setOnClickListenerDebounced { onDeleteClick(resource) }
                        
                    } else {
                        btnMoreActions.visibility = android.view.View.VISIBLE
                        layoutInlineActions.visibility = android.view.View.GONE
                        // S0977: per-card E2E handle so a specific resource's overflow is uniquely targetable
                        btnMoreActions.contentDescription = "more_options:${resource.name}"

                        btnMoreActions.setOnClickListenerDebounced { view ->
                            val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
                            popup.menuInflater.inflate(R.menu.resource_item_actions, popup.menu)
                            applyActionVisibility(popup.menu, resource)
                            popup.setForceShowIcon(true)
                            tintPopupMenuIcons(view.context, popup.menu)
                            popup.setOnMenuItemClickListener { item ->
                                onActionSelected(item.itemId, resource)
                            }
                            popup.show()
                        }
                    }

                    // Drag handle - visible for real resources when drag is wired up
                    // S0488: the pinned All-files row stays first, so it is non-draggable.
                    val isDraggable = dragStartListener != null && !resource.isAllFilesPredefined
                    ivDragHandle.visibility = if (isDraggable) android.view.View.VISIBLE else android.view.View.GONE
                    ivDragHandle.setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            dragStartListener?.onStartDrag(this@ResourceViewHolder)
                        }
                        false
                    }
                }

            }
        }

        // C-213: the list row's selection visual is only root.isSelected (the row background is
        // selection-independent zebra striping). Shared by the full bind and the PAYLOAD_SELECTION
        // partial rebind.
        fun applySelectionVisual(resource: MediaResource, selectedId: Long?) {
            binding.root.isSelected = resource.id == selectedId
        }
    }

    private class ResourceDiffCallback : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource) = oldItem == newItem
    }
}

/**
 * PopupMenu renders raw menu icons untinted. Most of this menu's vectors are plain white fills
 * (shared with dark player overlays), so in the light theme they turn invisible/white. Tint
 * mutated copies with colorControlNormal so icons always match the popup's own text color.
 */
private fun tintPopupMenuIcons(context: android.content.Context, menu: android.view.Menu) {
    val tv = android.util.TypedValue()
    val resolved = context.theme.resolveAttribute(androidx.appcompat.R.attr.colorControlNormal, tv, true) ||
        context.theme.resolveAttribute(android.R.attr.colorControlNormal, tv, true)
    if (!resolved) return
    val color = if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
    for (i in 0 until menu.size()) {
        val item = menu.getItem(i)
        val icon = item.icon ?: continue
        val wrapped = DrawableCompat.wrap(icon.mutate())
        DrawableCompat.setTint(wrapped, color)
        item.icon = wrapped
    }
}
