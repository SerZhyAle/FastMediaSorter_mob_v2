package com.sza.fastmediasorter.ui.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.text.format.DateFormat
import android.view.LayoutInflater
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
    
    // Fast scroll detection to skip thumbnail loading during rapid scrolling
    private var isScrolling: Boolean = false
    
    // Binary file thumbnail generator (Task 6)
    private var binaryThumbnailGenerator: BinaryFileThumbnailGenerator? = null
    private var disableDocumentPreviewsOnLowMemory: Boolean? = null
    
    init {
        Timber.i("=== MediaFileAdapter CREATED with refreshVersion=$refreshVersion ===")
    }
    
    fun setBinaryThumbnailGenerator(generator: BinaryFileThumbnailGenerator) {
        binaryThumbnailGenerator = generator
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
    
    /**
     * Enable/disable initial thumbnail loading in bind().
     * When true, thumbnails are loaded only via LOAD_THUMBNAILS payload.
     */
    fun setSkipInitialThumbnailLoad(skip: Boolean) {
        Timber.d("MediaFileAdapter: setSkipInitialThumbnailLoad($skip)")
        skipInitialThumbnailLoad = skip
    }
    
    fun getSkipInitialThumbnailLoad(): Boolean = skipInitialThumbnailLoad
    
    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val PAYLOAD_VIEW_MODE_CHANGE = "view_mode_change"
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
        Timber.d("onBindViewHolder WITH PAYLOADS: position=$position, file=${file.name}, payloads=$payloads, isEmpty=${payloads.isEmpty()}, skipFlag=$skipInitialThumbnailLoad")
        
        if (payloads.isEmpty()) {
            // Standard full bind
            Timber.d("onBindViewHolder: payloads EMPTY, calling super (full bind) for ${file.name}")
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Handle multiple payloads - process each one
            if (payloads.contains("LOAD_THUMBNAILS")) {
                // For audio/text files: load extension bitmap (fast path in loadThumbnail handles this)
                // For other types: load full thumbnail via Glide
                Timber.d("onBindViewHolder: LOAD_THUMBNAILS payload detected for ${file.name}, calling loadThumbnailOnly")
                when (holder) {
                    is ListViewHolder -> {
                        Timber.d(">>> Calling ListViewHolder.loadThumbnailOnly for ${file.name}")
                        holder.loadThumbnailOnly(file)
                    }
                    is GridViewHolder -> {
                        Timber.d(">>> Calling GridViewHolder.loadThumbnailOnly for ${file.name}")
                        holder.loadThumbnailOnly(file)
                    }
                }
            }
            if (payloads.contains("FAVORITE_CHANGED")) {
                // Partial bind: only update favorite icon
                Timber.d("onBindViewHolder: FAVORITE_CHANGED payload detected for ${file.name}, updating icon only")
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
            // If no known payloads were handled, fall back to super
            if (!payloads.contains("LOAD_THUMBNAILS") && !payloads.contains("FAVORITE_CHANGED")) {
                Timber.d("onBindViewHolder: UNKNOWN payloads=$payloads, calling super")
                super.onBindViewHolder(holder, position, payloads)
            }
        }
    }
    
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        // Explicitly clear Glide requests when view is recycled to free up
        // ConnectionThrottleManager slots immediately. This is critical for
        // network resources where concurrency is limited.
        when (holder) {
            is ListViewHolder -> holder.clearImage()
            is GridViewHolder -> holder.clearImage()
        }
    }

    inner class ListViewHolder(
        private val binding: ItemMediaFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var lastLoadedKey: String? = null
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
            
            Timber.d("=== CACHE_KEY_DEBUG: loadThumbnailOnly called ===")
            Timber.d("  File: ${file.name}")
            Timber.d("  New Key: ${newKey.take(120)}")
            Timber.d("  Last Key: ${lastLoadedKey?.take(120)}")
            Timber.d("  refreshVersion: $refreshVersion")
            
            // Only skip reload if the key is exactly the same (meaning thumbnail already loaded for this version)
            if (lastLoadedKey == newKey) {
                Timber.d("  Result: SKIPPED - key matches (thumbnail already loaded)")
                return
            }
            
            Timber.d("  Result: LOADING - key mismatch (will call loadThumbnail)")
            loadThumbnail(file)
        }

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            // Note: Glide automatically cancels previous request when load() is called on same ImageView
            
            Timber.d("ListViewHolder.bind: START file=${file.name}, isDirectory=${file.isDirectory}, childCount=${file.childCount}")
            
            binding.apply {
                val isSelected = file.path in selectedPaths
                val isFolder = file.isDirectory
                
                // Apply thumbnail size from settings for list mode
                val thumbnailSizePx = if (this@MediaFileAdapter.disableThumbnails) {
                    if (isAudioOnlyMode) {
                        (AUDIO_ONLY_THUMBNAIL_DP * root.resources.displayMetrics.density).toInt()
                    } else {
                        (32 * root.resources.displayMetrics.density).toInt() // 32dp for list when disabled
                    }
                } else {
                    val sizeDp = if (isAudioOnlyMode) AUDIO_ONLY_THUMBNAIL_DP else thumbnailSize
                    (sizeDp * root.resources.displayMetrics.density).toInt()
                }
                ivThumbnail.layoutParams.width = thumbnailSizePx
                ivThumbnail.layoutParams.height = thumbnailSizePx
                
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
                
                tvFileName.text = file.name
                tvFileInfo.text = buildFileInfo(file)
                
                // Load thumbnail or folder icon
                if (isFolder) {
                    // ALWAYS show folder icon, regardless of skipInitialThumbnailLoad
                    Timber.d("ListViewHolder.bind: Setting folder icon for ${file.name}, isDirectory=${file.isDirectory}")
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
                        Timber.d("ListViewHolder.bind: SKIPPED initial thumbnail load for ${file.name} (waiting for payload)")
                    }
                }
                
                // Favorite button (hide for folders)
                btnFavorite.isVisible = showFavoriteButton && !isFolder
                btnFavorite.setImageResource(
                    if (file.isFavorite) R.drawable.ic_star_filled
                    else R.drawable.ic_star_outline
                )

                // Setup operation buttons with visibility checks
                // HIDE buttons if: (It's Grid Mode AND HideGridActions is ON) OR it's a folder
                val shouldHideActions = (isGridMode && hideGridActionButtons) || isFolder
                
                btnCopyItem.isVisible = hasDestinations && !shouldHideActions
                
                btnMoveItem.isVisible = hasDestinations && isWritable && !shouldHideActions
                
                btnRenameItem.isVisible = isWritable && !shouldHideActions
                
                btnDeleteItem.isVisible = isWritable && !shouldHideActions
            }
        }
        
        private fun loadThumbnail(file: MediaFile) {
            // Skip thumbnail loading during fast scroll
            if (isScrolling) {
                Timber.d("loadThumbnail: SKIPPED during scroll for ${file.name}")
                return
            }
            
            // Fast path for AUDIO/TEXT: no network/disk loading needed, just show extension bitmap
            if (file.type == MediaType.AUDIO || file.type == MediaType.TEXT) {
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
                    Timber.d("Binary file thumbnail generated for ${file.name}")
                } ?: run {
                    val ext = extension.uppercase()
                    binding.ivThumbnail.setImageBitmap(createExtensionBitmap(ext))
                    applyPlaceholderStyle(binding.ivThumbnail, file.type, true)
                }
                return
            }
            
            // Don't load thumbnails for directories - they use folder icon
            if (file.isDirectory) {
                Timber.d("loadThumbnail: SKIPPED for directory ${file.name}")
                return
            }
            
            Timber.d("loadThumbnail: START file=${file.name}")
            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"
            if (lastLoadedKey == newKey) {
                return
            }
            lastLoadedKey = newKey

            val imageView = binding.ivThumbnail
            val context = imageView.context
            val generatedPlaceholder = createPlaceholderDrawable(file)
            val generatedPlaceholder = createPlaceholderDrawable(file)
            
            // Reset scaleType to CENTER_CROP (may have been CENTER_INSIDE for folder icons due to view recycling)
            imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            
            // If thumbnails disabled, show only extension-based icons (no Glide loading)
            if (this@MediaFileAdapter.disableThumbnails) {
                Timber.d("THUMBNAIL_DEBUG: Skipping thumbnail load for ${file.name} - disableThumbnails=true")
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

            if (shouldDisableDocumentPreviews(context) && (file.type == MediaType.PDF || file.type == MediaType.EPUB)) {
                Timber.d("THUMBNAIL_DEBUG: Document preview disabled on LOW memory for ${file.name}")
                when (file.type) {
                    MediaType.PDF -> {
                        showGeneratedPlaceholder(imageView, file)
                    }
                    MediaType.EPUB -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(createExtensionBitmap(extension))
                        applyPlaceholderStyle(imageView, file.type, true)
                    }
                    else -> Unit
                }
                return
            }
            
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
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache extracted cover
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
                                Timber.d("Skipping EPUB cover load for ${file.name} (cached as failed)")
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
                    Timber.d("PDF_THUMB_DEBUG: Loading PDF thumbnail for ${file.name}, largePdfMode=$largePdfThumbnails, isNetwork=$isNetworkPath, isCloud=$isCloudPath, size=${file.size}")
                    
                    // Load PDF thumbnail using Glide (PdfPageDecoder registered in GlideAppModule)
                    if (!isCloudPath && !isNetworkPath) {
                        // Local PDF - Glide will use PdfPageDecoder automatically for .pdf files
                        val pdfFile = File(file.path)
                        if (pdfFile.exists()) {
                            Glide.with(context)
                                .asBitmap()
                                .load(pdfFile)
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache rendered bitmap
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
                        Timber.d("PDF_THUMB_DEBUG: Network PDF ${file.name}, size=${file.size}, maxSize=$maxSize, isSMB=$isSmbPath, largePdfMode=$largePdfThumbnails")
                        
                        if (file.size > maxSize) {
                            // File too large - show placeholder icon without downloading
                            Timber.d("PDF_THUMB_DEBUG: PDF too large, showing placeholder for ${file.name}")
                            showGeneratedPlaceholder(imageView, file)
                        } else {
                            // Check if thumbnail loading previously failed for this file
                            if (NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                                Timber.d("Skipping PDF thumbnail load for ${file.name} (cached as failed)")
                                showGeneratedPlaceholder(imageView, file)
                            } else {
                                // File size OK - use NetworkPdfThumbnailLoader
                                Timber.d("PDF_THUMB_DEBUG: Loading network PDF thumbnail via Glide for ${file.name}")
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
                    when {
                        isCloudPath -> {
                            // Load cloud thumbnail using CloudThumbnailData for authenticated access
                            // Detect provider from path: cloud://googledrive/, cloud://onedrive/, cloud://dropbox/
                            val provider = when {
                                file.path.contains("googledrive", ignoreCase = true) || file.path.contains("google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
                                file.path.contains("onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                                file.path.contains("dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
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
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        isNetworkPath -> {
                            // Check if thumbnail loading previously failed for this file
                            if (NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                                Timber.d("Skipping thumbnail load for ${file.name} (cached as failed)")
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
                                        Timber.d("CACHE_HIT_DEBUG: Network image loaded from ${dataSource.name} for ${file.name}")
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .priority(Priority.HIGH)  // High priority for images
                                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded for persistence
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(100))
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        else -> {
                            // Load image/GIF thumbnail using Glide for local files
                            val data = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
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
                                        Timber.d("CACHE_HIT_DEBUG: Local image loaded from ${dataSource.name} for ${file.name}")
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .signature(ObjectKey("${file.path}_${file.size}")) // Stable cache key (path + size)
                                .priority(Priority.HIGH)  // High priority for images
                                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded (critical for GIF persistence)
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(100))
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
                            // Detect provider from path
                            val provider = when {
                                file.path.contains("googledrive", ignoreCase = true) || file.path.contains("google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
                                file.path.contains("onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                                file.path.contains("dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
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
                                            Timber.d("Thumbnail load failed: ${file.name} (decoder error, cached)")
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
                                        Timber.d("CACHE_HIT_DEBUG: Video loaded from ${dataSource.name} for ${file.name}")
                                        resetThumbnailStyle(imageView)
                                        return false
                                    }
                                })
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(100))
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
                            val data = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
                            } else {
                                File(file.path)
                            }
                            Glide.with(context)
                                .load(data)
                                .signature(ObjectKey("${file.path}_${file.size}")) // Stable cache key (path + size)
                                .priority(Priority.NORMAL)  // Normal priority for videos
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE) // Fixed size for cache stability
                                .centerCrop()
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(100))
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
            
            // Hide invalid FTP metadata (size=0 or date=1970-01-01)
            val size = if (file.size > 0) formatFileSize(file.size) else "—"
            val date = if (file.createdDate > 0) {
                DateFormat.format("yyyy-MM-dd", Date(file.createdDate))
            } else {
                "—"
            }
            return "$size • $date"
        }
        
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
        }
        
        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            // Note: Glide automatically cancels previous request when load() is called on same ImageView
            
            Timber.d("GridViewHolder.bind: START file=${file.name}, isDirectory=${file.isDirectory}, childCount=${file.childCount}")
            
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
                    if (isAudioOnlyMode) {
                        (AUDIO_ONLY_THUMBNAIL_DP * root.context.resources.displayMetrics.density).toInt()
                    } else {
                        (64 * root.context.resources.displayMetrics.density).toInt() // 64dp when disabled
                    }
                } else {
                    val sizeDp = if (isAudioOnlyMode) AUDIO_ONLY_THUMBNAIL_DP else thumbnailSize
                    (sizeDp * root.context.resources.displayMetrics.density).toInt() // User preference
                }
                
                // Set fixed height for consistent grid appearance
                // Width is match_parent from XML, adjustViewBounds handles aspect ratio
                val imgParams = ivThumbnail.layoutParams
                imgParams.height = sizeInPx
                ivThumbnail.layoutParams = imgParams
                
                // Highlight selected items
                cvCard.setCardBackgroundColor(
                    if (isSelected) {
                        root.context.getColor(R.color.item_selected)
                    } else {
                        root.context.getColor(R.color.item_normal)
                    }
                )
                
                tvFileName.text = file.name
                
                // Load thumbnail or folder icon
                if (isFolder) {
                    // ALWAYS show folder icon, regardless of skipInitialThumbnailLoad
                    Timber.d("GridViewHolder.bind: Setting folder icon for ${file.name}, isDirectory=${file.isDirectory}")
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
                        Timber.d("GridViewHolder.bind: SKIPPED initial thumbnail load for ${file.name} (waiting for payload)")
                    }
                }
                
                // Favorite button
                btnFavorite.isVisible = showFavoriteButton
                btnFavorite.setImageResource(
                    if (file.isFavorite) R.drawable.ic_star_filled 
                    else R.drawable.ic_star_outline
                )

                // Setup operation buttons with visibility
                val shouldShowAnyOperation = hasDestinations || isWritable
                if (shouldShowAnyOperation) {
                    ensureOperationsInflated()
                }

                operationsContainer?.isVisible = shouldShowAnyOperation && !hideGridActionButtons
                btnCopyItem?.isVisible = hasDestinations && !hideGridActionButtons
                btnMoveItem?.isVisible = hasDestinations && isWritable && !hideGridActionButtons
                btnRenameItem?.isVisible = isWritable && !hideGridActionButtons
                btnDeleteItem?.isVisible = isWritable && !hideGridActionButtons
            }
        }
        
        private fun loadThumbnail(file: MediaFile) {
            // Skip thumbnail loading during fast scroll
            if (isScrolling) {
                Timber.d("loadThumbnail (Grid): SKIPPED during scroll for ${file.name}")
                return
            }
            
            // Skip thumbnail loading for directories (folders have static icons)
            if (file.isDirectory) {
                Timber.d("loadThumbnail: SKIP directory ${file.name}")
                return
            }
            
            // Fast path for AUDIO/TEXT: no network/disk loading needed, just show extension bitmap
            if (file.type == MediaType.AUDIO || file.type == MediaType.TEXT) {
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
                    Timber.d("Binary file thumbnail generated for ${file.name}")
                } ?: run {
                    val ext = extension.uppercase()
                    binding.ivThumbnail.setImageBitmap(createExtensionBitmap(ext))
                    applyPlaceholderStyle(binding.ivThumbnail, file.type, true)
                }
                return
            }
            
            Timber.d("loadThumbnail: START file=${file.name}")
            val newKey = "${file.path}_${file.size}_${disableThumbnails}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${refreshVersion}"
            if (lastLoadedKey == newKey) {
                return
            }
            lastLoadedKey = newKey

            val imageView = binding.ivThumbnail
            val context = imageView.context
            
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

            if (shouldDisableDocumentPreviews(context) && (file.type == MediaType.PDF || file.type == MediaType.EPUB)) {
                Timber.d("THUMBNAIL_DEBUG: Grid document preview disabled on LOW memory for ${file.name}")
                when (file.type) {
                    MediaType.PDF -> imageView.setImageBitmap(createExtensionBitmap("PDF"))
                    MediaType.EPUB -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        imageView.setImageBitmap(createExtensionBitmap(extension))
                    }
                    else -> Unit
                }
                return
            }
            
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
                    when {
                        isCloudPath -> {
                            // Load cloud thumbnail using CloudThumbnailData for authenticated access
                            val provider = when {
                                file.path.contains("googledrive", ignoreCase = true) || file.path.contains("google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
                                file.path.contains("onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                                file.path.contains("dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
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
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        isNetworkPath -> {
                            // Check if thumbnail loading previously failed for this file
                            if (NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                                Timber.d("Skipping thumbnail load for ${file.name} (cached as failed)")
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
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(100))
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        else -> {
                            val data = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
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
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(100))
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
                                file.path.contains("googledrive", ignoreCase = true) || file.path.contains("google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
                                file.path.contains("onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                                file.path.contains("dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
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
                                            Timber.d("Thumbnail load failed: ${file.name} (decoder error, cached)")
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
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(100))
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        }
                        else -> {
                            val data = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
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
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(100))
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
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache extracted cover
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
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache rendered bitmap
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
            return null // Full rebind needed for other changes
        }
    }
}





