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
import androidx.recyclerview.widget.DiffUtil
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

    // ── Drag-to-reorder (MANUAL sort mode) ───────────────────────────────────
    interface DragStartListener {
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    }

    private var dragStartListener: DragStartListener? = null
    private var showDragHandles: Boolean = false
    // Shadow copy maintained during drag to track visual order without mutating currentList
    private val dragOrderedList = mutableListOf<com.sza.fastmediasorter.domain.model.MediaFile>()

    fun setDragStartListener(listener: DragStartListener?) {
        dragStartListener = listener
    }

    fun showDragHandles(show: Boolean) {
        if (showDragHandles == show) return
        showDragHandles = show
        if (show) {
            dragOrderedList.clear()
            dragOrderedList.addAll(currentList)
        }
        notifyItemRangeChanged(0, itemCount)
    }

    /** Called by ItemTouchHelper.onMove — reorders the shadow list and notifies RecyclerView. */
    fun moveItem(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= dragOrderedList.size || to >= dragOrderedList.size) return
        if (from < to) {
            for (i in from until to) dragOrderedList.add(i + 1, dragOrderedList.removeAt(i))
        } else {
            for (i in from downTo to + 1) dragOrderedList.add(i - 1, dragOrderedList.removeAt(i))
        }
        notifyItemMoved(from, to)
    }

    /** Returns file paths in current drag-visual order (call from clearView). */
    fun getOrderedPaths(): List<String> = dragOrderedList.map { it.path }.toList()

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
        private const val PAYLOAD_AUDIO_METADATA = "audio_metadata"
        private const val CACHED_THUMBNAIL_SIZE = 300 // Fixed size for cache stability across List/Grid modes
        private const val AUDIO_ONLY_THUMBNAIL_DP = 48
        
        // PDF thumbnail size limits for network resources when "Large PDF Thumbnails" is ENABLED (bytes)
        private const val SMB_PDF_LARGE_MAX_SIZE = 50 * 1024 * 1024L // 50 MB for SMB
        private const val NETWORK_PDF_LARGE_MAX_SIZE = 10 * 1024 * 1024L // 10 MB for SFTP/FTP/Cloud
        
        // PDF thumbnail size limits when "Large PDF Thumbnails" is DISABLED (normal behavior)
        private const val SMB_PDF_NORMAL_MAX_SIZE = 3 * 1024 * 1024L // 3 MB for SMB
        private const val NETWORK_PDF_NORMAL_MAX_SIZE = 1 * 1024 * 1024L // 1 MB for SFTP/FTP/Cloud
        
        // EPUB cover size limits for network resources (same as PDF)
        private const val SMB_EPUB_MAX_SIZE = 50 * 1024 * 1024L // 50 MB for SMB
        private const val NETWORK_EPUB_MAX_SIZE = 10 * 1024 * 1024L // 10 MB for SFTP/FTP/Cloud

        /**
         * Check if GlideException is caused by video decoder failure.
         */
        private fun isVideoDecoderException(e: GlideException?): Boolean {
            if (e == null) return false
            
            var current: Throwable? = e
            var depth = 0
            while (current != null && depth < 10) {
                val msg = current.message?.lowercase() ?: ""
                val className = current.javaClass.simpleName.lowercase()
                
                if (className.contains("videodecoder") ||
                    className.contains("videodecoderexception") ||
                    msg.contains("mediametadataretriever") ||
                    msg.contains("failed to retrieve a frame")) {
                    return true
                }
                
                current = current.cause
                depth++
            }
            return false
        }
            }
        
        /**
         * Apply placeholder style (background color + reduced size for list)
         */
        @Suppress("UNUSED_PARAMETER")
        private fun applyPlaceholderStyle(imageView: android.widget.ImageView, type: MediaType, _isListMode: Boolean = false) {
            val context = imageView.context
            val colorRes = when (type) {
                MediaType.VIDEO -> R.color.thumbnail_video_bg
                MediaType.AUDIO -> R.color.thumbnail_audio_bg
                MediaType.TEXT, MediaType.PDF, MediaType.EPUB -> R.color.thumbnail_doc_bg
                else -> R.color.thumbnail_image_bg
            }
            imageView.setBackgroundColor(ContextCompat.getColor(context, colorRes))
        }
        
        private fun resetThumbnailStyle(imageView: android.widget.ImageView) {
            imageView.background = null
            // Reset scaleType to CENTER_CROP for proper thumbnail display (may have been CENTER_INSIDE for folder icons)
            imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
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
        if (showDragHandles) {
            dragOrderedList.clear()
            dragOrderedList.addAll(currentList)
        }
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
        private var noteAnimator: android.animation.ObjectAnimator? = null
        private var downloadAnimator: android.animation.ObjectAnimator? = null
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

            binding.root.setOnGenericMotionListener { _, event ->
                if (event.buttonState == android.view.MotionEvent.BUTTON_SECONDARY) {
                    val file = getItemByPosition() ?: return@setOnGenericMotionListener false
                    Timber.d("Right-click on ${file.name}")
                    onFileLongClick(file)
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
                    dragStartListener?.onStartDrag(this)
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
            binding.btnPlayInline.isVisible = isAudioOnlyMode
            if (!isAudioOnlyMode) return

            // Use detail line (filename + size + duration) for the info row, not artist/title which is already in tvFileName
            val baseInfo = buildAudioDetailLine(file)
            if (isDownloading) {
                val progress = state.downloadProgressPercent.coerceIn(0, 100)
                binding.tvFileInfo.text = if (progress > 0) "$baseInfo • Cache $progress%" else "$baseInfo • Cache..."
            } else {
                binding.tvFileInfo.text = baseInfo
            }

            binding.btnPlayInline.isEnabled = !isDownloading
            applyInlineHighlight(isThisItem || isDownloading)

            when {
                isDownloading -> {
                    binding.btnPlayInline.setImageResource(android.R.drawable.stat_sys_download)
                    stopNoteAnimation()
                    startDownloadAnimation()
                }
                !isThisItem -> {
                    binding.btnPlayInline.setImageResource(R.drawable.ic_play_inline_outline)
                    stopNoteAnimation()
                    stopDownloadAnimation()
                }
                state.status == PlaybackStatus.PLAYING -> {
                    binding.btnPlayInline.setImageResource(R.drawable.ic_music_note)
                    stopDownloadAnimation()
                    startNoteAnimation()
                }
                state.status == PlaybackStatus.PAUSED -> {
                    binding.btnPlayInline.setImageResource(R.drawable.ic_pause)
                    stopNoteAnimation()
                    stopDownloadAnimation()
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
                binding.tvFileName.text = buildAudioDisplayName(file)
                binding.tvFileInfo.text = buildAudioDetailLine(file)
            } else {
                // Non audio-only mode: filename stays, but info line may include duration
                binding.tvFileInfo.text = buildFileInfo(file)
            }
        }

        private fun startNoteAnimation() {
            if (noteAnimator?.isRunning == true) return
            noteAnimator = android.animation.ObjectAnimator.ofFloat(
                binding.btnPlayInline, "rotation", 0f, 360f
            ).apply {
                duration = 1200
                repeatCount = android.animation.ObjectAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                start()
            }
        }

        private fun stopNoteAnimation() {
            noteAnimator?.cancel()
            noteAnimator = null
            binding.btnPlayInline.rotation = 0f
        }

        private fun startDownloadAnimation() {
            if (downloadAnimator?.isRunning == true) return
            downloadAnimator = android.animation.ObjectAnimator.ofFloat(
                binding.btnPlayInline,
                "alpha",
                1f,
                0.35f,
                1f
            ).apply {
                duration = 900
                repeatCount = android.animation.ObjectAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                start()
            }
        }

        private fun stopDownloadAnimation() {
            downloadAnimator?.cancel()
            downloadAnimator = null
            binding.btnPlayInline.alpha = 1f
        }

        fun stopPlaybackAnimations() {
            stopNoteAnimation()
            stopDownloadAnimation()
            binding.btnPlayInline.rotation = 0f
        }

        private fun applyInlineHighlight(active: Boolean) {
            binding.root.foreground = if (active) {
                ContextCompat.getDrawable(binding.root.context, R.drawable.item_inline_playing_border)
            } else {
                null
            }
        }

        fun loadThumbnailOnly(file: MediaFile) {
            // Partial update: only reload thumbnail (called via payload)
            // In audio-only mode, ivThumbnail is GONE — skip entirely (no bitmap work needed)
            if (isAudioOnlyMode && !file.isDirectory) return

            // Check if we need to reload based on the key (includes refreshVersion)
            // Note: credentialsId removed from key - it's session-specific and shouldn't affect cache
            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"
            
            Timber.v("=== CACHE_KEY_DEBUG: loadThumbnailOnly called ===")
            Timber.v("  File: ${file.name}")
            Timber.v("  New Key: ${newKey.take(120)}")
            Timber.v("  Last Key: ${lastLoadedKey?.take(120)}")
            Timber.v("  refreshVersion: $refreshVersion")
            
            // Only skip reload if the key is exactly the same (meaning thumbnail already loaded for this version)
            if (lastLoadedKey == newKey) {
                Timber.v("  Result: SKIPPED - key matches (thumbnail already loaded)")
                return
            }
            
            Timber.v("  Result: LOADING - key mismatch (will call loadThumbnail)")
            loadThumbnail(file)
            // Update key so AUDIO/TEXT/Binary fast-paths (which return early without setting the key)
            // don't re-trigger on the next LOAD_THUMBNAILS payload after the same recycle.
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
                    tvFileName.text = buildAudioDisplayName(displayFile)
                    tvFileInfo.text = buildAudioDetailLine(displayFile)
                } else {
                    tvFileName.text = file.name
                    tvFileInfo.text = buildFileInfo(file)
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

                // Inline play button: visible only in Audio Only mode, not for folders
                if (isAudioOnlyMode && !isFolder) {
                    updatePlaybackState(file)
                } else {
                    btnPlayInline.isVisible = false
                    btnPlayInline.isEnabled = true
                    tvFileInfo.text = buildFileInfo(file)
                    applyInlineHighlight(false)
                }

                // Drag handle: visible only in MANUAL sort mode (set via showDragHandles)
                ivDragHandle.isVisible = this@MediaFileAdapter.showDragHandles && !isFolder
            }
        }

        private fun loadThumbnail(file: MediaFile) {
            // Skip thumbnail loading during fast scroll
            if (isScrolling) {
                Timber.v("loadThumbnail: SKIPPED during scroll for ${file.name}")
                return
            }
            
            // AUDIO: show extension bitmap only (no metadata/cover art/internet in Browse).
            // Cover art is loaded exclusively in the Player (ImageLoadingManager.loadAudioCoverArt).
            if (file.type == MediaType.AUDIO) {
                val extension = file.name.substringAfterLast('.', "").uppercase()
                binding.ivThumbnail.setImageBitmap(createExtensionBitmap(extension))
                applyPlaceholderStyle(binding.ivThumbnail, file.type, true)
                Timber.v("loadThumbnail: AUDIO extension bitmap for ${file.name} ($extension)")
                return
            }

            // TEXT: show extension bitmap (no network/disk loading needed)
            if (file.type == MediaType.TEXT) {
                val extension = file.name.substringAfterLast('.', "").uppercase()
                binding.ivThumbnail.setImageBitmap(createExtensionBitmap(extension))
                applyPlaceholderStyle(binding.ivThumbnail, file.type, true)
                return
            }
            
            // Task 6: Binary files - generate custom thumbnail
            if (file.type.isBinaryFile()) {
                val extension = file.name.substringAfterLast('.', "").ifEmpty { "BIN" }
                binaryThumbnailGenerator?.let { generator ->
                    val thumbnailSize = (this@MediaFileAdapter.thumbnailSize * binding.root.resources.displayMetrics.density).toInt()
                    val thumbnail = generator.generateThumbnail(extension, file.type, thumbnailSize)
                    binding.ivThumbnail.setImageBitmap(thumbnail)
                    resetThumbnailStyle(binding.ivThumbnail)
                    Timber.v("Binary file thumbnail generated for ${file.name}")
                } ?: run {
                    val ext = extension.uppercase()
                    binding.ivThumbnail.setImageBitmap(createExtensionBitmap(ext))
                    applyPlaceholderStyle(binding.ivThumbnail, file.type, true)
                }
                return
            }
            
            // Don't load thumbnails for directories - they use folder icon
            if (file.isDirectory) {
                Timber.v("loadThumbnail: SKIPPED for directory ${file.name}")
                return
            }
            
            Timber.v("loadThumbnail: START file=${file.name}")
            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"
            if (lastLoadedKey == newKey) {
                return
            }
            lastLoadedKey = newKey

            val imageView = binding.ivThumbnail
            val context = imageView.context
            val generatedPlaceholder = createPlaceholderDrawable(file)
            
            // Reset scaleType to CENTER_CROP (may have been CENTER_INSIDE for folder icons due to view recycling)
            imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            
            // If thumbnails disabled, show only extension-based icons (no Glide loading)
            if (this@MediaFileAdapter.disableThumbnails) {
                Timber.v("THUMBNAIL_DEBUG: Skipping thumbnail load for ${file.name} - disableThumbnails=true")
                when (file.type) {
                    MediaType.IMAGE -> {
                        showGeneratedPlaceholder(imageView, file)
                    }
                    MediaType.VIDEO -> {
                        showGeneratedPlaceholder(imageView, file)
                    }
                    MediaType.AUDIO -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(createExtensionBitmap(extension))
                        applyPlaceholderStyle(imageView, file.type, true)
                    }
                    MediaType.GIF -> {
                        showGeneratedPlaceholder(imageView, file)
                    }
                    MediaType.TEXT -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(createExtensionBitmap(extension))
                        applyPlaceholderStyle(imageView, file.type, true)
                    }
                    MediaType.PDF -> {
                        showGeneratedPlaceholder(imageView, file)
                    }
                    MediaType.EPUB -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(createExtensionBitmap(extension))
                        applyPlaceholderStyle(imageView, file.type, true)
                    }
                    MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                    MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(binaryThumbnailGenerator?.generateThumbnail(extension, file.type))
                        applyPlaceholderStyle(imageView, file.type, true)
                    }
                }
                return
            }
            
            // Check if this is a cloud path (cloud://)
            val isCloudPath = file.path.startsWith("cloud://")
            // Check if this is a network path (SMB/SFTP/FTP)
            val isNetworkPath = file.path.startsWith("smb://") || file.path.startsWith("sftp://") || file.path.startsWith("ftp://")

            // Check if file exists before loading thumbnail
            if (!isNetworkPath && !isCloudPath) {
                val fileExists = if (file.path.startsWith("content://")) {
                    // For SAF URIs, check using DocumentFile
                    try {
                        val uri = Uri.parse(file.path)
                        val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                        docFile?.exists() == true
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to check SAF URI existence: ${file.path}")
                        false
                    }
                } else {
                    // For regular file paths
                    File(file.path).exists()
                }
                
                if (!fileExists) {
                    Timber.w("File no longer exists: ${file.path}")
                    // Show error placeholder for deleted files
                    when (file.type) {
                        MediaType.IMAGE, MediaType.GIF -> {
                            showGeneratedPlaceholder(imageView, file)
                        }
                        MediaType.VIDEO -> {
                            showGeneratedPlaceholder(imageView, file)
                        }
                        MediaType.AUDIO -> {
                            val extension = file.name.substringAfterLast('.', "").uppercase()
                            imageView.setImageBitmap(createExtensionBitmap(extension))
                            applyPlaceholderStyle(imageView, file.type, true)
                        }
                        MediaType.TEXT -> {
                            val extension = file.name.substringAfterLast('.', "").uppercase()
                            imageView.setImageBitmap(createExtensionBitmap(extension))
                            applyPlaceholderStyle(imageView, file.type, true)
                        }
                        MediaType.PDF -> {
                            showGeneratedPlaceholder(imageView, file)
                        }
                        MediaType.EPUB -> {
                            val extension = file.name.substringAfterLast('.', "").uppercase()
                            imageView.setImageBitmap(createExtensionBitmap(extension))
                            applyPlaceholderStyle(imageView, file.type, true)
                        }
                        MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                        MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                            val extension = file.name.substringAfterLast('.', "").uppercase()
                            imageView.setImageBitmap(binaryThumbnailGenerator?.generateThumbnail(extension, file.type))
                            applyPlaceholderStyle(imageView, file.type, true)
                        }
                    }
                    return
                }
            }
            
            // Don't apply placeholder style initially - only for fallbacks
            // This keeps normal 64dp size for successful thumbnail loads
            
            when (file.type) {
                MediaType.TEXT -> {
                    val extension = file.name.substringAfterLast('.', "").uppercase()
                    imageView.setImageBitmap(createExtensionBitmap(extension))
                    applyPlaceholderStyle(imageView, file.type, true)
                }
                MediaType.EPUB -> {
                    // Load EPUB cover using Glide (EpubCoverDecoder registered in GlideAppModule)
                    if (!isCloudPath && !isNetworkPath) {
                        // Local EPUB - Glide will use EpubCoverDecoder automatically for .epub files
                        val epubFile = File(file.path)
                        if (epubFile.exists()) {
                            Glide.with(context)
                                .asBitmap()
                                .load(epubFile)
                                .signature(ObjectKey("${file.path}_${file.size}"))
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache extracted cover
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .into(imageView)
                        } else {
                            showGeneratedPlaceholder(imageView, file)
                        }
                    } else if (isNetworkPath) {
                        // Network EPUB (SMB/SFTP/FTP) - check size limits (same as PDF logic)
                        val isSmbPath = file.path.startsWith("smb://")
                        val maxSize = if (isSmbPath) SMB_EPUB_MAX_SIZE else NETWORK_EPUB_MAX_SIZE
                        
                        if (file.size > maxSize) {
                            // File too large - show placeholder without downloading
                            showGeneratedPlaceholder(imageView, file)
                        } else {
                            // Check if thumbnail loading previously failed for this file
                            if (NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                                Timber.v("Skipping EPUB cover load for ${file.name} (cached as failed)")
                                showGeneratedPlaceholder(imageView, file)
                            } else {
                                // File size OK - use NetworkEpubCoverLoader
                                val networkData = NetworkFileData(
                                    path = file.path,
                                    size = file.size,
                                    credentialsId = credentialsId
                                )
                                val cacheKey = ObjectKey("${file.path}_${file.size}")
                                Glide.with(context)
                                    .asBitmap()
                                    .load(networkData)
                                    .signature(cacheKey)
                                    .listener(object : RequestListener<Bitmap> {
                                        override fun onLoadFailed(
                                            e: GlideException?,
                                            model: Any?,
                                            target: Target<Bitmap>,
                                            isFirstResource: Boolean
                                        ): Boolean {
                                            if (e != null) {
                                                Timber.w("EPUB cover load failed: ${file.name}, ${e.message}")
                                                NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
                                            }
                                            return false
                                        }

                                        override fun onResourceReady(
                                            resource: Bitmap,
                                            model: Any,
                                            target: Target<Bitmap>?,
                                            dataSource: DataSource,
                                            isFirstResource: Boolean
                                        ): Boolean {
                                            resetThumbnailStyle(imageView)
                                            return false
                                        }
                                    })
                                    .placeholder(generatedPlaceholder)
                                    .error(generatedPlaceholder)
                                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                    .dontAnimate() // avoid placeholder flash on disk-cache hit
                                    .into(imageView)
                            }
                        }
                    } else {
                        // Cloud EPUB - check size limit (same as PDF)
                        if (file.size > NETWORK_EPUB_MAX_SIZE) {
                            showGeneratedPlaceholder(imageView, file)
                        } else {
                            // Cloud EPUB implementation would go here
                            showGeneratedPlaceholder(imageView, file)
                        }
                    }
                }
                MediaType.PDF -> {
                    // PDF thumbnails are always shown when PDF support is enabled
                    // getShowPdfThumbnails() controls size limits (Large PDF Thumbnails setting)
                    val largePdfThumbnails = getShowPdfThumbnails()
                    Timber.v("PDF_THUMB_DEBUG: Loading PDF thumbnail for ${file.name}, largePdfMode=$largePdfThumbnails, isNetwork=$isNetworkPath, isCloud=$isCloudPath, size=${file.size}")
                    
                    // Load PDF thumbnail using Glide (PdfPageDecoder registered in GlideAppModule)
                    if (!isCloudPath && !isNetworkPath) {
                        // Local PDF - Glide will use PdfPageDecoder automatically for .pdf files
                        val pdfFile = File(file.path)
                        if (pdfFile.exists()) {
                            Glide.with(context)
                                .asBitmap()
                                .load(pdfFile)
                                .signature(ObjectKey("${file.path}_${file.size}"))
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache rendered bitmap
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .into(imageView)
                        } else {
                            showGeneratedPlaceholder(imageView, file)
                        }
                    } else if (isNetworkPath) {
                        // Network PDF (SMB/SFTP/FTP) - check size limits based on setting
                        val isSmbPath = file.path.startsWith("smb://")
                        val maxSize = if (largePdfThumbnails) {
                            // "Large PDF Thumbnails" enabled - use large limits
                            if (isSmbPath) SMB_PDF_LARGE_MAX_SIZE else NETWORK_PDF_LARGE_MAX_SIZE
                        } else {
                            // "Large PDF Thumbnails" disabled - use normal limits
                            if (isSmbPath) SMB_PDF_NORMAL_MAX_SIZE else NETWORK_PDF_NORMAL_MAX_SIZE
                        }
                        Timber.v("PDF_THUMB_DEBUG: Network PDF ${file.name}, size=${file.size}, maxSize=$maxSize, isSMB=$isSmbPath, largePdfMode=$largePdfThumbnails")
                        
                        if (file.size > maxSize) {
                            // File too large - show placeholder icon without downloading
                            Timber.v("PDF_THUMB_DEBUG: PDF too large, showing placeholder for ${file.name}")
                            showGeneratedPlaceholder(imageView, file)
                        } else {
                            // Check if thumbnail loading previously failed for this file
                            if (NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                                Timber.v("Skipping PDF thumbnail load for ${file.name} (cached as failed)")
                                showGeneratedPlaceholder(imageView, file)
                            } else {
                                // File size OK - use NetworkPdfThumbnailLoader
                                Timber.v("PDF_THUMB_DEBUG: Loading network PDF thumbnail via Glide for ${file.name}")
                                Glide.with(context)
                                    .asBitmap()
                                    .load(NetworkFileData(
                                        path = file.path,
                                        credentialsId = credentialsId,
                                        loadFullImage = false,
                                        size = file.size,
                                        createdDate = file.createdDate
                                    ))
                                    .apply(
                                        RequestOptions().set(
                                            com.sza.fastmediasorter.data.glide.NetworkPdfThumbnailLoader.OPTION_FULL_PDF_DOWNLOAD,
                                            largePdfThumbnails
                                        )
                                    )
                                    .listener(object : RequestListener<Bitmap> {
                                        override fun onLoadFailed(
                                            e: GlideException?,
                                            model: Any?,
                                            target: Target<Bitmap>,
                                            isFirstResource: Boolean
                                        ): Boolean {
                                            if (e != null) {
                                                Timber.w("PDF thumbnail load failed: ${file.name}, ${e.message}")
                                                NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
                                            }
                                            return false
                                        }

                                        override fun onResourceReady(
                                            resource: Bitmap,
                                            model: Any,
                                            target: Target<Bitmap>?,
                                            dataSource: DataSource,
                                            isFirstResource: Boolean
                                        ): Boolean {
                                            resetThumbnailStyle(imageView)
                                            return false
                                        }
                                    })
                                    .placeholder(generatedPlaceholder)
                                    .error(generatedPlaceholder)
                                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache rendered bitmap
                                    .dontAnimate() // avoid placeholder flash on disk-cache hit
                                    .into(imageView)
                            }
                        }
                    } else {
                        // Cloud PDF - check size limit based on setting
                        val maxSize = if (largePdfThumbnails) {
                            NETWORK_PDF_LARGE_MAX_SIZE
                        } else {
                            NETWORK_PDF_NORMAL_MAX_SIZE
                        }
                        
                        if (file.size > maxSize) {
                            showGeneratedPlaceholder(imageView, file)
                        } else {
                            // Cloud PDF implementation would go here
                            showGeneratedPlaceholder(imageView, file)
                        }
                    }
                }
                MediaType.IMAGE, MediaType.GIF -> {
                    // Pre-flight: HEIC/HEIF requires API 28+; AVIF requires API 31+.
                    val fileExt = file.name.substringAfterLast('.', "").lowercase()
                    if (!HeifSupportUtils.isSupported(fileExt)) {
                        Timber.w("loadThumbnail: ${fileExt.uppercase()} not supported on this device — showing placeholder for ${file.name}")
                        showGeneratedPlaceholder(imageView, file)
                        return
                    }
                    when {
                        isCloudPath -> {
                            // Load cloud thumbnail using CloudThumbnailData for authenticated access
                            // Detect provider from URL scheme authority (cloud://dropbox/, cloud://googledrive/, etc.)
                            val provider = when {
                                file.path.startsWith("cloud://googledrive", ignoreCase = true) || file.path.startsWith("cloud://google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
                                file.path.startsWith("cloud://onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                                file.path.startsWith("cloud://dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
                                else -> CloudProvider.GOOGLE_DRIVE
                            }
                            // Extract file ID from cloud path
                            val fileId = when (provider) {
                                CloudProvider.DROPBOX -> {
                                    // Dropbox needs full path starting with /
                                    val dropboxPath = file.path.substringAfter("cloud://dropbox")
                                    if (dropboxPath.startsWith("/")) dropboxPath else "/$dropboxPath"
                                }
                                else -> {
                                    // Google Drive and OneDrive use file ID (last segment)
                                    file.path.substringAfterLast("/")
                                }
                            }
                            Glide.with(context)
                                .load(CloudThumbnailData(
                                    thumbnailUrl = file.thumbnailUrl ?: "",
                                    fileId = fileId,
                                    loadFullImage = false, // Load thumbnail for browse list
                                    cloudProvider = provider
                                ))
                                .priority(Priority.HIGH)  // High priority for images
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)  // Cache decoded, not source stream
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        isNetworkPath -> {
                            // Check if thumbnail loading previously failed for this file
                            if (NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                                Timber.v("Skipping thumbnail load for ${file.name} (cached as failed)")
                                showGeneratedPlaceholder(imageView, file)
                                return
                            }
                            
                            // Load network image using NetworkFileData (implements Key interface for cache)
                            Glide.with(context)
                                .load(NetworkFileData(
                                    path = file.path,
                                    credentialsId = credentialsId,
                                    loadFullImage = false,
                                    size = file.size,
                                    createdDate = file.createdDate
                                ))
                                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<android.graphics.drawable.Drawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        if (e != null) {
                                            Timber.w("Network image load failed: ${file.name}, ${e.message}")
                                            NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
                                        }
                                        applyPlaceholderStyle(imageView, file.type, true)
                                        return false
                                    }

                                    override fun onResourceReady(
                                        resource: android.graphics.drawable.Drawable,
                                        model: Any,
                                        target: Target<android.graphics.drawable.Drawable>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        com.sza.fastmediasorter.utils.GlideCacheStats.recordLoad(dataSource)
                                        Timber.v("CACHE_HIT_DEBUG: Network image loaded from ${dataSource.name} for ${file.name}")
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .priority(Priority.HIGH)  // High priority for images
                                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded for persistence
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        else -> {
                            // Load image/GIF thumbnail using Glide for local files
                            val data: Any = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
                            } else if (!File(file.path).canRead() && !file.contentUri.isNullOrEmpty()) {
                                Uri.parse(file.contentUri)
                            } else {
                                File(file.path)
                            }
                            Glide.with(context)
                                .load(data)
                                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<android.graphics.drawable.Drawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        if (e != null) {
                                            Timber.w("Local image load failed: ${file.name}, ${e.message}")
                                        }
                                        applyPlaceholderStyle(imageView, file.type, true)
                                        return false
                                    }

                                    override fun onResourceReady(
                                        resource: android.graphics.drawable.Drawable,
                                        model: Any,
                                        target: Target<android.graphics.drawable.Drawable>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        com.sza.fastmediasorter.utils.GlideCacheStats.recordLoad(dataSource)
                                        Timber.v("CACHE_HIT_DEBUG: Local image loaded from ${dataSource.name} for ${file.name}")
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .signature(ObjectKey("${file.path}_${file.size}")) // Stable cache key (path + size)
                                .priority(Priority.HIGH)  // High priority for images
                                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded (critical for GIF persistence)
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                    }
                }
                MediaType.VIDEO -> {
                    when {
                        isCloudPath -> {
                            // Load cloud video thumbnail using CloudThumbnailData for authenticated access
                            // Detect provider from URL scheme authority
                            val provider = when {
                                file.path.startsWith("cloud://googledrive", ignoreCase = true) || file.path.startsWith("cloud://google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
                                file.path.startsWith("cloud://onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                                file.path.startsWith("cloud://dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
                                else -> CloudProvider.GOOGLE_DRIVE
                            }
                            // Extract file ID from cloud path
                            val fileId = when (provider) {
                                CloudProvider.DROPBOX -> {
                                    // Dropbox needs full path starting with /
                                    val dropboxPath = file.path.substringAfter("cloud://dropbox")
                                    if (dropboxPath.startsWith("/")) dropboxPath else "/$dropboxPath"
                                }
                                else -> {
                                    // Google Drive and OneDrive use file ID (last segment)
                                    file.path.substringAfterLast("/")
                                }
                            }
                            Glide.with(context)
                                .load(CloudThumbnailData(
                                    thumbnailUrl = file.thumbnailUrl ?: "",
                                    fileId = fileId,
                                    loadFullImage = false, // Load thumbnail for browse list
                                    cloudProvider = provider
                                ))
                                .priority(Priority.NORMAL)  // Normal priority for videos
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)  // Cache decoded, not source stream
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        isNetworkPath -> {
                            // Check if video thumbnails are enabled
                            if (!getShowVideoThumbnails()) {
                                showGeneratedPlaceholder(imageView, file)
                                return
                            }
                            
                            // Load network video thumbnail using NetworkFileData
                            // Use listener to catch decoder failures and cache them
                            Glide.with(context)
                                .load(NetworkFileData(
                                    path = file.path,
                                    credentialsId = credentialsId,
                                    loadFullImage = false,
                                    size = file.size,
                                    createdDate = file.createdDate
                                ))
                                .priority(Priority.NORMAL)  // Normal priority for videos
                                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<android.graphics.drawable.Drawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        // Check if this is a video decoder failure
                                        if (isVideoDecoderException(e)) {
                                            NetworkFileDataFetcher.markVideoAsFailed(file.path)
                                            Timber.v("Thumbnail load failed: ${file.name} (decoder error, cached)")
                                        } else if (e != null) {
                                            Timber.w("Thumbnail load failed: ${file.name}, ${e.message}")
                                        }
                                        applyPlaceholderStyle(imageView, file.type, true)
                                        return false // Let Glide show error placeholder
                                    }

                                    override fun onResourceReady(
                                        resource: android.graphics.drawable.Drawable,
                                        model: Any,
                                        target: Target<android.graphics.drawable.Drawable>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        com.sza.fastmediasorter.utils.GlideCacheStats.recordLoad(dataSource)
                                        Timber.v("CACHE_HIT_DEBUG: Video loaded from ${dataSource.name} for ${file.name}")
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        else -> {
                            // Check if video thumbnails are enabled
                            if (!getShowVideoThumbnails()) {
                                showGeneratedPlaceholder(imageView, file)
                                return
                            }
                            
                            // Load video first frame using Glide for local files
                            val data: Any = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
                            } else if (!File(file.path).canRead() && !file.contentUri.isNullOrEmpty()) {
                                Uri.parse(file.contentUri)
                            } else {
                                File(file.path)
                            }
                            Glide.with(context)
                                .load(data)
                                .signature(ObjectKey("${file.path}_${file.size}")) // Stable cache key (path + size)
                                .priority(Priority.NORMAL)  // Normal priority for videos
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache decoded bitmap — fixes re-load on every open
                                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<android.graphics.drawable.Drawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        applyPlaceholderStyle(imageView, file.type, true)
                                        return false
                                    }

                                    override fun onResourceReady(
                                        resource: android.graphics.drawable.Drawable,
                                        model: Any,
                                        target: Target<android.graphics.drawable.Drawable>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        com.sza.fastmediasorter.utils.GlideCacheStats.recordLoad(dataSource)
                                        Timber.v("CACHE_HIT_DEBUG: Local video loaded from ${dataSource.name} for ${file.name}")
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                    }
                }
                MediaType.AUDIO -> {
                    val extension = file.name.substringAfterLast('.', "").uppercase()
                    val bitmap = createExtensionBitmap(extension)
                    imageView.setImageBitmap(bitmap)
                    applyPlaceholderStyle(imageView, file.type, true)
                }
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                    val extension = file.name.substringAfterLast('.', "").uppercase()
                    imageView.setImageBitmap(binaryThumbnailGenerator?.generateThumbnail(extension, file.type))
                    applyPlaceholderStyle(imageView, file.type, true)
                }
            }
        }
        
        private fun createExtensionBitmap(extension: String): Bitmap {
            return ExtensionThumbnailGenerator.generate(extension, 200)
        }

        private fun getPlaceholderExtension(file: MediaFile): String {
            val extension = file.name.substringAfterLast('.', "").uppercase()
            if (extension.isNotBlank()) return extension
            return when (file.type) {
                MediaType.IMAGE, MediaType.GIF -> "IMG"
                MediaType.VIDEO -> "VID"
                MediaType.PDF -> "PDF"
                MediaType.EPUB -> "EPUB"
                MediaType.AUDIO -> "AUD"
                MediaType.TEXT -> "TXT"
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> "BIN"
            }
        }

        private fun createPlaceholderBitmap(file: MediaFile): Bitmap {
            return createExtensionBitmap(getPlaceholderExtension(file))
        }

        private fun createPlaceholderDrawable(file: MediaFile): BitmapDrawable {
            return BitmapDrawable(binding.root.resources, createPlaceholderBitmap(file))
        }

        private fun showGeneratedPlaceholder(imageView: android.widget.ImageView, file: MediaFile) {
            imageView.setImageBitmap(createPlaceholderBitmap(file))
            applyPlaceholderStyle(imageView, file.type, true)
        }
        
        /** "Artist - Title" for top line in audio-only mode. Falls back to filename if no metadata. */
        private fun buildAudioDisplayName(file: MediaFile): String {
            val result = when {
                !file.artist.isNullOrBlank() && !file.title.isNullOrBlank() -> "${file.artist} - ${file.title}"
                !file.artist.isNullOrBlank() -> file.artist
                !file.title.isNullOrBlank() -> file.title
                else -> file.name
            }
            // Guard against invisible characters from malformed ID3 tags (BOM, NUL, etc.)
            val trimmed = result.trim()
            if (trimmed.isEmpty() || trimmed.all { it.code < 32 || it == '\u00A0' || it == '\uFEFF' }) {
                Timber.w(
                    "buildAudioDisplayName: invisible result for '${file.name}' | " +
                    "artist.codes=${file.artist?.map { it.code }?.take(8)} | " +
                    "title.codes=${file.title?.map { it.code }?.take(8)}"
                )
                return file.name
            }
            return result
        }

        /** "size • date • duration" for bottom line in audio-only mode. */
        private fun buildAudioDetailLine(file: MediaFile): String {
            val size = if (file.size > 0) formatFileSize(file.size) else null
            val date = if (file.createdDate > 0) DateFormat.format("yy-MM-dd HH:mm", Date(file.createdDate)).toString() else null
            val duration = formatDuration(file.duration)
            return listOfNotNull(size, date, duration).joinToString(" • ")
        }

        private fun buildFileInfo(file: MediaFile): String {
            // Handle folders
            if (file.isDirectory) {
                val count = file.childCount ?: 0
                return when {
                    count == 0 -> "Empty folder"
                    count == 1 -> "1 item"
                    else -> "$count items"
                }
            }

            val legacyInfo = buildLegacyFileInfo(file)

            when (file.type) {
                MediaType.AUDIO -> {
                    val hasMetadata = !file.artist.isNullOrBlank() || !file.title.isNullOrBlank()
                    val duration = formatDuration(file.duration)
                    return if (hasMetadata) {
                        val audioTitle = when {
                            !file.artist.isNullOrBlank() && !file.title.isNullOrBlank() -> "${file.artist} - ${file.title}"
                            !file.artist.isNullOrBlank() -> file.artist
                            else -> file.title ?: file.name
                        }
                        if (duration != null) "$audioTitle • $duration" else audioTitle
                    } else {
                        // No metadata: show size + date (+ duration if known)
                        if (duration != null) "$legacyInfo • $duration" else legacyInfo
                    }
                }

                MediaType.VIDEO -> {
                    val resolution = if (file.width != null && file.height != null) {
                        "${file.width}x${file.height}"
                    } else {
                        null
                    }
                    val duration = formatDuration(file.duration)
                    val parts = listOfNotNull(resolution, duration)
                    return if (parts.isNotEmpty()) parts.joinToString(" • ") else legacyInfo
                }

                MediaType.IMAGE, MediaType.GIF -> {
                    val resolution = if (file.width != null && file.height != null) {
                        "${file.width}x${file.height}"
                    } else {
                        null
                    }
                    val dateTaken = file.exifDateTime?.let { DateFormat.format("yy-MM-dd HH:mm", Date(it)).toString() }
                    val parts = listOfNotNull(resolution, dateTaken)
                    return if (parts.isNotEmpty()) parts.joinToString(" • ") else legacyInfo
                }

                else -> return legacyInfo
            }
        }

        private fun buildLegacyFileInfo(file: MediaFile): String {
            
            // Hide invalid FTP metadata (size=0 or date=1970-01-01)
            val size = if (file.size > 0) formatFileSize(file.size) else "—"
            val date = if (file.createdDate > 0) {
                DateFormat.format("yy-MM-dd HH:mm", Date(file.createdDate))
            } else {
                "—"
            }
            return "$size • $date"
        }

        private fun formatDuration(durationMs: Long?): String? = formatMediaDuration(durationMs)
        
        private fun formatFileSize(size: Long): String {
            return com.sza.fastmediasorter.core.util.formatFileSize(size)
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

            binding.root.setOnGenericMotionListener { _, event ->
                if (event.buttonState == android.view.MotionEvent.BUTTON_SECONDARY) {
                    val file = getItemByPosition() ?: return@setOnGenericMotionListener false
                    Timber.d("Right-click on ${file.name}")
                    onFileLongClick(file)
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
            // Partial update: only reload thumbnail (called via payload)
            // Check if we need to reload based on the key (includes refreshVersion)
            // Note: credentialsId removed from key - it's session-specific and shouldn't affect cache
            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"
            
            // Only skip reload if the key is exactly the same (meaning thumbnail already loaded for this version)
            if (lastLoadedKey == newKey) {
                return
            }
            
            loadThumbnail(file)
            // Update key so AUDIO/TEXT/Binary fast-paths (which return early without setting the key)
            // don't re-trigger on the next LOAD_THUMBNAILS payload after the same recycle.
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
            // Skip thumbnail loading during fast scroll
            if (isScrolling) {
                Timber.v("loadThumbnail (Grid): SKIPPED during scroll for ${file.name}")
                return
            }
            
            // Skip thumbnail loading for directories (folders have static icons)
            if (file.isDirectory) {
                Timber.v("loadThumbnail: SKIP directory ${file.name}")
                return
            }
            
            // AUDIO: show extension bitmap only (no metadata/cover art/internet in Browse).
            // Cover art is loaded exclusively in the Player (ImageLoadingManager.loadAudioCoverArt).
            if (file.type == MediaType.AUDIO) {
                val extension = file.name.substringAfterLast('.', "").uppercase()
                binding.ivThumbnail.setImageBitmap(createExtensionBitmap(extension))
                applyPlaceholderStyle(binding.ivThumbnail, file.type, true)
                Timber.v("loadThumbnail (Grid): AUDIO extension bitmap for ${file.name} ($extension)")
                return
            }

            // TEXT: show extension bitmap (no network/disk loading needed)
            if (file.type == MediaType.TEXT) {
                val extension = file.name.substringAfterLast('.', "").uppercase()
                binding.ivThumbnail.setImageBitmap(createExtensionBitmap(extension))
                applyPlaceholderStyle(binding.ivThumbnail, file.type, true)
                return
            }
            
            // Task 6: Binary files - generate custom thumbnail
            if (file.type.isBinaryFile()) {
                val extension = file.name.substringAfterLast('.', "").ifEmpty { "BIN" }
                binaryThumbnailGenerator?.let { generator ->
                    val thumbnailSize = (this@MediaFileAdapter.thumbnailSize * binding.root.context.resources.displayMetrics.density).toInt()
                    val thumbnail = generator.generateThumbnail(extension, file.type, thumbnailSize)
                    binding.ivThumbnail.setImageBitmap(thumbnail)
                    resetThumbnailStyle(binding.ivThumbnail)
                    Timber.v("Binary file thumbnail generated for ${file.name}")
                } ?: run {
                    val ext = extension.uppercase()
                    binding.ivThumbnail.setImageBitmap(createExtensionBitmap(ext))
                    applyPlaceholderStyle(binding.ivThumbnail, file.type, true)
                }
                return
            }
            
            Timber.v("loadThumbnail: START file=${file.name}")
            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"
            if (lastLoadedKey == newKey) {
                return
            }
            lastLoadedKey = newKey

            val imageView = binding.ivThumbnail
            val context = imageView.context
            val generatedPlaceholder = createPlaceholderDrawable(file)
            
            // Reset scaleType to CENTER_CROP (may have been CENTER_INSIDE for folder icons due to view recycling)
            imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            
            // If thumbnails disabled, show only extension-based icons (no Glide loading)
            if (this@MediaFileAdapter.disableThumbnails) {
                when (file.type) {
                    MediaType.IMAGE -> showGeneratedPlaceholder(imageView, file)
                    MediaType.VIDEO -> showGeneratedPlaceholder(imageView, file)
                    MediaType.AUDIO -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(createExtensionBitmap(extension))
                    }
                    MediaType.GIF -> showGeneratedPlaceholder(imageView, file)
                    MediaType.TEXT -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(createExtensionBitmap(extension))
                    }
                    MediaType.PDF -> showGeneratedPlaceholder(imageView, file)
                    MediaType.EPUB -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(createExtensionBitmap(extension))
                    }
                    MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                    MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(binaryThumbnailGenerator?.generateThumbnail(extension, file.type))
                    }
                }
                return
            }
            
            // Check if this is a cloud path (cloud://)
            val isCloudPath = file.path.startsWith("cloud://")
            // Check if this is a network path (SMB/SFTP/FTP)
            val isNetworkPath = file.path.startsWith("smb://") || file.path.startsWith("sftp://") || file.path.startsWith("ftp://")

            // For local files, check if file exists (skip for content:// URIs)
            if (!isNetworkPath && !isCloudPath && !file.path.startsWith("content://")) {
                val localFile = File(file.path)
                if (!localFile.exists()) {
                    Timber.w("File no longer exists: ${file.path}")
                    when (file.type) {
                        MediaType.IMAGE, MediaType.GIF -> showGeneratedPlaceholder(imageView, file)
                        MediaType.VIDEO -> showGeneratedPlaceholder(imageView, file)
                        MediaType.AUDIO -> {
                            val extension = file.name.substringAfterLast('.', "").uppercase()
                            imageView.setImageBitmap(createExtensionBitmap(extension))
                        }
                        MediaType.TEXT -> {
                            val extension = file.name.substringAfterLast('.', "").uppercase()
                            imageView.setImageBitmap(createExtensionBitmap(extension))
                        }
                        MediaType.PDF -> showGeneratedPlaceholder(imageView, file)
                        MediaType.EPUB -> {
                            val extension = file.name.substringAfterLast('.', "").uppercase()
                            imageView.setImageBitmap(createExtensionBitmap(extension))
                        }
                        MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                        MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                            val extension = file.name.substringAfterLast('.', "").uppercase()
                            imageView.setImageBitmap(binaryThumbnailGenerator?.generateThumbnail(extension, file.type))
                        }
                    }
                    return
                }
            }
            
            when (file.type) {
                MediaType.IMAGE, MediaType.GIF -> {
                    // Pre-flight: HEIC/HEIF requires API 28+; AVIF requires API 31+.
                    val fileExt = file.name.substringAfterLast('.', "").lowercase()
                    if (!HeifSupportUtils.isSupported(fileExt)) {
                        Timber.w("loadThumbnail (Grid): ${fileExt.uppercase()} not supported on this device — showing placeholder for ${file.name}")
                        showGeneratedPlaceholder(imageView, file)
                        return
                    }
                    when {
                        isCloudPath -> {
                            // Load cloud thumbnail using CloudThumbnailData for authenticated access
                            val provider = when {
                                file.path.startsWith("cloud://googledrive", ignoreCase = true) || file.path.startsWith("cloud://google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
                                file.path.startsWith("cloud://onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                                file.path.startsWith("cloud://dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
                                else -> CloudProvider.GOOGLE_DRIVE
                            }
                            
                            val fileId = when (provider) {
                                CloudProvider.DROPBOX -> {
                                    // Dropbox needs full path starting with /
                                    val dropboxPath = file.path.substringAfter("cloud://dropbox")
                                    if (dropboxPath.startsWith("/")) dropboxPath else "/$dropboxPath"
                                }
                                else -> {
                                    file.path.substringAfterLast("/")
                                }
                            }

                            Glide.with(context)
                                .load(CloudThumbnailData(
                                    thumbnailUrl = file.thumbnailUrl ?: "",
                                    fileId = fileId,
                                    loadFullImage = false,
                                    cloudProvider = provider
                                ))
                                .priority(Priority.HIGH)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        isNetworkPath -> {
                            // Check if thumbnail loading previously failed for this file
                            if (NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                                Timber.v("Skipping thumbnail load for ${file.name} (cached as failed)")
                                showGeneratedPlaceholder(imageView, file)
                                return
                            }

                            // Grid mode: use user-defined thumbnailSize (converts dp to px)
                            // val sizePx = (thumbnailSize * context.resources.displayMetrics.density).toInt()
                            Glide.with(context)
                                .load(NetworkFileData(
                                    path = file.path,
                                    credentialsId = credentialsId,
                                    loadFullImage = false,
                                    size = file.size,
                                    createdDate = file.createdDate
                                ))
                                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<android.graphics.drawable.Drawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        if (e != null) {
                                            Timber.w("Network image load failed (grid): ${file.name}, ${e.message}")
                                            NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
                                        }
                                        return false
                                    }

                                    override fun onResourceReady(
                                        resource: android.graphics.drawable.Drawable,
                                        model: Any,
                                        target: Target<android.graphics.drawable.Drawable>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        com.sza.fastmediasorter.utils.GlideCacheStats.recordLoad(dataSource)
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .priority(Priority.HIGH)  // High priority for images
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache decoded only - PipedInputStream can't be re-read
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        else -> {
                            val data: Any = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
                            } else if (!File(file.path).canRead() && !file.contentUri.isNullOrEmpty()) {
                                Uri.parse(file.contentUri)
                            } else {
                                File(file.path)
                            }
                            // val sizePx = (thumbnailSize * context.resources.displayMetrics.density).toInt()
                            Glide.with(context)
                                .load(data)
                                .signature(ObjectKey("${file.path}_${file.size}")) // Stable cache key (path + size)
                                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<android.graphics.drawable.Drawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        if (e != null) {
                                            Timber.w("Local image load failed (grid): ${file.name}, ${e.message}")
                                        }
                                        return false
                                    }

                                    override fun onResourceReady(
                                        resource: android.graphics.drawable.Drawable,
                                        model: Any,
                                        target: Target<android.graphics.drawable.Drawable>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        com.sza.fastmediasorter.utils.GlideCacheStats.recordLoad(dataSource)
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .priority(Priority.HIGH)  // High priority for images
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                    }
                }
                MediaType.VIDEO -> {
                    when {
                        isCloudPath -> {
                            // Load cloud video thumbnail using CloudThumbnailData
                            val provider = when {
                                file.path.startsWith("cloud://googledrive", ignoreCase = true) || file.path.startsWith("cloud://google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
                                file.path.startsWith("cloud://onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                                file.path.startsWith("cloud://dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
                                else -> CloudProvider.GOOGLE_DRIVE
                            }
                            
                            val fileId = when (provider) {
                                CloudProvider.DROPBOX -> {
                                    val dropboxPath = file.path.substringAfter("cloud://dropbox")
                                    if (dropboxPath.startsWith("/")) dropboxPath else "/$dropboxPath"
                                }
                                else -> {
                                    file.path.substringAfterLast("/")
                                }
                            }

                            Glide.with(context)
                                .load(CloudThumbnailData(
                                    thumbnailUrl = file.thumbnailUrl ?: "",
                                    fileId = fileId,
                                    loadFullImage = false,
                                    cloudProvider = provider
                                ))
                                .priority(Priority.NORMAL)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        isNetworkPath -> {
                            // Load network video thumbnail with error listener
                            // val sizePx = (thumbnailSize * context.resources.displayMetrics.density).toInt()
                            Glide.with(context)
                                .load(NetworkFileData(
                                    path = file.path,
                                    credentialsId = credentialsId,
                                    loadFullImage = false,
                                    size = file.size,
                                    createdDate = file.createdDate
                                ))
                                .priority(Priority.NORMAL)  // Normal priority for videos
                                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<android.graphics.drawable.Drawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        // Check if this is a video decoder failure
                                        if (isVideoDecoderException(e)) {
                                            NetworkFileDataFetcher.markVideoAsFailed(file.path)
                                            Timber.v("Thumbnail load failed: ${file.name} (decoder error, cached)")
                                        } else if (e != null) {
                                            Timber.w("Thumbnail load failed: ${file.name}, ${e.message}")
                                        }
                                        return false // Let Glide show error placeholder
                                    }

                                    override fun onResourceReady(
                                        resource: android.graphics.drawable.Drawable,
                                        model: Any,
                                        target: Target<android.graphics.drawable.Drawable>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        com.sza.fastmediasorter.utils.GlideCacheStats.recordLoad(dataSource)
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        else -> {
                            val data: Any = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
                            } else if (!File(file.path).canRead() && !file.contentUri.isNullOrEmpty()) {
                                Uri.parse(file.contentUri)
                            } else {
                                File(file.path)
                            }
                            // val sizePx = (thumbnailSize * context.resources.displayMetrics.density).toInt()
                            Glide.with(context)
                                .load(data)
                                .signature(ObjectKey("${file.path}_${file.size}")) // Stable cache key (path + size)
                                .priority(Priority.NORMAL)  // Normal priority for videos
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                    }
                }
                MediaType.AUDIO -> {
                    val extension = file.name.substringAfterLast('.', "").uppercase()
                    val bitmap = createExtensionBitmap(extension)
                    imageView.setImageBitmap(bitmap)
                }
                MediaType.TEXT -> {
                    val extension = file.name.substringAfterLast('.', "").uppercase()
                    imageView.setImageBitmap(createExtensionBitmap(extension))
                }
                MediaType.EPUB -> {
                    // Load EPUB cover using Glide (EpubCoverDecoder registered in GlideAppModule)
                    if (!isCloudPath && !isNetworkPath) {
                        // Local EPUB - Glide will use EpubCoverDecoder automatically for .epub files
                        val epubFile = File(file.path)
                        if (epubFile.exists()) {
                            Glide.with(context)
                                .asBitmap()
                                .load(epubFile)
                                .signature(ObjectKey("${file.path}_${file.size}"))
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache extracted cover
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .into(imageView)
                        } else {
                            showGeneratedPlaceholder(imageView, file)
                        }
                    } else if (isNetworkPath) {
                        // Network EPUB (SMB/SFTP/FTP) - check size limits (same as PDF logic)
                        val isSmbPath = file.path.startsWith("smb://")
                        val maxSize = if (isSmbPath) SMB_EPUB_MAX_SIZE else NETWORK_EPUB_MAX_SIZE
                        
                        if (file.size > maxSize) {
                            // File too large - show placeholder without downloading
                            showGeneratedPlaceholder(imageView, file)
                        } else {
                            // File size OK - use NetworkEpubCoverLoader
                            val networkData = NetworkFileData(
                                path = file.path,
                                size = file.size,
                                credentialsId = credentialsId
                            )
                            val cacheKey = ObjectKey("${file.path}_${file.size}")
                            Glide.with(context)
                                .asBitmap()
                                .load(networkData)
                                .signature(cacheKey)
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .into(imageView)
                        }
                    } else {
                        // Cloud EPUB - check size limit (same as PDF)
                        if (file.size > NETWORK_EPUB_MAX_SIZE) {
                            showGeneratedPlaceholder(imageView, file)
                        } else {
                            // Cloud EPUB implementation would go here
                            showGeneratedPlaceholder(imageView, file)
                        }
                    }
                }
                MediaType.PDF -> {
                    // PDF thumbnails are always shown when PDF support is enabled
                    // getShowPdfThumbnails() controls size limits (Large PDF Thumbnails setting)
                    val largePdfThumbnails = getShowPdfThumbnails()
                    
                    // Load PDF thumbnail using Glide (PdfPageDecoder registered in GlideAppModule)
                    if (!isCloudPath && !isNetworkPath) {
                        // Local PDF - Glide will use PdfPageDecoder automatically for .pdf files
                        val pdfFile = File(file.path)
                        if (pdfFile.exists()) {
                            Glide.with(context)
                                .asBitmap()
                                .load(pdfFile)
                                .signature(ObjectKey("${file.path}_${file.size}"))
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache rendered bitmap
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .into(imageView)
                        } else {
                            imageView.setImageBitmap(createExtensionBitmap("PDF"))
                        }
                    } else if (isNetworkPath) {
                        // Network PDF (SMB/SFTP/FTP) - check size limits based on setting
                        val isSmbPath = file.path.startsWith("smb://")
                        val maxSize = if (largePdfThumbnails) {
                            // "Large PDF Thumbnails" enabled - use large limits
                            if (isSmbPath) SMB_PDF_LARGE_MAX_SIZE else NETWORK_PDF_LARGE_MAX_SIZE
                        } else {
                            // "Large PDF Thumbnails" disabled - use normal limits
                            if (isSmbPath) SMB_PDF_NORMAL_MAX_SIZE else NETWORK_PDF_NORMAL_MAX_SIZE
                        }
                        
                        if (file.size > maxSize) {
                            // File too large - show PDF icon without downloading
                            imageView.setImageBitmap(createExtensionBitmap("PDF"))
                        } else {
                            // File size OK - use NetworkPdfThumbnailLoader
                            Glide.with(context)
                                .asBitmap()
                                .load(NetworkFileData(
                                    path = file.path,
                                    credentialsId = credentialsId,
                                    loadFullImage = false,
                                    size = file.size,
                                    createdDate = file.createdDate
                                ))
                                .apply(
                                    RequestOptions().set(
                                        com.sza.fastmediasorter.data.glide.NetworkPdfThumbnailLoader.OPTION_FULL_PDF_DOWNLOAD,
                                        largePdfThumbnails
                                    )
                                )
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache rendered bitmap
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .into(imageView)
                        }
                    } else {
                        // Cloud PDF - check size limit based on setting
                        val maxSize = if (largePdfThumbnails) {
                            NETWORK_PDF_LARGE_MAX_SIZE
                        } else {
                            NETWORK_PDF_NORMAL_MAX_SIZE
                        }
                        
                        if (file.size > maxSize) {
                            imageView.setImageBitmap(createExtensionBitmap("PDF"))
                        } else {
                            // Cloud PDF implementation would go here
                            imageView.setImageBitmap(createExtensionBitmap("PDF"))
                        }
                    }
                }
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                    val extension = file.name.substringAfterLast('.', "").uppercase()
                    imageView.setImageBitmap(binaryThumbnailGenerator?.generateThumbnail(extension, file.type))
                }
            }
        }
        
        private fun createExtensionBitmap(extension: String): Bitmap {
            return ExtensionThumbnailGenerator.generate(extension, 200)
        }

        private fun getPlaceholderExtension(file: MediaFile): String {
            val extension = file.name.substringAfterLast('.', "").uppercase()
            if (extension.isNotBlank()) return extension
            return when (file.type) {
                MediaType.IMAGE, MediaType.GIF -> "IMG"
                MediaType.VIDEO -> "VID"
                MediaType.PDF -> "PDF"
                MediaType.EPUB -> "EPUB"
                MediaType.AUDIO -> "AUD"
                MediaType.TEXT -> "TXT"
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> "BIN"
            }
        }

        private fun createPlaceholderBitmap(file: MediaFile): Bitmap {
            return createExtensionBitmap(getPlaceholderExtension(file))
        }

        private fun createPlaceholderDrawable(file: MediaFile): BitmapDrawable {
            return BitmapDrawable(binding.root.resources, createPlaceholderBitmap(file))
        }

        private fun showGeneratedPlaceholder(imageView: android.widget.ImageView, file: MediaFile) {
            imageView.setImageBitmap(createPlaceholderBitmap(file))
        }
    }

    private class MediaFileDiffCallback : DiffUtil.ItemCallback<MediaFile>() {
        override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
            return oldItem == newItem
        }
        
        override fun getChangePayload(oldItem: MediaFile, newItem: MediaFile): Any? {
            // If only isFavorite changed, return FAVORITE_CHANGED payload for partial update
            if (oldItem.isFavorite != newItem.isFavorite) {
                // isFavorite changed - check if everything else is the same
                if (oldItem.copy(isFavorite = newItem.isFavorite) == newItem) {
                    return "FAVORITE_CHANGED"
                }
            }
            // If only audio metadata changed (artist/album/title/duration), partial text update
            if (oldItem.type == MediaType.AUDIO &&
                (oldItem.artist != newItem.artist || oldItem.album != newItem.album ||
                    oldItem.title != newItem.title || oldItem.duration != newItem.duration)
            ) {
                if (oldItem.copy(
                        artist = newItem.artist,
                        album = newItem.album,
                        title = newItem.title,
                        duration = newItem.duration
                    ) == newItem
                ) {
                    return PAYLOAD_AUDIO_METADATA
                }
            }
            return null // Full rebind needed for other changes
        }
    }
}





