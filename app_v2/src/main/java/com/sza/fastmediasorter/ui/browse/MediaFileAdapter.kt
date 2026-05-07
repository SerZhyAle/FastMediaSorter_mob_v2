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
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.Priority
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.util.AudioMetadataLoader
import com.sza.fastmediasorter.core.util.formatMediaDuration
import com.sza.fastmediasorter.core.util.HeifSupportUtils
import com.sza.fastmediasorter.core.util.PathUtils
import timber.log.Timber
import com.sza.fastmediasorter.databinding.ItemMediaFileBinding
import com.sza.fastmediasorter.databinding.ItemMediaFileGridBinding
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.cloud.glide.GoogleDriveThumbnailData
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.core.util.MemoryTier
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.util.BinaryFileThumbnailGenerator
import com.sza.fastmediasorter.util.ExtensionThumbnailGenerator
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
    private val onFolderClick: (MediaFile) -> Unit = {}, // Callback for folder navigation
    private val onBinaryFileClick: (MediaFile) -> Unit = {}, // Callback for binary files (Task 6)
    private var isGridMode: Boolean = false,
    private var thumbnailSize: Int = 96, // Default size in dp
    private var useCompactElements: Boolean = false, // Global 0.5x scaling mode
    private val getShowVideoThumbnails: () -> Boolean = { false }, // Callback to get current setting
    private val getShowPdfThumbnails: () -> Boolean = { false }, // Callback to get PDF thumbnail setting
    private var disableThumbnails: Boolean = false // Skip thumbnail loading, show extension icons only
) : ListAdapter<MediaFile, RecyclerView.ViewHolder>(MediaFileDiffCallback()) {

    private var selectedPaths = setOf<String>()
    private var credentialsId: String? = null // Credentials ID for network files
    private var hasDestinations: Boolean = false
    private var isWritable: Boolean = false
    private var refreshVersion: Int = 0
    private var skipInitialThumbnailLoad = false // Control initial thumbnail loading
    private var showFavoriteButton: Boolean = true // Show/hide favorite button based on settings
    private var hideGridActionButtons: Boolean = false // Hide quick action buttons in grid mode
    private var isAudioOnlyMode: Boolean = false

    private val thumbnailLoader = AdapterThumbnailLoader(
        getIsScrolling = { isScrolling },
        getDisableThumbnails = { disableThumbnails },
        getRefreshVersion = { refreshVersion },
        getCredentialsId = { credentialsId },
        getShowVideoThumbnails = getShowVideoThumbnails,
        getShowPdfThumbnails = getShowPdfThumbnails,
        getBinaryGenerator = { binaryThumbnailGenerator }
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

    /** Called by ItemTouchHelper.onMove — reorders the shadow list and notifies RecyclerView. */
    fun moveItem(from: Int, to: Int) {
        if (dragController.moveItem(from, to)) notifyItemMoved(from, to)
    }

    /** Returns file paths in current drag-visual order (call from clearView). */
    fun getOrderedPaths(): List<String> = dragController.getOrderedPaths()

    // ─────────────────────────────────────────────────────────────────────────

    // Inline audio player state (adapter-level, not part of MediaFile model)
    private var inlinePlayerState: InlinePlayerState = InlinePlayerState()

    /**
     * Update inline playback state and partially rebind only affected items.
     * Uses PAYLOAD_PLAYBACK_STATE to avoid full rebind (thumbnail stays intact).
     */
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
    
    init {
        Timber.i("=== MediaFileAdapter CREATED with refreshVersion=$refreshVersion ===")
    }
    
    fun setBinaryThumbnailGenerator(generator: BinaryFileThumbnailGenerator) {
        binaryThumbnailGenerator = generator
    }

    fun setAudioMetadataLoader(loader: AudioMetadataLoader) {
        audioMetadataLoader = loader
    }

    /**
     * Called after scrolling stops to load audio metadata for visible network audio files.
     * Mirrors [loadVisibleThumbnails] pattern. Triggers [AudioMetadataLoader] for each
     * qualifying item; callbacks update the ViewHolder via [PAYLOAD_AUDIO_METADATA].
     */
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
                // Callback on main thread — tell RecyclerView to rebind text only
                if (position in 0 until itemCount && getItem(position).path == file.path) {
                    notifyItemChanged(position, PAYLOAD_AUDIO_METADATA)
                }
            }
        }
    }

    private fun shouldDisableDocumentPreviews(context: android.content.Context): Boolean {
        if (disableDocumentPreviewsOnLowMemory == null) {
            disableDocumentPreviewsOnLowMemory = MemoryTier.detect(context) == MemoryTier.LOW
            Timber.i("MediaFileAdapter: disableDocumentPreviewsOnLowMemory=$disableDocumentPreviewsOnLowMemory")
        }
        return disableDocumentPreviewsOnLowMemory == true
    }
    
    fun incrementRefreshVersion() {
        refreshVersion++
        Timber.i("*** CACHE INVALIDATION *** refreshVersion incremented to $refreshVersion (will invalidate ALL cached thumbnails)")
    }
    
    /**
     * Set scrolling state to skip thumbnail loading during fast scroll.
     * Call setScrolling(true) when scroll starts, setScrolling(false) when scroll ends.
     */
    fun setScrolling(scrolling: Boolean) {
        if (isScrolling != scrolling) {
            isScrolling = scrolling
            Timber.d("MediaFileAdapter: isScrolling=$isScrolling (caller: ${Thread.currentThread().stackTrace[3].methodName})")
        }
    }
    
    /**
     * Called after scrolling stops to load thumbnails for currently visible items.
     * Uses notifyItemRangeChanged with LOAD_THUMBNAILS payload.
     */
    fun loadVisibleThumbnails(firstVisiblePos: Int, lastVisiblePos: Int) {
        if (firstVisiblePos < 0 || lastVisiblePos < 0 || firstVisiblePos > lastVisiblePos) return
        val count = (lastVisiblePos - firstVisiblePos + 1).coerceAtMost(itemCount - firstVisiblePos)
        if (count > 0) {
            Timber.d("MediaFileAdapter: Loading visible thumbnails [$firstVisiblePos..$lastVisiblePos] count=$count")
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
    
    /**
     * Enable/disable initial thumbnail loading in bind().
     * When true, thumbnails are loaded only via LOAD_THUMBNAILS payload.
     */
    fun setSkipInitialThumbnailLoad(skip: Boolean) {
        Timber.d("MediaFileAdapter: setSkipInitialThumbnailLoad($skip)")
        skipInitialThumbnailLoad = skip
    }
    
    fun getSkipInitialThumbnailLoad(): Boolean = skipInitialThumbnailLoad

    /**
     * Returns [file] enriched with AudioMetadataLoader's in-memory cache, if available.
     * This is a fast ConcurrentHashMap lookup — safe to call on every bind.
     */
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
        private const val PAYLOAD_VIEW_MODE_CHANGE = "view_mode_change"
        private const val PAYLOAD_PLAYBACK_STATE = "playback_state"
        private const val PAYLOAD_AUDIO_METADATA = MediaFileDiffCallback.PAYLOAD_AUDIO_METADATA
        private const val AUDIO_ONLY_THUMBNAIL_DP = 48
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
                Timber.d("Thumbnail size changed to ${iconSize}dp, cache will be regenerated at new size")
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
                    notifyItemChanged(index)
                }
            }
            return
        }
        
        // If selection was added/changed
        currentList.forEachIndexed { index, file ->
            val wasSelected = file.path in oldSelected
            val isSelected = file.path in paths
            if (wasSelected != isSelected) {
                notifyItemChanged(index)
            }
        }
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
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
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val file = getItem(position)
        Timber.v("onBindViewHolder WITH PAYLOADS: position=$position, file=${file.name}, payloads=$payloads, isEmpty=${payloads.isEmpty()}, skipFlag=$skipInitialThumbnailLoad")
        
        if (payloads.isEmpty()) {
            // Standard full bind
            Timber.v("onBindViewHolder: payloads EMPTY, calling super (full bind) for ${file.name}")
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Handle multiple payloads - process each one
            if (payloads.contains("LOAD_THUMBNAILS")) {
                // For audio/text files: load extension bitmap (fast path in loadThumbnail handles this)
                // For other types: load full thumbnail via Glide
                Timber.v("onBindViewHolder: LOAD_THUMBNAILS payload detected for ${file.name}, calling loadThumbnailOnly")
                when (holder) {
                    is ListViewHolder -> {
                        Timber.v(">>> Calling ListViewHolder.loadThumbnailOnly for ${file.name}")
                        holder.loadThumbnailOnly(file)
                    }
                    is GridViewHolder -> {
                        Timber.v(">>> Calling GridViewHolder.loadThumbnailOnly for ${file.name}")
                        holder.loadThumbnailOnly(file)
                    }
                }
            }
            if (payloads.contains("FAVORITE_CHANGED")) {
                // Partial bind: only update favorite icon
                Timber.v("onBindViewHolder: FAVORITE_CHANGED payload detected for ${file.name}, updating icon only")
                when (holder) {
                    is ListViewHolder -> {
                        holder.itemView.findViewById<android.widget.ImageButton>(R.id.btnFavorite)?.setImageResource(
                            if (file.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                        )
                    }
                    is GridViewHolder -> {
                        holder.itemView.findViewById<android.widget.ImageButton>(R.id.btnFavorite)?.setImageResource(
                            if (file.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                        )
                    }
                }
            }
            if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
                // Partial bind: only update inline play button state
                if (holder is ListViewHolder) {
                    holder.updatePlaybackState(file)
                }
            }
            if (payloads.contains(PAYLOAD_AUDIO_METADATA)) {
                // Partial bind: only update text labels with enriched audio metadata.
                // For network files the ListAdapter list may not contain enriched data yet,
                // so check AudioMetadataLoader's in-memory cache first.
                val displayFile = resolveAudioMetadata(file)
                if (holder is ListViewHolder) {
                    holder.updateAudioMetadataText(displayFile)
                }
            }
            // If no known payloads were handled, fall back to super
            if (!payloads.contains("LOAD_THUMBNAILS") && !payloads.contains("FAVORITE_CHANGED") && !payloads.contains(PAYLOAD_PLAYBACK_STATE) && !payloads.contains(PAYLOAD_AUDIO_METADATA)) {
                Timber.v("onBindViewHolder: UNKNOWN payloads=$payloads, calling super")
                super.onBindViewHolder(holder, position, payloads)
            }
        }
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
        // Explicitly clear Glide requests when view is recycled to free up
        // ConnectionThrottleManager slots immediately. This is critical for
        // network resources where concurrency is limited.
        when (holder) {
            is ListViewHolder -> {
                holder.clearImage()
                holder.stopPlaybackAnimations() // Cancel inline playback animations when view is recycled
            }
            is GridViewHolder -> holder.clearImage()
        }
    }

    inner class ListViewHolder(
        private val binding: ItemMediaFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var lastLoadedKey: String? = null
        private val playbackAnimator = InlinePlaybackAnimator(binding.btnPlayInline)
        private val selectionCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            val file = getItemByPosition() ?: return@OnCheckedChangeListener
            if (!file.isDirectory) {
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
            binding.ivThumbnail.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                if (file.isDirectory) {
                    onFolderClick(file)
                } else if (file.type.isBinaryFile()) {
                    onBinaryFileClick(file)
                } else {
                    onFileClick(file)
                }
            }

            binding.root.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                if (file.isDirectory) {
                    onFolderClick(file)
                } else if (file.type.isBinaryFile()) {
                    onBinaryFileClick(file)
                } else {
                    onFileClick(file)
                }
            }

            binding.root.setOnGenericMotionListener { view, event ->
                if (event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                    event.buttonState == MotionEvent.BUTTON_SECONDARY
                ) {
                    val file = getItemByPosition() ?: return@setOnGenericMotionListener false
                    Timber.d("Right-click on ${file.name}")
                    // Right-click must open the context menu, not reuse range-selection semantics.
                    onContextMenuRequest(view, file)
                    return@setOnGenericMotionListener true
                }
                false
            }

            binding.root.setOnLongClickListener {
                val file = getItemByPosition() ?: return@setOnLongClickListener false
                if (!file.isDirectory) {
                    onFileLongClick(file)
                }
                true
            }

            binding.cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)

            binding.btnFavorite.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onFavoriteClick(file)
            }

            binding.btnCopyItem.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onCopyClick(file)
            }

            binding.btnMoveItem.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onMoveClick(file)
            }

            binding.btnRenameItem.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onRenameClick(file)
            }

            binding.btnDeleteItem.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onDeleteClick(file)
            }

            binding.btnPlayInline.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onPlayClick(file)
            }

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

        /**
         * Update only the inline play button icon/animation for this item.
         * Called via PAYLOAD_PLAYBACK_STATE partial rebind.
         */
        fun updatePlaybackState(file: MediaFile) {
            val state = this@MediaFileAdapter.inlinePlayerState
            val isThisItem = state.playingPath == file.path
            val isDownloading = state.downloadingPath == file.path
            val shouldShow = file.type == MediaType.AUDIO && !file.isDirectory && !(isGridMode && hideGridActionButtons)
            binding.btnPlayInline.isVisible = shouldShow
            if (!shouldShow) return
            if (!isAudioOnlyMode && shouldShow) {
                Timber.d("S0105: showing inline play button for audio file '${file.name}' in mixed resource")
            }

            // In audio-only mode update info line with detail / cache progress; in other modes leave tvFileInfo as-is
            if (isAudioOnlyMode) {
                val baseInfo = AdapterFileInfoFormatter.buildAudioDetailLine(file)
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

        /**
         * Update only text labels when audio metadata (artist/album/title/duration) is enriched.
         * Called via PAYLOAD_AUDIO_METADATA partial rebind — avoids thumbnail reload.
         */
        fun updateAudioMetadataText(file: MediaFile) {
            val audioOnlyFile = isAudioOnlyMode && !file.isDirectory
            if (audioOnlyFile) {
                binding.tvFileName.text = AdapterFileInfoFormatter.buildAudioDisplayName(file)
                binding.tvFileInfo.text = AdapterFileInfoFormatter.buildAudioDetailLine(file)
            } else {
                // Non audio-only mode: filename stays, but info line may include duration
                binding.tvFileInfo.text = AdapterFileInfoFormatter.buildFileInfo(file)
            }
        }

        fun stopPlaybackAnimations() = playbackAnimator.stopAll()

        private fun applyInlineHighlight(active: Boolean) {
            binding.root.foreground = if (active) {
                ContextCompat.getDrawable(binding.root.context, R.drawable.item_inline_playing_border)
            } else {
                null
            }
        }

        fun loadThumbnailOnly(file: MediaFile) {
            // In audio-only mode, ivThumbnail is GONE — skip entirely
            if (isAudioOnlyMode && !file.isDirectory) return

            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"

            Timber.v("=== CACHE_KEY_DEBUG: loadThumbnailOnly called ===")
            Timber.v("  File: ${file.name} | NewKey: ${newKey.take(80)} | LastKey: ${lastLoadedKey?.take(80)}")

            if (lastLoadedKey == newKey) {
                Timber.v("  Result: SKIPPED - key matches")
                return
            }
            Timber.v("  Result: LOADING")
            thumbnailLoader.load(binding.ivThumbnail, file, lastLoadedKey, isListMode = true)
            // Always update key so AUDIO/TEXT/Binary fast-paths don't re-trigger
            lastLoadedKey = newKey
        }

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            // Note: Glide automatically cancels previous request when load() is called on same ImageView
            
            Timber.v("ListViewHolder.bind: START file=${file.name}, isDirectory=${file.isDirectory}, childCount=${file.childCount}")
            
            binding.apply {
                val isSelected = file.path in selectedPaths
                val isFolder = file.isDirectory
                
                // In audio-only mode, hide thumbnail entirely for files (not folders)
                // so file name/details shift to the left and take its space
                val audioOnlyFile = isAudioOnlyMode && !isFolder
                ivThumbnail.visibility = if (audioOnlyFile) android.view.View.GONE else android.view.View.VISIBLE

                if (!audioOnlyFile) {
                    // Apply thumbnail size from settings for list mode (halved if compact mode enabled)
                    val effectiveBaseSize = if (useCompactElements) thumbnailSize / 2 else thumbnailSize
                    val thumbnailSizePx = if (this@MediaFileAdapter.disableThumbnails) {
                        val dSize = if (useCompactElements) 20 else 32
                        (dSize * root.resources.displayMetrics.density).toInt()
                    } else {
                        (effectiveBaseSize * root.resources.displayMetrics.density).toInt()
                    }
                    ivThumbnail.layoutParams.width = thumbnailSizePx
                    ivThumbnail.layoutParams.height = thumbnailSizePx
                }

                if (useCompactElements) {
                    tvFileName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    tvFileInfo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f)
                    val p8 = (8 * root.resources.displayMetrics.density).toInt()
                    val p4 = (4 * root.resources.displayMetrics.density).toInt()
                    root.setPadding(p8, p4, p8, p4)
                } else {
                    tvFileName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    tvFileInfo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    val p16 = (16 * root.resources.displayMetrics.density).toInt()
                    val p12 = (12 * root.resources.displayMetrics.density).toInt()
                    root.setPadding(p16, p12, p16, p12)
                }

                // Scale action buttons: compact = 24dp (0.75× normal), normal = 32dp
                // 16dp was too small — icon area was only ~4dp with 6dp XML padding
                val btnSizePx = if (useCompactElements) {
                    (24 * root.resources.displayMetrics.density).toInt()
                } else {
                    (32 * root.resources.displayMetrics.density).toInt()
                }
                for (btn in listOf(btnFavorite, btnCopyItem, btnMoveItem, btnRenameItem, btnDeleteItem, btnPlayInline)) {
                    btn.layoutParams.width = btnSizePx
                    btn.layoutParams.height = btnSizePx
                }

                // Hide checkbox for folders (folders can't be selected)
                cbSelect.isVisible = !isFolder
                if (!isFolder) {
                    cbSelect.setOnCheckedChangeListener(null)
                    cbSelect.isChecked = isSelected
                    cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)
                }
                
                // Highlight selected items
                // Highlight selected items using background color
                val backgroundColor = when {
                    isSelected -> root.context.getColor(com.sza.fastmediasorter.R.color.item_selected)
                    bindingAdapterPosition % 2 != 0 -> root.context.getColor(com.sza.fastmediasorter.R.color.item_alternate)
                    else -> root.context.getColor(com.sza.fastmediasorter.R.color.item_normal)
                }
                root.setBackgroundColor(backgroundColor)
                
                // In audio-only mode: top line = Artist - Title, bottom = filename + size + duration
                // For network audio files, check AudioMetadataLoader cache for enriched data.
                val displayFile = resolveAudioMetadata(file)
                if (audioOnlyFile) {
                    tvFileName.text = AdapterFileInfoFormatter.buildAudioDisplayName(displayFile)
                    tvFileInfo.text = AdapterFileInfoFormatter.buildAudioDetailLine(displayFile)
                } else {
                    tvFileName.text = file.name
                    tvFileInfo.text = AdapterFileInfoFormatter.buildFileInfo(file)
                }

                // Load thumbnail or folder icon
                if (isFolder) {
                    // ALWAYS show folder icon, regardless of skipInitialThumbnailLoad
                    Timber.v("ListViewHolder.bind: Setting folder icon for ${file.name}, isDirectory=${file.isDirectory}")
                    ivThumbnail.setImageResource(R.drawable.ic_folder)
                    ivThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    ivThumbnail.setBackgroundColor(Color.TRANSPARENT)
                    ivThumbnail.imageTintList = null // Clear any tint from previous thumbnails
                    ivThumbnail.colorFilter = null
                } else if (!audioOnlyFile) {
                    // For files in non-audio-only mode, respect skipInitialThumbnailLoad flag
                    if (!skipInitialThumbnailLoad) {
                        loadThumbnail(file)
                    } else {
                        Timber.v("ListViewHolder.bind: SKIPPED initial thumbnail load for ${file.name} (waiting for payload)")
                    }
                }
                // audioOnlyFile: thumbnail is GONE, nothing to load
                
                // Favorite button (hide for folders)
                btnFavorite.isVisible = showFavoriteButton && !isFolder
                btnFavorite.setImageResource(
                    if (file.isFavorite) R.drawable.ic_star_filled
                    else R.drawable.ic_star_outline
                )

                // Setup operation buttons with visibility checks
                // HIDE buttons if: (It's Grid Mode AND HideGridActions is ON) OR it's a folder
                val shouldHideActions = (isGridMode && hideGridActionButtons) || isFolder
                
                btnCopyItem.isVisible = !shouldHideActions
                
                btnMoveItem.isVisible = isWritable && !shouldHideActions
                
                btnRenameItem.isVisible = isWritable && !shouldHideActions
                
                btnDeleteItem.isVisible = isWritable && !shouldHideActions

                // Inline play button: visible for any audio file, suppressed by hideGridActionButtons in grid mode
                val showPlayButton = file.type == MediaType.AUDIO && !isFolder && !(isGridMode && hideGridActionButtons)
                if (showPlayButton) {
                    updatePlaybackState(file)
                } else {
                    btnPlayInline.isVisible = false
                    btnPlayInline.isEnabled = true
                    applyInlineHighlight(false)
                }

                // Drag handle: visible only in MANUAL sort mode (set via showDragHandles)
                ivDragHandle.isVisible = dragController.showHandles && !isFolder
            }
        }

        private fun loadThumbnail(file: MediaFile) {
            thumbnailLoader.load(binding.ivThumbnail, file, lastLoadedKey, isListMode = true)
                ?.let { lastLoadedKey = it }
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
            if (!file.isDirectory) {
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

            btnCopyItem?.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onCopyClick(file)
            }

            btnMoveItem?.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onMoveClick(file)
            }

            btnRenameItem?.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onRenameClick(file)
            }

            btnDeleteItem?.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onDeleteClick(file)
            }
        }

        init {
            binding.ivThumbnail.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                if (file.isDirectory) {
                    onFolderClick(file)
                } else if (file.type.isBinaryFile()) {
                    onBinaryFileClick(file)
                } else {
                    onFileClick(file)
                }
            }

            binding.root.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                if (file.isDirectory) {
                    onFolderClick(file)
                } else if (file.type.isBinaryFile()) {
                    onBinaryFileClick(file)
                } else {
                    onFileClick(file)
                }
            }

            binding.root.setOnGenericMotionListener { view, event ->
                if (event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                    event.buttonState == MotionEvent.BUTTON_SECONDARY
                ) {
                    val file = getItemByPosition() ?: return@setOnGenericMotionListener false
                    Timber.d("Right-click on ${file.name}")
                    // Right-click must open the context menu, not reuse range-selection semantics.
                    onContextMenuRequest(view, file)
                    return@setOnGenericMotionListener true
                }
                false
            }

            binding.root.setOnLongClickListener {
                val file = getItemByPosition() ?: return@setOnLongClickListener false
                onFileLongClick(file)
                true
            }

            binding.cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)
            binding.cbSelect.setOnLongClickListener {
                val file = getItemByPosition() ?: return@setOnLongClickListener false
                if (!file.isDirectory && !binding.cbSelect.isChecked) {
                    onSelectionRangeRequested(file)
                }
                true
            }

            binding.btnFavorite.setOnClickListener {
                val file = getItemByPosition() ?: return@setOnClickListener
                onFavoriteClick(file)
            }

            // Task 8: Make item focusable for keyboard navigation
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
        }
        
        fun clearImage() {
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
            
            Timber.v("GridViewHolder.bind: START file=${file.name}, isDirectory=${file.isDirectory}, childCount=${file.childCount}")
            
            binding.apply {
                val isSelected = file.path in selectedPaths
                val isFolder = file.isDirectory
                
                // Hide checkbox for folders (folders can't be selected)
                cbSelect.isVisible = !isFolder
                if (!isFolder) {
                    // Setup checkbox
                    cbSelect.setOnCheckedChangeListener(null)
                    cbSelect.isChecked = isSelected
                    cbSelect.setOnCheckedChangeListener(selectionCheckedChangeListener)
                }
                
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
                
                // Highlight selected items
                cvCard.setCardBackgroundColor(
                    if (isSelected) {
                        root.context.getColor(R.color.item_selected)
                    } else {
                        root.context.getColor(R.color.item_normal)
                    }
                )
                
                tvFileName.text = file.name
                if (useCompactElements) {
                    tvFileName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                } else {
                    tvFileName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                }
                
                // Load thumbnail or folder icon
                if (isFolder) {
                    // ALWAYS show folder icon, regardless of skipInitialThumbnailLoad
                    Timber.v("GridViewHolder.bind: Setting folder icon for ${file.name}, isDirectory=${file.isDirectory}")
                    ivThumbnail.setImageResource(R.drawable.ic_folder)
                    ivThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    ivThumbnail.setBackgroundColor(Color.TRANSPARENT)
                    ivThumbnail.imageTintList = null // Clear any tint from previous thumbnails
                    ivThumbnail.colorFilter = null
                } else {
                    // For files, respect skipInitialThumbnailLoad flag
                    if (!skipInitialThumbnailLoad) {
                        loadThumbnail(file)
                    } else {
                        Timber.v("GridViewHolder.bind: SKIPPED initial thumbnail load for ${file.name} (waiting for payload)")
                    }
                }
                
                // Favorite button
                btnFavorite.isVisible = showFavoriteButton
                btnFavorite.setImageResource(
                    if (file.isFavorite) R.drawable.ic_star_filled 
                    else R.drawable.ic_star_outline
                )

                // Setup operation buttons with visibility
                val shouldShowAnyOperation = true // Copy is always available (select folder option)
                if (shouldShowAnyOperation) {
                    ensureOperationsInflated()
                }

                operationsContainer?.isVisible = shouldShowAnyOperation && !hideGridActionButtons
                btnCopyItem?.isVisible = !hideGridActionButtons
                btnMoveItem?.isVisible = isWritable && !hideGridActionButtons
                btnRenameItem?.isVisible = isWritable && !hideGridActionButtons
                btnDeleteItem?.isVisible = isWritable && !hideGridActionButtons
            }
        }
        
        private fun loadThumbnail(file: MediaFile) {
            val binarySizePx = (this@MediaFileAdapter.thumbnailSize * binding.root.context.resources.displayMetrics.density).toInt()
            thumbnailLoader.load(binding.ivThumbnail, file, lastLoadedKey, binarySizePx, isListMode = false)
                ?.let { lastLoadedKey = it }
        }
    }

}





