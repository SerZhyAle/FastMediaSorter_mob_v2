package com.sza.fastmediasorter.ui.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.util.AudioMetadataLoader
import com.sza.fastmediasorter.core.util.HeifSupportUtils
import com.sza.fastmediasorter.core.util.MemoryTier
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.core.util.formatMediaDuration
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.cloud.glide.GoogleDriveThumbnailData
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.databinding.ItemMediaFileBinding
import com.sza.fastmediasorter.databinding.ItemMediaFileGridBinding
import com.sza.fastmediasorter.databinding.ItemMediaFileGridNoThumbBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.browse.helpers.BrowseItemOperation
import com.sza.fastmediasorter.ui.browse.helpers.BrowseItemOperationPolicy
import com.sza.fastmediasorter.ui.browse.managers.BrowseApkTileBadgeBinder
import com.sza.fastmediasorter.util.BinaryFileThumbnailGenerator
import com.sza.fastmediasorter.util.ExtensionThumbnailGenerator
import timber.log.Timber
import java.io.File
import java.util.Date

class MediaFileAdapter(
    private val onFileClick: (MediaFile) -> Unit,
    private val onFileLongClick: (MediaFile) -> Unit,
    private val onContextMenuRequest: (android.view.View, MediaFile) -> Unit = { _, _ -> },
    private val onSelectionChanged: (MediaFile, Boolean) -> Unit,
    private val onSelectionRangeRequested: (MediaFile) -> Unit = {}, // Long click on checkbox
    private val onPlayClick: (MediaFile) -> Unit,
    private val onFavoriteClick: (MediaFile) -> Unit = {},
    private val onCopyClick: (MediaFile) -> Unit = {},
    private val onMoveClick: (MediaFile) -> Unit = {},
    private val onRenameClick: (MediaFile) -> Unit = {},
    private val onDeleteClick: (MediaFile) -> Unit = {},
    private val onOverflowMenuClick: (MediaFile, android.view.View) -> Unit = { _, _ -> },
    private val onFolderClick: (MediaFile) -> Unit = {}, // Callback for folder navigation
    private val onBinaryFileClick: (MediaFile) -> Unit = {}, // Callback for binary files (Task 6)
    private val apkTileBadgeBinder: BrowseApkTileBadgeBinder,
    private var isGridMode: Boolean = false,
    private var thumbnailSize: Int = 96, // Default size in dp
    private var useCompactElements: Boolean = false, // Global 0.5x scaling mode
    private val getShowVideoThumbnails: () -> Boolean = { false }, // Callback to get current setting
    private val getShowPdfThumbnails: () -> Boolean = { false }, // Callback to get PDF thumbnail setting
    private var disableThumbnails: Boolean = false, // Skip thumbnail loading, show extension icons only
    // S0783: favicon sprite-atlas plumbing forwarded to AdapterThumbnailLoader so STREAM favorites rows
    // (live channels in the Favorites list) show the channel logo. No-op defaults keep other screens as-is.
    private val faviconResolver: (String) -> Int? = { null },
    private val faviconTileLoader: suspend (Int) -> android.graphics.Bitmap? = { null },
    private val faviconScope: kotlinx.coroutines.CoroutineScope? = null
) : ListAdapter<MediaFile, RecyclerView.ViewHolder>(MediaFileDiffCallback()) {

    private var selectedPaths = setOf<String>()
    private var credentialsId: String? = null // Credentials ID for network files
    private var hasDestinations: Boolean = false
    private var isWritable: Boolean = false
    private var refreshVersion: Int = 0
    private var skipInitialThumbnailLoad = false // Control initial thumbnail loading
    private var showFavoriteButton: Boolean = true // Show/hide favorite button based on settings
    private var hideGridActionButtons: Boolean = false // Hide quick action buttons in grid mode
    private var fileOpsInOverflowMenu: Boolean = true
    private var isAudioOnlyMode: Boolean = false

    private val thumbnailLoader = AdapterThumbnailLoader(
        getIsScrolling = { isScrolling },
        getDisableThumbnails = { disableThumbnails },
        getRefreshVersion = { refreshVersion },
        getCredentialsId = { credentialsId },
        getShowVideoThumbnails = getShowVideoThumbnails,
        getShowPdfThumbnails = getShowPdfThumbnails,
        getBinaryGenerator = { binaryThumbnailGenerator },
        faviconResolver = faviconResolver,
        faviconTileLoader = faviconTileLoader,
        faviconScope = faviconScope
    )

    // ── Drag-to-reorder (MANUAL sort mode) ───────────────────────────────────
    private val dragController = AdapterDragController()

    // Public type alias so call sites keep using MediaFileAdapter.DragStartListener
    interface DragStartListener : AdapterDragController.DragStartListener

    fun setDragStartListener(listener: AdapterDragController.DragStartListener?) {
        dragController.listener = listener
    }

    fun showDragHandles(show: Boolean) {
        if (dragController.setShowHandles(show, currentList)) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    /** Called by ItemTouchHelper.onMove - reorders the shadow list and notifies RecyclerView. */
    fun moveItem(from: Int, to: Int) {
        if (dragController.moveItem(from, to)) notifyItemMoved(from, to)
    }

    /** Returns file paths in current drag-visual order (call from clearView). */
    fun getOrderedPaths(): List<String> = dragController.getOrderedPaths()

    // ─────────────────────────────────────────────────────────────────────────

    // Inline audio player state (adapter-level, not part of MediaFile model)
    private var inlinePlayerState: InlinePlayerState = InlinePlayerState()

    /** Update inline playback state and partially rebind only affected items. Uses PAYLOAD_PLAYBACK_STATE to avoid full rebind (thumbnail stays intact). */
    fun updateInlinePlayerState(newState: InlinePlayerState) {
        val oldState = inlinePlayerState
        inlinePlayerState = newState
        val affectedPaths = setOf(
            oldState.playingPath,
            oldState.downloadingPath,
            newState.playingPath,
            newState.downloadingPath
        ).filterNotNull()
        currentList.forEachIndexed { index, file ->
            if (file.path in affectedPaths) {
                notifyItemChanged(index, PAYLOAD_PLAYBACK_STATE)
            }
        }
    }

    // Fast scroll detection to skip thumbnail loading during rapid scrolling
    private var isScrolling: Boolean = false

    // Viewport-based audio metadata loader for network files
    private var audioMetadataLoader: AudioMetadataLoader? = null

    // Binary file thumbnail generator (Task 6)
    private var binaryThumbnailGenerator: BinaryFileThumbnailGenerator? = null
    private var disableDocumentPreviewsOnLowMemory: Boolean? = null

    fun setBinaryThumbnailGenerator(generator: BinaryFileThumbnailGenerator) {
        binaryThumbnailGenerator = generator
    }

    fun setAudioMetadataLoader(loader: AudioMetadataLoader) {
        audioMetadataLoader = loader
    }

    /** Called after scrolling stops to load audio metadata for visible network audio files. Mirrors [loadVisibleThumbnails] pattern. Triggers [AudioMetadataLoader] for each qualifying item; callbacks update the ViewHolder via [PAYLOAD_AUDIO_METADATA]. */
    fun loadVisibleAudioMetadata(firstVisiblePos: Int, lastVisiblePos: Int) {
        val loader = audioMetadataLoader ?: return
        if (firstVisiblePos < 0 || lastVisiblePos < 0 || firstVisiblePos > lastVisiblePos) return
        val safeFirst = firstVisiblePos.coerceAtMost(itemCount - 1)
        val safeLast = lastVisiblePos.coerceAtMost(itemCount - 1)
        if (safeFirst < 0) return

        for (i in safeFirst..safeLast) {
            val file = getItem(i)
            if (file.isDirectory || file.type != MediaType.AUDIO) continue
            if (PathUtils.isLocalPath(file.path) || file.path.startsWith("content://")) continue
            if (file.artist != null && file.title != null) continue

            val position = i
            loader.loadIfNeeded(file) { enriched ->
                // Update shared RAM cache so PlayerActivity picks up the metadata
                enriched.resourceId?.let { resId ->
                    MediaFilesCacheManager.updateFile(resId, enriched.path, enriched)
                }
                // Callback on main thread - tell RecyclerView to rebind text only
                if (position in 0 until itemCount && getItem(position).path == file.path) {
                    notifyItemChanged(position, PAYLOAD_AUDIO_METADATA)
                }
            }
        }
    }

    private fun shouldDisableDocumentPreviews(context: android.content.Context): Boolean {
        if (disableDocumentPreviewsOnLowMemory == null) {
            disableDocumentPreviewsOnLowMemory = MemoryTier.detect(context) == MemoryTier.LOW
        }
        return disableDocumentPreviewsOnLowMemory == true
    }

    fun incrementRefreshVersion() {
        refreshVersion++
    }

    /** Set scrolling state to skip thumbnail loading during fast scroll. Call setScrolling(true) when scroll starts, setScrolling(false) when scroll ends. */
    fun setScrolling(scrolling: Boolean) {
        if (isScrolling != scrolling) {
            isScrolling = scrolling
        }
    }

    /** Called after scrolling stops to load thumbnails for currently visible items. Uses notifyItemRangeChanged with LOAD_THUMBNAILS payload. */
    fun loadVisibleThumbnails(firstVisiblePos: Int, lastVisiblePos: Int) {
        if (firstVisiblePos < 0 || lastVisiblePos < 0 || firstVisiblePos > lastVisiblePos) return
        val count = (lastVisiblePos - firstVisiblePos + 1).coerceAtMost(itemCount - firstVisiblePos)
        if (count > 0) {
            notifyItemRangeChanged(firstVisiblePos, count, "LOAD_THUMBNAILS")
        }
    }

    fun setCredentialsId(id: String?) {
        credentialsId = id
    }

    fun setShowFavoriteButton(show: Boolean) {
        if (this.showFavoriteButton != show) {
            this.showFavoriteButton = show
            notifyDataSetChanged() // Update button visibility across all items
        }
    }

    fun setHideGridActionButtons(hide: Boolean) {
        if (this.hideGridActionButtons != hide) {
            this.hideGridActionButtons = hide
            notifyDataSetChanged() // Update button visibility across all items
        }
    }

    fun setFileOpsInOverflowMenu(enabled: Boolean) {
        if (this.fileOpsInOverflowMenu != enabled) {
            this.fileOpsInOverflowMenu = enabled
            notifyDataSetChanged()
        }
    }

    val isInGridMode: Boolean get() = isGridMode

    fun setResourcePermissions(hasDestinations: Boolean, isWritable: Boolean) {
        if (this.hasDestinations != hasDestinations || this.isWritable != isWritable) {
            this.hasDestinations = hasDestinations
            this.isWritable = isWritable
            notifyDataSetChanged() // Update button visibility across all items
        }
    }

    fun setDisableThumbnails(disabled: Boolean) {
        if (disableThumbnails != disabled) {
            disableThumbnails = disabled
            // Force rebind all items to switch between thumbnail/icon mode
            notifyDataSetChanged()
        }
    }

    fun setAudioOnlyMode(isAudioOnly: Boolean) {
        if (isAudioOnlyMode != isAudioOnly) {
            isAudioOnlyMode = isAudioOnly
            notifyDataSetChanged()
        }
    }

    fun setUseCompactElements(enabled: Boolean) {
        if (useCompactElements != enabled) {
            useCompactElements = enabled
            notifyDataSetChanged()
        }
    }

    /** Enable/disable initial thumbnail loading in bind(). When true, thumbnails are loaded only via LOAD_THUMBNAILS payload. */
    fun setSkipInitialThumbnailLoad(skip: Boolean) {
        skipInitialThumbnailLoad = skip
    }

    fun getSkipInitialThumbnailLoad(): Boolean = skipInitialThumbnailLoad

    /** Returns [file] enriched with AudioMetadataLoader's in-memory cache, if available. This is a fast ConcurrentHashMap lookup - safe to call on every bind. */
    private fun resolveAudioMetadata(file: MediaFile): MediaFile {
        if (file.type != MediaType.AUDIO || file.isDirectory) return file
        if (file.artist != null && file.title != null) return file
        val cached = audioMetadataLoader?.getCachedMetadata(file.path) ?: return file
        return file.copy(
            artist = cached.artist ?: file.artist,
            album = cached.album ?: file.album,
            title = cached.title ?: file.title,
            duration = cached.duration ?: file.duration
        )
    }

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_GRID_NO_THUMB = 2
        private const val PAYLOAD_VIEW_MODE_CHANGE = "view_mode_change"
        private const val PAYLOAD_PLAYBACK_STATE = "playback_state"
        private const val PAYLOAD_SELECTION = "payload_selection"
        private const val PAYLOAD_AUDIO_METADATA = MediaFileDiffCallback.PAYLOAD_AUDIO_METADATA
        private const val AUDIO_ONLY_THUMBNAIL_DP = 48

        private const val COMPACT_CAPTION_SP = 11f
        private const val NORMAL_CAPTION_SP = 13f

        // S2177: TextView's default line spacing, used to answer "how many lines does this tile hold".
        private const val CAPTION_LINE_SPACING = 1.25f

        // S2177: the tile height left over is what still shows the colour identifying the file type,
        // which the owner asked the icon to keep doing once the name is written on it.
        private const val MAX_OVERLAY_CAPTION_LINES = 5
        private const val BELOW_TILE_CAPTION_LINES = 3

        // Payloads onBindViewHolder handles as partial binds; anything else falls back to a full bind.
        private val KNOWN_PAYLOADS = setOf(
            "LOAD_THUMBNAILS",
            "FAVORITE_CHANGED",
            PAYLOAD_PLAYBACK_STATE,
            PAYLOAD_AUDIO_METADATA,
            PAYLOAD_SELECTION,
        )
    }

    // Shared click-listener helpers used by both ListViewHolder and GridViewHolder.
    private inline fun View.bindFileClick(
        crossinline getFile: () -> MediaFile?,
        crossinline action: (MediaFile) -> Unit,
    ) = setOnClickListener {
        val file = getFile() ?: return@setOnClickListener
        action(file)
    }

    private inline fun View.bindFileTypeClick(crossinline getFile: () -> MediaFile?) =
        setOnClickListener {
            val file = getFile() ?: return@setOnClickListener
            when {
                file.isDirectory -> onFolderClick(file)
                file.type.isBinaryFile() -> onBinaryFileClick(file)
                else -> onFileClick(file)
            }
        }

    private inline fun View.bindRightClickContextMenu(crossinline getFile: () -> MediaFile?) =
        setOnGenericMotionListener { view, event ->
            if (event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                event.buttonState == MotionEvent.BUTTON_SECONDARY) {
                val file = getFile() ?: return@setOnGenericMotionListener false
                onContextMenuRequest(view, file)
                return@setOnGenericMotionListener true
            }
            false
        }

    fun setGridMode(enabled: Boolean, iconSize: Int = 96) {
        if (isGridMode != enabled || thumbnailSize != iconSize) {
            val modeChanged = isGridMode != enabled
            val sizeChanged = thumbnailSize != iconSize
            isGridMode = enabled
            thumbnailSize = iconSize

            // When thumbnail size changes, increment refresh version to force Glide reload
            if (sizeChanged) {
                incrementRefreshVersion()
            }

            // When mode changes (List↔Grid), use payload to rebind items efficiently
            // When only size changes, force thumbnail reload with payload
            if (sizeChanged && !modeChanged) {
                // Size changed: notify with LOAD_THUMBNAILS payload to force thumbnail reload
                notifyItemRangeChanged(0, itemCount, "LOAD_THUMBNAILS")
            } else {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_VIEW_MODE_CHANGE)
            }
        }
    }

    fun setSelectedPaths(paths: Set<String>) {
        if (selectedPaths == paths) return

        val oldSelected = selectedPaths
        selectedPaths = paths

        // Optimize updates: only notify changed items
        // If selection was cleared
        if (paths.isEmpty() && oldSelected.isNotEmpty()) {
            currentList.forEachIndexed { index, file ->
                if (file.path in oldSelected) {
                    notifyItemChanged(index, PAYLOAD_SELECTION)
                }
            }
            return
        }

        // If selection was added/changed
        currentList.forEachIndexed { index, file ->
            val wasSelected = file.path in oldSelected
            val isSelected = file.path in paths
            if (wasSelected != isSelected) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            isGridMode && disableThumbnails -> VIEW_TYPE_GRID_NO_THUMB
            isGridMode -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GRID_NO_THUMB -> {
                val binding = ItemMediaFileGridNoThumbBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                GridNoThumbViewHolder(binding)
            }
            VIEW_TYPE_GRID -> {
                val binding = ItemMediaFileGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                GridViewHolder(binding)
            }
            else -> {
                val binding = ItemMediaFileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ListViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val file = getItem(position)
        when (holder) {
            is ListViewHolder -> holder.bind(file, selectedPaths)
            is GridViewHolder -> holder.bind(file, selectedPaths)
            is GridNoThumbViewHolder -> holder.bind(file, selectedPaths)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        val file = getItem(position)
        // Apply each present payload as a partial bind; fall back to a full bind only when none matched.
        if (payloads.contains("LOAD_THUMBNAILS")) {
            when (holder) {
                is ListViewHolder -> holder.loadThumbnailOnly(file)
                is GridViewHolder -> holder.loadThumbnailOnly(file)
                is GridNoThumbViewHolder -> holder.loadThumbnailOnly(file)
            }
        }
        if (payloads.contains("FAVORITE_CHANGED")) {
            applyFavoritePayload(holder, file)
        }
        if (payloads.contains(PAYLOAD_PLAYBACK_STATE) && holder is ListViewHolder) {
            holder.updatePlaybackState(file)
        }
        if (payloads.contains(PAYLOAD_AUDIO_METADATA) && holder is ListViewHolder) {
            // Network files may lack enriched data here; resolveAudioMetadata checks the in-memory cache.
            holder.updateAudioMetadataText(resolveAudioMetadata(file))
        }
        if (payloads.contains(PAYLOAD_SELECTION)) {
            when (holder) {
                is ListViewHolder -> holder.applySelectionVisual(file, selectedPaths)
                is GridViewHolder -> holder.applySelectionVisual(file, selectedPaths)
                is GridNoThumbViewHolder -> holder.applySelectionVisual(file, selectedPaths)
            }
        }
        if (payloads.none { it in KNOWN_PAYLOADS }) {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    // Favorite-icon partial bind for list/grid rows (GridNoThumb has no favorite button).
    private fun applyFavoritePayload(holder: RecyclerView.ViewHolder, file: MediaFile) {
        if (holder !is ListViewHolder && holder !is GridViewHolder) return
        val iconRes = if (file.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        holder.itemView
            .findViewById<android.widget.ImageButton>(R.id.btnFavorite)
            ?.setImageResource(iconRes)
    }

    override fun onCurrentListChanged(
        previousList: MutableList<com.sza.fastmediasorter.domain.model.MediaFile>,
        currentList: MutableList<com.sza.fastmediasorter.domain.model.MediaFile>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        // Keep drag shadow in sync when a new list is submitted (e.g. after reload)
        dragController.syncWithCurrentList(currentList)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        // Explicitly clear Glide requests when view is recycled to free up ConnectionThrottleManager slots immediately. This is critical for network resources where concurrency is limited.
        when (holder) {
            is ListViewHolder -> {
                holder.clearImage()
                holder.stopPlaybackAnimations() // Cancel inline playback animations when view is recycled
            }
            is GridViewHolder -> holder.clearImage()
            is GridNoThumbViewHolder -> holder.clearImage()
        }
        apkTileBadgeBinder.onViewRecycled(holder.itemView)
    }

    inner class ListViewHolder(
        private val binding: ItemMediaFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastLoadedKey: String? = null
        private val playbackAnimator = InlinePlaybackAnimator(binding.btnPlayInline)

        // Hoisted once per holder: avoids a per-bind List + iterator allocation for the action-button sizing loop.
        private val actionButtons by lazy {
            listOf(
                binding.btnFavorite,
                binding.btnCopyItem,
                binding.btnMoveItem,
                binding.btnRenameItem,
                binding.btnDeleteItem,
                binding.btnPlayInline
            )
        }

        // Cached once per holder: applyInlineHighlight previously inflated this drawable on every call.
        private val inlinePlayingBorder by lazy {
            ContextCompat.getDrawable(binding.root.context, R.drawable.item_inline_playing_border)
        }
        private val selectionCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            val file = getItemByPosition() ?: return@OnCheckedChangeListener
            if (BrowseItemOperationPolicy.isSelectable(file)) {
                onSelectionChanged(file, isChecked)
            }
        }

        private fun getItemByPosition(): MediaFile? {
            val position = bindingAdapterPosition
            return if (position != RecyclerView.NO_POSITION) {
                this@MediaFileAdapter.getItem(position)
            } else {
                null
            }
        }

        init {
            val getFile: () -> MediaFile? = ::getItemByPosition
            binding.ivThumbnail.bindFileTypeClick(getFile)
            binding.root.bindFileTypeClick(getFile)
            binding.root.bindRightClickContextMenu(getFile)
            binding.root.setOnLongClickListener {
                val file = getFile() ?: return@setOnLongClickListener false
                if (BrowseItemOperationPolicy.isSelectable(file)) onFileLongClick(file)
                true
            }
            // The clickable thumbnail otherwise swallows the long press (firing its open-player
            // click); give it the same range-select long-click as the row root.
            binding.ivThumbnail.setOnLongClickListener {
                val file = getFile() ?: return@setOnLongClickListener false
                if (BrowseItemOperationPolicy.isSelectable(file)) onFileLongClick(file)
                true
            }
            binding.cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)
            binding.btnFavorite.bindFileClick(getFile, onFavoriteClick)
            binding.btnCopyItem.bindFileClick(getFile, onCopyClick)
            binding.btnMoveItem.bindFileClick(getFile, onMoveClick)
            binding.btnRenameItem.bindFileClick(getFile, onRenameClick)
            binding.btnDeleteItem.bindFileClick(getFile, onDeleteClick)
            binding.btnOverflowMenu.setOnClickListener {
                val file = getFile() ?: return@setOnClickListener
                onOverflowMenuClick(file, it)
            }
            binding.btnPlayInline.bindFileClick(getFile, onPlayClick)
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    dragController.listener?.onStartDrag(this)
                }
                false
            }

            // Task 8: Make item focusable for keyboard navigation
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
        }

        fun clearImage() {
            // S0783: cancel any in-flight stream-favicon decode targeting this row before recycle.
            thumbnailLoader.cancelFavicon(binding.ivThumbnail)
            // Check if the context (activity) is still valid before clearing Glide request
            val context = binding.ivThumbnail.context
            if (context is android.app.Activity && context.isDestroyed) {
                // Activity is destroyed, skip Glide clear to avoid IllegalArgumentException
                lastLoadedKey = null
                return
            }
            try {
                Glide.with(context).clear(binding.ivThumbnail)
            } catch (e: IllegalArgumentException) {
                // Catch any remaining edge cases where activity might be destroyed
                Timber.w("Failed to clear Glide request: ${e.message}")
            }
            lastLoadedKey = null
        }

        /** Update only the inline play button icon/animation for this item. Called via PAYLOAD_PLAYBACK_STATE partial rebind. */
        fun updatePlaybackState(file: MediaFile) {
            val state = this@MediaFileAdapter.inlinePlayerState
            val isThisItem = state.playingPath == file.path
            val isDownloading = state.downloadingPath == file.path
            val shouldShow = file.type == MediaType.AUDIO && !file.isDirectory && !(isGridMode && hideGridActionButtons)
            binding.btnPlayInline.isVisible = shouldShow
            if (!shouldShow) return

            // In audio-only mode update info line with detail / cache progress; in other modes leave tvFileInfo as-is
            if (isAudioOnlyMode) {
                val baseInfo = AdapterFileInfoFormatter.buildAudioDetailLine(itemView.context, file)
                if (isDownloading) {
                    val progress = state.downloadProgressPercent.coerceIn(0, 100)
                    binding.tvFileInfo.text = if (progress > 0) "$baseInfo • Cache $progress%" else "$baseInfo • Cache..."
                } else {
                    binding.tvFileInfo.text = baseInfo
                }
            }

            binding.btnPlayInline.isEnabled = !isDownloading
            applyInlineHighlight(isThisItem || isDownloading)

            when {
                isDownloading -> {
                    binding.btnPlayInline.setImageResource(android.R.drawable.stat_sys_download)
                    playbackAnimator.stopNote()
                    playbackAnimator.startDownload()
                }
                !isThisItem -> {
                    binding.btnPlayInline.setImageResource(R.drawable.ic_play_inline_outline)
                    playbackAnimator.stopNote()
                    playbackAnimator.stopDownload()
                }
                state.status == PlaybackStatus.PLAYING -> {
                    binding.btnPlayInline.setImageResource(R.drawable.ic_music_note)
                    playbackAnimator.stopDownload()
                    playbackAnimator.startNote()
                }
                state.status == PlaybackStatus.PAUSED -> {
                    binding.btnPlayInline.setImageResource(R.drawable.ic_pause)
                    playbackAnimator.stopNote()
                    playbackAnimator.stopDownload()
                }
            }
        }

        /** Update only text labels when audio metadata (artist/album/title/duration) is enriched. Called via PAYLOAD_AUDIO_METADATA partial rebind - avoids thumbnail reload. */
        fun updateAudioMetadataText(file: MediaFile) {
            val audioOnlyFile = isAudioOnlyMode && !file.isDirectory
            if (audioOnlyFile) {
                binding.tvFileName.text = AdapterFileInfoFormatter.buildAudioDisplayName(file)
                binding.tvFileInfo.text = AdapterFileInfoFormatter.buildAudioDetailLine(itemView.context, file)
            } else {
                // Non audio-only mode: filename stays, but info line may include duration
                binding.tvFileInfo.text = AdapterFileInfoFormatter.buildFileInfo(itemView.context, file)
            }
        }

        fun stopPlaybackAnimations() = playbackAnimator.stopAll()

        private fun applyInlineHighlight(active: Boolean) {
            binding.root.foreground = if (active) inlinePlayingBorder else null
        }

        fun loadThumbnailOnly(file: MediaFile) {
            // In audio-only mode, ivThumbnail is GONE - skip entirely
            if (isAudioOnlyMode && !file.isDirectory) return

            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"

            if (lastLoadedKey == newKey) return
            thumbnailLoader.load(binding.ivThumbnail, file, lastLoadedKey, isListMode = true)
            // Always update key so AUDIO/TEXT/Binary fast-paths don't re-trigger
            lastLoadedKey = newKey
        }

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            // Note: Glide automatically cancels previous request when load() is called on same ImageView

            binding.apply {
                val isFolder = file.isDirectory

                // In audio-only mode, hide thumbnail entirely for files (not folders)
                // so file name/details shift to the left and take its space
                val audioOnlyFile = isAudioOnlyMode && !isFolder
                ivThumbnail.visibility = if (audioOnlyFile) android.view.View.GONE else android.view.View.VISIBLE
                val thumbnailFrame = root.findViewById<android.view.View?>(R.id.flThumbnailFrame)
                thumbnailFrame?.visibility = if (audioOnlyFile) android.view.View.GONE else android.view.View.VISIBLE

                // The checkbox overlays the thumbnail bottom-left (S0419). In audio-only mode the
                // thumbnail is GONE, so reserve a start margin so the name clears the overlaid checkbox.
                val nameMarginStartPx = ((if (audioOnlyFile) 32 else 4) * root.resources.displayMetrics.density).toInt()
                (tvFileName.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                    if (lp.marginStart != nameMarginStartPx) {
                        lp.marginStart = nameMarginStartPx
                        tvFileName.layoutParams = lp
                    }
                }

                if (!audioOnlyFile) {
                    // Apply thumbnail size from settings for list mode (halved if compact mode enabled)
                    val effectiveBaseSize = if (useCompactElements) thumbnailSize / 2 else thumbnailSize
                    val thumbnailSizePx = if (this@MediaFileAdapter.disableThumbnails) {
                        val dSize = if (useCompactElements) 20 else 32
                        (dSize * root.resources.displayMetrics.density).toInt()
                    } else {
                        (effectiveBaseSize * root.resources.displayMetrics.density).toInt()
                    }
                    // noLegal wraps the thumbnail in a frame so the badge stays anchored to the
                    // same box the adapter resizes in standard list mode.
                    thumbnailFrame?.layoutParams?.let { frameParams ->
                        frameParams.width = thumbnailSizePx
                        frameParams.height = thumbnailSizePx
                        thumbnailFrame.layoutParams = frameParams
                    }
                    ivThumbnail.layoutParams.width = thumbnailSizePx
                    ivThumbnail.layoutParams.height = thumbnailSizePx
                }

                if (useCompactElements) {
                    tvFileName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    tvFileInfo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f)
                    val p2 = (2 * root.resources.displayMetrics.density).toInt()
                    val p6 = (6 * root.resources.displayMetrics.density).toInt()
                    val p8 = (8 * root.resources.displayMetrics.density).toInt()
                    root.setPaddingRelative(p6, p2, p8, p2)
                } else {
                    tvFileName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    tvFileInfo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    val p2 = (2 * root.resources.displayMetrics.density).toInt()
                    val p8 = (8 * root.resources.displayMetrics.density).toInt()
                    val p12 = (12 * root.resources.displayMetrics.density).toInt()
                    root.setPaddingRelative(p8, p2, p12, p2)
                }

                // Scale action buttons: compact = 24dp (0.75× normal), normal = 32dp
                // 16dp was too small - icon area was only ~4dp with 6dp XML padding
                val btnSizePx = if (useCompactElements) {
                    (24 * root.resources.displayMetrics.density).toInt()
                } else {
                    (32 * root.resources.displayMetrics.density).toInt()
                }
                for (btn in actionButtons) {
                    btn.layoutParams.width = btnSizePx
                    btn.layoutParams.height = btnSizePx
                }

                // Checkbox visibility/state + selection background color (shared with PAYLOAD_SELECTION partial rebind)
                this@ListViewHolder.applySelectionVisual(file, selectedPaths)

                // Checkbox sits over the thumbnail in thumbnail mode; in no-thumbnail mode the icon
                // is too small to host the overlay, so move the checkbox flush to its left (S0419).
                applyCheckboxPlacement(
                    anchor = thumbnailFrame ?: ivThumbnail,
                    leftOfIcon = this@MediaFileAdapter.disableThumbnails && !audioOnlyFile
                )

                // In audio-only mode: top line = Artist - Title, bottom = filename + size + duration
                // For network audio files, check AudioMetadataLoader cache for enriched data.
                val displayFile = resolveAudioMetadata(file)
                if (audioOnlyFile) {
                    tvFileName.text = AdapterFileInfoFormatter.buildAudioDisplayName(displayFile)
                    tvFileInfo.text = AdapterFileInfoFormatter.buildAudioDetailLine(itemView.context, displayFile)
                } else {
                    tvFileName.text = file.name
                    tvFileInfo.text = AdapterFileInfoFormatter.buildFileInfo(itemView.context, file)
                }

                // Load thumbnail or folder icon
                if (isFolder) {
                    // ALWAYS show folder icon, regardless of skipInitialThumbnailLoad
                    ivThumbnail.setImageResource(R.drawable.ic_folder)
                    ivThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    ivThumbnail.setBackgroundColor(Color.TRANSPARENT)
                    ivThumbnail.imageTintList = null // Clear any tint from previous thumbnails
                    ivThumbnail.colorFilter = null
                } else if (!audioOnlyFile) {
                    // For files in non-audio-only mode, respect skipInitialThumbnailLoad flag
                    if (!skipInitialThumbnailLoad) {
                        loadThumbnail(file)
                    }
                }
                // audioOnlyFile: thumbnail is GONE, nothing to load

                // Favorite button (hide for folders; hidden in no-thumbnail mode where it lives in
                // the overflow menu only - S0419).
                btnFavorite.isVisible = showFavoriteButton &&
                    BrowseItemOperationPolicy.supports(BrowseItemOperation.FAVORITE, file) &&
                    !this@MediaFileAdapter.disableThumbnails
                btnFavorite.setImageResource(
                    if (file.isFavorite) R.drawable.ic_star_filled
                    else R.drawable.ic_star_outline
                )

                // Setup operation buttons with visibility checks
                // HIDE buttons if: (It's Grid Mode AND HideGridActions is ON) OR it's a folder
                // S1325: a folder row keeps the transfer actions; only the single-file ones drop out,
                // and which is which is the policy's answer, not an inline isDirectory test.
                val supportsRowOps = BrowseItemOperationPolicy.supports(BrowseItemOperation.COPY, file)
                val shouldHideActions = (isGridMode && hideGridActionButtons) || !supportsRowOps
                val useOverflow = fileOpsInOverflowMenu
                // Overflow button
                binding.btnOverflowMenu.isVisible = useOverflow
                // Direct op buttons - hide when overflow mode OR standard shouldHideActions rule applies
                btnCopyItem.isVisible = !shouldHideActions && !useOverflow
                btnMoveItem.isVisible = isWritable && !shouldHideActions && !useOverflow
                btnRenameItem.isVisible = isWritable && !shouldHideActions && !useOverflow
                btnDeleteItem.isVisible = isWritable && !shouldHideActions && !useOverflow

                // Inline play button: visible for any audio file, suppressed by hideGridActionButtons in grid mode
                val showPlayButton = file.type == MediaType.AUDIO &&
                    BrowseItemOperationPolicy.supports(BrowseItemOperation.OPEN_IN_PLAYER, file) &&
                    !(isGridMode && hideGridActionButtons)
                if (showPlayButton) {
                    updatePlaybackState(file)
                } else {
                    btnPlayInline.isVisible = false
                    btnPlayInline.isEnabled = true
                    applyInlineHighlight(false)
                }

                // Drag handle: visible only in MANUAL sort mode (set via showDragHandles)
                ivDragHandle.isVisible = dragController.showHandles &&
                    BrowseItemOperationPolicy.supports(BrowseItemOperation.REORDER, file)

                // Flavor-specific tile chrome must bind after the holder's own visibility state is stable.
                apkTileBadgeBinder.bind(root, file)
            }
        }

        private fun loadThumbnail(file: MediaFile) {
            thumbnailLoader.load(binding.ivThumbnail, file, lastLoadedKey, isListMode = true)
                ?.let { lastLoadedKey = it }
        }

        /**
         * Apply only the selection-reflecting visuals (checkbox + row background). Shared by bind()
         * and the PAYLOAD_SELECTION partial rebind so the two paths cannot diverge.
         */
        fun applySelectionVisual(file: MediaFile, selectedPaths: Set<String>) {
            val isSelected = file.path in selectedPaths
            val isFolder = file.isDirectory
            binding.cbSelect.isVisible = BrowseItemOperationPolicy.isSelectable(file)
            if (binding.cbSelect.isVisible) {
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.isChecked = isSelected
                binding.cbSelect.contentDescription = binding.root.context.getString(
                    if (isFolder) R.string.browse_row_folder_checkbox else R.string.browse_row_file_checkbox,
                    file.name,
                )
                binding.cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)
            }
            val context = binding.root.context
            val backgroundColor = when {
                isSelected -> context.getColor(com.sza.fastmediasorter.R.color.item_selected)
                bindingAdapterPosition % 2 != 0 -> context.getColor(com.sza.fastmediasorter.R.color.item_alternate)
                else -> context.getColor(com.sza.fastmediasorter.R.color.item_normal)
            }
            binding.root.setBackgroundColor(backgroundColor)
        }

        /**
         * Position the selection checkbox relative to the file icon ([anchor] is the thumbnail, or
         * the badge frame in flavors that wrap it). Thumbnail mode overlays the checkbox on the
         * icon's bottom-left corner; no-thumbnail mode shrinks the icon to ~32dp where the overlay
         * would hide it, so the checkbox is pinned flush to the icon's left and the icon shifts
         * right to make room (S0419).
         */
        private fun applyCheckboxPlacement(anchor: View, leftOfIcon: Boolean) {
            val cbLp = binding.cbSelect.layoutParams as? ConstraintLayout.LayoutParams ?: return
            val anchorLp = anchor.layoutParams as? ConstraintLayout.LayoutParams ?: return
            val unset = ConstraintLayout.LayoutParams.UNSET
            if (leftOfIcon) {
                cbLp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                cbLp.topToTop = anchor.id
                cbLp.bottomToBottom = anchor.id
                anchorLp.startToStart = unset
                anchorLp.startToEnd = binding.cbSelect.id
            } else {
                cbLp.startToStart = anchor.id
                cbLp.topToTop = unset
                cbLp.bottomToBottom = anchor.id
                anchorLp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                anchorLp.startToEnd = unset
            }
            binding.cbSelect.layoutParams = cbLp
            anchor.layoutParams = anchorLp
        }

    }

    // Grid ViewHolder for grid mode
    inner class GridViewHolder(
        private val binding: ItemMediaFileGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastLoadedKey: String? = null
        private var operationsContainer: android.widget.LinearLayout? = null
        private var btnCopyItem: android.widget.ImageButton? = null
        private var btnMoveItem: android.widget.ImageButton? = null
        private var btnRenameItem: android.widget.ImageButton? = null
        private var btnDeleteItem: android.widget.ImageButton? = null
        private val selectionCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            val file = getItemByPosition() ?: return@OnCheckedChangeListener
            if (BrowseItemOperationPolicy.isSelectable(file)) {
                onSelectionChanged(file, isChecked)
            }
        }

        private fun getItemByPosition(): MediaFile? {
            val position = bindingAdapterPosition
            return if (position != RecyclerView.NO_POSITION) {
                this@MediaFileAdapter.getItem(position)
            } else {
                null
            }
        }

        private fun ensureOperationsInflated() {
            if (operationsContainer != null) return
            val inflated = binding.stubOperations.inflate()
            operationsContainer = inflated as? android.widget.LinearLayout
            btnCopyItem = inflated.findViewById(R.id.btnCopyItem)
            btnMoveItem = inflated.findViewById(R.id.btnMoveItem)
            btnRenameItem = inflated.findViewById(R.id.btnRenameItem)
            btnDeleteItem = inflated.findViewById(R.id.btnDeleteItem)
            val getFile: () -> MediaFile? = ::getItemByPosition
            btnCopyItem?.bindFileClick(getFile, onCopyClick)
            btnMoveItem?.bindFileClick(getFile, onMoveClick)
            btnRenameItem?.bindFileClick(getFile, onRenameClick)
            btnDeleteItem?.bindFileClick(getFile, onDeleteClick)
        }

        init {
            val getFile: () -> MediaFile? = ::getItemByPosition
            binding.ivThumbnail.bindFileTypeClick(getFile)
            binding.root.bindFileTypeClick(getFile)
            binding.root.bindRightClickContextMenu(getFile)
            binding.root.setOnLongClickListener {
                val file = getFile() ?: return@setOnLongClickListener false
                onFileLongClick(file)
                true
            }
            // Grid cells are dominated by the clickable thumbnail; without its own
            // long-click handler the long press fires the thumbnail's click (open player)
            // instead of bubbling to root for range selection.
            binding.ivThumbnail.setOnLongClickListener {
                val file = getFile() ?: return@setOnLongClickListener false
                onFileLongClick(file)
                true
            }
            binding.cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)
            binding.cbSelect.setOnLongClickListener {
                val file = getFile() ?: return@setOnLongClickListener false
                if (BrowseItemOperationPolicy.isSelectable(file) && !binding.cbSelect.isChecked) {
                    onSelectionRangeRequested(file)
                }
                true
            }
            binding.btnFavorite.bindFileClick(getFile, onFavoriteClick)
            binding.btnOverflowMenu.setOnClickListener {
                val file = getFile() ?: return@setOnClickListener
                onOverflowMenuClick(file, it)
            }
            // Make item focusable for keyboard navigation
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
        }

        fun clearImage() {
            // S0783: cancel any in-flight stream-favicon decode targeting this row before recycle.
            thumbnailLoader.cancelFavicon(binding.ivThumbnail)
            // Check if the context (activity) is still valid before clearing Glide request
            val context = binding.ivThumbnail.context
            if (context is android.app.Activity && context.isDestroyed) {
                // Activity is destroyed, skip Glide clear to avoid IllegalArgumentException
                lastLoadedKey = null
                return
            }
            try {
                Glide.with(context).clear(binding.ivThumbnail)
            } catch (e: IllegalArgumentException) {
                // Catch any remaining edge cases where activity might be destroyed
                Timber.w("Failed to clear Glide request: ${e.message}")
            }
            lastLoadedKey = null
        }

        fun loadThumbnailOnly(file: MediaFile) {
            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"
            if (lastLoadedKey == newKey) return
            val binarySizePx = (this@MediaFileAdapter.thumbnailSize * binding.root.context.resources.displayMetrics.density).toInt()
            thumbnailLoader.load(binding.ivThumbnail, file, lastLoadedKey, binarySizePx, isListMode = false)
            lastLoadedKey = newKey
        }

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            // Note: Glide automatically cancels previous request when load() is called on same ImageView

            binding.apply {
                val isFolder = file.isDirectory

                // Checkbox visibility/state + selection card color (shared with PAYLOAD_SELECTION partial rebind)
                applySelectionVisual(file, selectedPaths)

                // Set dynamic thumbnail size (height only - width is match_parent)
                val sizeInPx = if (this@MediaFileAdapter.disableThumbnails) {
                    val baseDisableSize = if (isAudioOnlyMode) AUDIO_ONLY_THUMBNAIL_DP else 64
                    val effectiveDisableSize = if (useCompactElements) baseDisableSize / 2 else baseDisableSize
                    (effectiveDisableSize * root.context.resources.displayMetrics.density).toInt()
                } else {
                    val sizeDp = if (isAudioOnlyMode) AUDIO_ONLY_THUMBNAIL_DP else thumbnailSize
                    val effectiveSizeDp = if (useCompactElements) sizeDp / 2 else sizeDp
                    (effectiveSizeDp * root.context.resources.displayMetrics.density).toInt() // User preference
                }

                // Apply user-defined icon size to thumbnail container and image.
                // Container controls effective cell height; updating only ImageView is not enough.
                val containerParams = flThumbnailContainer.layoutParams
                if (containerParams.height != sizeInPx) {
                    containerParams.height = sizeInPx
                    flThumbnailContainer.layoutParams = containerParams
                }

                val imgParams = ivThumbnail.layoutParams
                if (imgParams.height != sizeInPx) {
                    imgParams.height = sizeInPx
                    ivThumbnail.layoutParams = imgParams
                }

                val captionSp = if (useCompactElements) COMPACT_CAPTION_SP else NORMAL_CAPTION_SP
                tvFileName.text = file.name
                tvFileName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, captionSp)
                placeCaption(file, sizeInPx, captionSp)

                // Load thumbnail or folder icon
                if (isFolder) {
                    // ALWAYS show folder icon, regardless of skipInitialThumbnailLoad
                    ivThumbnail.setImageResource(R.drawable.ic_folder)
                    ivThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    ivThumbnail.setBackgroundColor(Color.TRANSPARENT)
                    ivThumbnail.imageTintList = null // Clear any tint from previous thumbnails
                    ivThumbnail.colorFilter = null
                } else {
                    // For files, respect skipInitialThumbnailLoad flag
                    if (!skipInitialThumbnailLoad) {
                        loadThumbnail(file)
                    }
                }

                // Favorite button (folders are navigation items, not favorite targets)
                btnFavorite.isVisible = showFavoriteButton &&
                    BrowseItemOperationPolicy.supports(BrowseItemOperation.FAVORITE, file)
                btnFavorite.setImageResource(
                    if (file.isFavorite) R.drawable.ic_star_filled
                    else R.drawable.ic_star_outline
                )

                // Setup operation buttons with visibility
                val useOverflow = fileOpsInOverflowMenu
                binding.btnOverflowMenu.isVisible = useOverflow
                if (!useOverflow) {
                    val shouldShowAnyOperation = true // Copy is always available (select folder option)
                    if (shouldShowAnyOperation) ensureOperationsInflated()
                    operationsContainer?.isVisible = shouldShowAnyOperation && !hideGridActionButtons
                    btnCopyItem?.isVisible = !hideGridActionButtons
                    btnMoveItem?.isVisible = isWritable && !hideGridActionButtons
                    btnRenameItem?.isVisible = isWritable && !hideGridActionButtons
                    btnDeleteItem?.isVisible = isWritable && !hideGridActionButtons
                } else {
                    operationsContainer?.isVisible = false
                }

                // Flavor-specific tile chrome must bind after the holder's own visibility state is stable.
                apkTileBadgeBinder.bind(root, file)
            }
        }

        /**
         * Put the file name over the tile, or under it, per S2177.
         *
         * Over it only when the picture is the extension tile shared by every file of that type, so
         * covering it loses nothing; a real preview, a folder icon and a channel favicon all keep the
         * caption below, because there the picture is what identifies the item.
         *
         * Both branches assign both views. A recycled holder that previously showed the overlay would
         * otherwise arrive with `tvFileName` still hidden and render a nameless cell.
         *
         * [tileHeightPx] is the live container height, which follows the user's icon-size setting -
         * hence the line count is measured here rather than fixed in the layout.
         *
         * A flavor badge along the top edge (S2202) is subtracted from that height and the plate is shifted
         * down by half of it, because the corner controls above clear a centred plate on their own while a
         * full-width badge does not.
         */
        private fun placeCaption(file: MediaFile, tileHeightPx: Int, captionSp: Float) {
            val density = binding.root.context.resources.displayMetrics.density
            val lineHeightPx = captionSp * CAPTION_LINE_SPACING * density
            val topBandPx = apkTileBadgeBinder.reservedTopBandPx(file)
            val captionHeightPx = (tileHeightPx - topBandPx).coerceAtLeast(0)
            val lines = (captionHeightPx / lineHeightPx).toInt()
                .coerceIn(1, MAX_OVERLAY_CAPTION_LINES)
            val showOverlay = AdapterThumbnailLoader.rendersGroupIcon(file) &&
                lines > BELOW_TILE_CAPTION_LINES
            binding.tvFileName.isVisible = !showOverlay
            binding.tvNameOverlay.isVisible = showOverlay
            if (!showOverlay) return

            // FrameLayout lays a centred child out at (parentHeight - childHeight) / 2 + topMargin, so half
            // the reserved band is exactly the offset that re-centres the plate in the strip left below it.
            applyCaptionTopMargin(topBandPx / 2)
            binding.tvNameOverlay.apply {
                text = file.name
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, captionSp)
                maxLines = lines
            }
        }

        /**
         * Assigning layoutParams schedules a layout pass, so the write is guarded - a grid fling rebinds
         * every visible holder and the margin is unchanged for all but the badge-bearing ones.
         */
        private fun applyCaptionTopMargin(topMarginPx: Int) {
            val params = binding.tvNameOverlay.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            if (params.topMargin == topMarginPx) return
            params.topMargin = topMarginPx
            binding.tvNameOverlay.layoutParams = params
        }

        /**
         * Apply only the selection-reflecting visuals (checkbox + card color). Shared by bind()
         * and the PAYLOAD_SELECTION partial rebind so the two paths cannot diverge.
         */
        fun applySelectionVisual(file: MediaFile, selectedPaths: Set<String>) {
            val isSelected = file.path in selectedPaths
            val isFolder = file.isDirectory
            binding.cbSelect.isVisible = BrowseItemOperationPolicy.isSelectable(file)
            if (binding.cbSelect.isVisible) {
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.isChecked = isSelected
                binding.cbSelect.contentDescription = binding.root.context.getString(
                    if (isFolder) R.string.browse_row_folder_checkbox else R.string.browse_row_file_checkbox,
                    file.name,
                )
                binding.cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)
            }
            binding.cvCard.setCardBackgroundColor(
                if (isSelected) {
                    binding.root.context.getColor(R.color.item_selected)
                } else {
                    binding.root.context.getColor(R.color.item_normal)
                }
            )
        }

        private fun loadThumbnail(file: MediaFile) {
            val binarySizePx = (this@MediaFileAdapter.thumbnailSize * binding.root.context.resources.displayMetrics.density).toInt()
            thumbnailLoader.load(binding.ivThumbnail, file, lastLoadedKey, binarySizePx, isListMode = false)
                ?.let { lastLoadedKey = it }
        }
    }

    // No-thumbnail grid ViewHolder: horizontal "plank" - square extension tile on the left,
    // file name filling the rest. The favorite star is intentionally absent here; favorite is
    // reachable through the overflow menu only (S0419).
    inner class GridNoThumbViewHolder(
        private val binding: ItemMediaFileGridNoThumbBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastLoadedKey: String? = null
        private var operationsContainer: android.widget.LinearLayout? = null
        private var btnCopyItem: android.widget.ImageButton? = null
        private var btnMoveItem: android.widget.ImageButton? = null
        private var btnRenameItem: android.widget.ImageButton? = null
        private var btnDeleteItem: android.widget.ImageButton? = null
        private val selectionCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            val file = getItemByPosition() ?: return@OnCheckedChangeListener
            if (BrowseItemOperationPolicy.isSelectable(file)) {
                onSelectionChanged(file, isChecked)
            }
        }

        private fun getItemByPosition(): MediaFile? {
            val position = bindingAdapterPosition
            return if (position != RecyclerView.NO_POSITION) {
                this@MediaFileAdapter.getItem(position)
            } else {
                null
            }
        }

        private fun cubeSizePx(): Int {
            val cubeDp = if (useCompactElements) 24 else 48
            return (cubeDp * binding.root.context.resources.displayMetrics.density).toInt()
        }

        private fun ensureOperationsInflated() {
            if (operationsContainer != null) return
            val inflated = binding.stubOperations.inflate()
            operationsContainer = inflated as? android.widget.LinearLayout
            btnCopyItem = inflated.findViewById(R.id.btnCopyItem)
            btnMoveItem = inflated.findViewById(R.id.btnMoveItem)
            btnRenameItem = inflated.findViewById(R.id.btnRenameItem)
            btnDeleteItem = inflated.findViewById(R.id.btnDeleteItem)
            val getFile: () -> MediaFile? = ::getItemByPosition
            btnCopyItem?.bindFileClick(getFile, onCopyClick)
            btnMoveItem?.bindFileClick(getFile, onMoveClick)
            btnRenameItem?.bindFileClick(getFile, onRenameClick)
            btnDeleteItem?.bindFileClick(getFile, onDeleteClick)
        }

        init {
            val getFile: () -> MediaFile? = ::getItemByPosition
            binding.ivThumbnail.bindFileTypeClick(getFile)
            binding.root.bindFileTypeClick(getFile)
            binding.root.bindRightClickContextMenu(getFile)
            binding.root.setOnLongClickListener {
                val file = getFile() ?: return@setOnLongClickListener false
                onFileLongClick(file)
                true
            }
            binding.ivThumbnail.setOnLongClickListener {
                val file = getFile() ?: return@setOnLongClickListener false
                onFileLongClick(file)
                true
            }
            binding.cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)
            binding.cbSelect.setOnLongClickListener {
                val file = getFile() ?: return@setOnLongClickListener false
                if (BrowseItemOperationPolicy.isSelectable(file) && !binding.cbSelect.isChecked) {
                    onSelectionRangeRequested(file)
                }
                true
            }
            binding.btnOverflowMenu.setOnClickListener {
                val file = getFile() ?: return@setOnClickListener
                onOverflowMenuClick(file, it)
            }
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
        }

        fun clearImage() {
            val context = binding.ivThumbnail.context
            if (context is android.app.Activity && context.isDestroyed) {
                lastLoadedKey = null
                return
            }
            try {
                Glide.with(context).clear(binding.ivThumbnail)
            } catch (e: IllegalArgumentException) {
                Timber.w("Failed to clear Glide request: ${e.message}")
            }
            lastLoadedKey = null
        }

        fun loadThumbnailOnly(file: MediaFile) {
            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"
            if (lastLoadedKey == newKey) return
            thumbnailLoader.load(binding.ivThumbnail, file, lastLoadedKey, cubeSizePx(), isListMode = false)
            lastLoadedKey = newKey
        }

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            binding.apply {
                val isFolder = file.isDirectory

                // Checkbox visibility/state + selection card color (shared with PAYLOAD_SELECTION partial rebind)
                applySelectionVisual(file, selectedPaths)

                // Keep the extension tile square (sized to the cell height); it never stretches
                // across the doubled cell width - the name takes the remaining horizontal space.
                val sizePx = cubeSizePx()
                flThumbnailContainer.layoutParams?.let { lp ->
                    if (lp.width != sizePx || lp.height != sizePx) {
                        lp.width = sizePx
                        lp.height = sizePx
                        flThumbnailContainer.layoutParams = lp
                    }
                }

                tvFileName.text = file.name
                tvFileName.setTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    if (useCompactElements) 11f else 13f
                )

                if (isFolder) {
                    ivThumbnail.setImageResource(R.drawable.ic_folder)
                    ivThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    ivThumbnail.setBackgroundColor(Color.TRANSPARENT)
                    ivThumbnail.imageTintList = null
                    ivThumbnail.colorFilter = null
                } else if (!skipInitialThumbnailLoad) {
                    loadThumbnail(file)
                }

                // Setup operation buttons with visibility
                val useOverflow = fileOpsInOverflowMenu
                binding.btnOverflowMenu.isVisible = useOverflow
                if (!useOverflow) {
                    ensureOperationsInflated()
                    operationsContainer?.isVisible = !hideGridActionButtons
                    btnCopyItem?.isVisible = !hideGridActionButtons
                    btnMoveItem?.isVisible = isWritable && !hideGridActionButtons
                    btnRenameItem?.isVisible = isWritable && !hideGridActionButtons
                    btnDeleteItem?.isVisible = isWritable && !hideGridActionButtons
                } else {
                    operationsContainer?.isVisible = false
                }

                // Flavor-specific tile chrome must bind after the holder's own visibility state is stable.
                apkTileBadgeBinder.bind(root, file)
            }
        }

        /**
         * Apply only the selection-reflecting visuals (checkbox + card color). Shared by bind()
         * and the PAYLOAD_SELECTION partial rebind so the two paths cannot diverge.
         */
        fun applySelectionVisual(file: MediaFile, selectedPaths: Set<String>) {
            val isSelected = file.path in selectedPaths
            val isFolder = file.isDirectory
            binding.cbSelect.isVisible = BrowseItemOperationPolicy.isSelectable(file)
            if (binding.cbSelect.isVisible) {
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.isChecked = isSelected
                binding.cbSelect.contentDescription = binding.root.context.getString(
                    if (isFolder) R.string.browse_row_folder_checkbox else R.string.browse_row_file_checkbox,
                    file.name,
                )
                binding.cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)
            }
            binding.cvCard.setCardBackgroundColor(
                if (isSelected) {
                    binding.root.context.getColor(R.color.item_selected)
                } else {
                    binding.root.context.getColor(R.color.item_normal)
                }
            )
        }

        private fun loadThumbnail(file: MediaFile) {
            thumbnailLoader.load(binding.ivThumbnail, file, lastLoadedKey, cubeSizePx(), isListMode = false)
                ?.let { lastLoadedKey = it }
        }
    }

}
