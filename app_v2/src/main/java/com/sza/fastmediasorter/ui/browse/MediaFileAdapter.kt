package com.sza.fastmediasorter.ui.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.load
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.RoundedCornersTransformation
import com.sza.fastmediasorter.R
import timber.log.Timber
import com.sza.fastmediasorter.databinding.ItemMediaFileBinding
import com.sza.fastmediasorter.databinding.ItemMediaFileGridBinding
import com.sza.fastmediasorter.data.network.coil.NetworkFileData
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import java.io.File
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

class MediaFileAdapter(
    private val onFileClick: (MediaFile) -> Unit,
    private val onFileLongClick: (MediaFile) -> Unit,
    private val onSelectionChanged: (MediaFile, Boolean) -> Unit,
    private val onPlayClick: (MediaFile) -> Unit,
    private var isGridMode: Boolean = false,
    private var thumbnailSize: Int = 96, // Default size in dp
    private val getShowVideoThumbnails: () -> Boolean = { false } // Callback to get current setting
) : ListAdapter<MediaFile, RecyclerView.ViewHolder>(MediaFileDiffCallback()) {

    private var selectedPaths = setOf<String>()
    private var credentialsId: String? = null // Credentials ID for network files
    
    fun setCredentialsId(id: String?) {
        credentialsId = id
    }
    
    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val PAYLOAD_VIEW_MODE_CHANGE = "view_mode_change"
    }
    
    fun setGridMode(enabled: Boolean, iconSize: Int = 96) {
        if (isGridMode != enabled || thumbnailSize != iconSize) {
            val modeChanged = isGridMode != enabled
            val sizeChanged = thumbnailSize != iconSize
            isGridMode = enabled
            thumbnailSize = iconSize
            
            // When mode changes (List↔Grid), use payload to rebind items efficiently
            // When only size changes, use notifyDataSetChanged to force layout recalculation
            if (sizeChanged && !modeChanged) {
                notifyDataSetChanged() // Force view recreation for size changes
            } else {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_VIEW_MODE_CHANGE)
            }
        }
    }

    fun setSelectedPaths(paths: Set<String>) {
        val oldSelected = selectedPaths
        selectedPaths = paths
        
        currentList.forEachIndexed { index, file ->
            if (file.path in oldSelected || file.path in paths) {
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

    inner class ListViewHolder(
        private val binding: ItemMediaFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            binding.apply {
                val isSelected = file.path in selectedPaths
                
                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = isSelected
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(file, isChecked)
                }
                
                // Highlight selected items
                root.setCardBackgroundColor(
                    if (isSelected) {
                        root.context.getColor(com.sza.fastmediasorter.R.color.item_selected)
                    } else {
                        root.context.getColor(com.sza.fastmediasorter.R.color.item_normal)
                    }
                )
                
                tvFileName.text = file.name
                tvFileInfo.text = buildFileInfo(file)
                
                // Load thumbnail using Coil
                loadThumbnail(file)
                
                ivThumbnail.setOnClickListener {
                    onFileClick(file)
                }
                
                root.setOnClickListener {
                    onFileClick(file)
                }
                
                root.setOnLongClickListener {
                    onFileLongClick(file)
                    true
                }
                
                btnPlay.setOnClickListener {
                    onPlayClick(file)
                }
            }
        }
        
        @OptIn(ExperimentalCoilApi::class)
        private fun loadThumbnail(file: MediaFile) {
            binding.ivThumbnail.apply {
                // Check if this is a cloud path (cloud://)
                val isCloudPath = file.path.startsWith("cloud://")
                // Check if this is a network path (SMB/SFTP/FTP)
                val isNetworkPath = file.path.startsWith("smb://") || file.path.startsWith("sftp://") || file.path.startsWith("ftp://")
                
                // For local files, check if file exists (skip for content:// URIs)
                if (!isNetworkPath && !isCloudPath && !file.path.startsWith("content://")) {
                    val localFile = File(file.path)
                    if (!localFile.exists()) {
                        Timber.w("File no longer exists: ${file.path}")
                        // Show error placeholder for deleted files
                        when (file.type) {
                            MediaType.IMAGE, MediaType.GIF -> setImageResource(R.drawable.ic_image_error)
                            MediaType.VIDEO -> setImageResource(R.drawable.ic_video_error)
                            MediaType.AUDIO -> {
                                val extension = file.name.substringAfterLast('.', "").uppercase()
                                setImageBitmap(createExtensionBitmap(extension))
                            }
                        }
                        return
                    }
                    
                    // Check if file has been modified since last scan (stale thumbnail)
                    val currentModified = localFile.lastModified()
                    if (currentModified != file.createdDate) {
                        Timber.d("File modified since last scan: ${file.name} (${file.createdDate} -> $currentModified)")
                        // Invalidate Coil cache for this file
                        context.imageLoader.diskCache?.remove(file.path)
                        context.imageLoader.memoryCache?.remove(MemoryCache.Key(file.path))
                    }
                }
                
                Timber.d("Loading thumbnail for: ${file.name}, isCloud: $isCloudPath, isNetwork: $isNetworkPath, type: ${file.type}")
                
                when (file.type) {
                    MediaType.IMAGE, MediaType.GIF -> {
                        when {
                            isCloudPath -> {
                                // Load cloud thumbnail using thumbnailUrl if available
                                if (!file.thumbnailUrl.isNullOrEmpty()) {
                                    Timber.d("Loading cloud thumbnail from URL: ${file.thumbnailUrl}")
                                    load(file.thumbnailUrl) {
                                        size(512) // Fixed size for consistent caching across List/Grid modes
                                        crossfade(false)
                                        placeholder(R.drawable.ic_image_placeholder)
                                        error(R.drawable.ic_image_error)
                                        transformations(RoundedCornersTransformation(8f))
                                        memoryCacheKey(file.path)
                                        diskCacheKey(file.path)
                                        listener(
                                            onSuccess = { _, _ ->
                                                Timber.d("Successfully loaded cloud thumbnail: ${file.name}")
                                            },
                                            onError = { _, result ->
                                                Timber.w(result.throwable, "Failed to load cloud thumbnail: ${file.name}")
                                            }
                                        )
                                    }
                                } else {
                                    // Fallback: show placeholder for cloud files without thumbnailUrl
                                    Timber.w("No thumbnailUrl for cloud file: ${file.name}")
                                    setImageResource(R.drawable.ic_image_placeholder)
                                }
                            }
                            isNetworkPath -> {
                                // Load network image using NetworkFileData (Coil will use NetworkFileFetcher)
                                Timber.d("Loading network image via NetworkFileData: ${file.path}")
                                load(com.sza.fastmediasorter.data.network.coil.NetworkFileData(file.path, credentialsId)) {
                                    size(thumbnailSize) // Use configured thumbnail size from settings
                                    crossfade(false) // Disable crossfade for faster loading
                                    allowHardware(true) // GPU-accelerated decoding
                                    placeholder(R.drawable.ic_image_placeholder)
                                    error(R.drawable.ic_image_error)
                                    transformations(RoundedCornersTransformation(8f))
                                    // Set memory and disk cache policy
                                    memoryCacheKey(file.path)
                                    diskCacheKey(file.path)
                                    listener(
                                        onSuccess = { _, _ ->
                                            Timber.d("Successfully loaded network thumbnail: ${file.name}")
                                        },
                                        onError = { _, result ->
                                            Timber.w(result.throwable, "Failed to load network thumbnail: ${file.name}")
                                        }
                                    )
                                }
                            }
                            else -> {
                                // Load image/GIF thumbnail using Coil for local files
                                // Support both file:// paths and content:// URIs
                                val data = if (file.path.startsWith("content://")) {
                                    Uri.parse(file.path)
                                } else {
                                    File(file.path)
                                }
                                load(data) {
                                    size(512) // Fixed size for consistent caching across List/Grid modes
                                    crossfade(false)
                                    placeholder(R.drawable.ic_image_placeholder)
                                    error(R.drawable.ic_image_error)
                                    transformations(RoundedCornersTransformation(8f))
                                }
                            }
                        }
                    }
                    MediaType.VIDEO -> {
                        when {
                            isCloudPath -> {
                                // Load cloud video thumbnail using thumbnailUrl if available
                                if (!file.thumbnailUrl.isNullOrEmpty()) {
                                    Timber.d("Loading cloud video thumbnail from URL: ${file.thumbnailUrl}")
                                    load(file.thumbnailUrl) {
                                        crossfade(false)
                                        placeholder(R.drawable.ic_video_placeholder)
                                        error(R.drawable.ic_video_error)
                                        transformations(RoundedCornersTransformation(8f))
                                        memoryCacheKey(file.path)
                                        diskCacheKey(file.path)
                                    }
                                } else {
                                    // Fallback: show placeholder
                                    Timber.w("No thumbnailUrl for cloud video: ${file.name}")
                                    setImageResource(R.drawable.ic_video_placeholder)
                                }
                            }
                            isNetworkPath -> {
                                // If setting enabled, attempt frame extraction; otherwise show placeholder
                                if (getShowVideoThumbnails()) {
                                    val data = if (file.path.startsWith("content://")) {
                                        Uri.parse(file.path)
                                    } else {
                                        NetworkFileData(file.path, credentialsId)
                                    }
                                    load(data) {
                                        size(512)
                                        crossfade(false)
                                        placeholder(R.drawable.ic_video_placeholder)
                                        error(R.drawable.ic_video_placeholder)
                                        transformations(RoundedCornersTransformation(8f))
                                        // Pass NetworkFileData through parameters for NetworkVideoFrameDecoder
                                        if (data is NetworkFileData) {
                                            parameters(coil.request.Parameters.Builder().apply {
                                                set("network_file_data", data)
                                            }.build())
                                        }
                                        listener(
                                            onError = { _, result ->
                                                Timber.w(result.throwable, "Failed to load network video thumbnail: ${file.name}")
                                            }
                                        )
                                    }
                                } else {
                                    // Show placeholder icon immediately (no network delay, no decoding attempt)
                                    setImageResource(R.drawable.ic_video_placeholder)
                                }
                            }
                            else -> {
                                // Load video first frame using Coil with video frame decoder for local files
                                // Support both file:// paths and content:// URIs
                                val data = if (file.path.startsWith("content://")) {
                                    Uri.parse(file.path)
                                } else {
                                    File(file.path)
                                }
                                load(data) {
                                    size(512) // Fixed size for consistent caching across List/Grid modes
                                    crossfade(false)
                                    placeholder(R.drawable.ic_video_placeholder)
                                    error(R.drawable.ic_video_error)
                                    transformations(RoundedCornersTransformation(8f))
                                }
                            }
                        }
                    }
                    MediaType.AUDIO -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        val bitmap = createExtensionBitmap(extension)
                        setImageBitmap(bitmap)
                    }
                }
            }
        }
        
        private fun createExtensionBitmap(extension: String): Bitmap {
            val size = 200
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Background
            val bgPaint = Paint().apply {
                color = ContextCompat.getColor(binding.root.context, R.color.audio_icon_bg)
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
            
            // Text
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 60f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val xPos = size / 2f
            val yPos = (size / 2f - (textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(extension, xPos, yPos, textPaint)
            
            return bitmap
        }
        
        private fun buildFileInfo(file: MediaFile): String {
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
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (ln(size.toDouble()) / ln(1024.0)).toInt()
            return String.format(
                "%.1f %s",
                size / 1024.0.pow(digitGroups.toDouble()),
                units[digitGroups]
            )
        }
    }
    
    // Grid ViewHolder for grid mode
    inner class GridViewHolder(
        private val binding: ItemMediaFileGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            binding.apply {
                val isSelected = file.path in selectedPaths
                
                // Set dynamic thumbnail size
                val sizeInPx = (thumbnailSize * root.context.resources.displayMetrics.density).toInt()
                ivThumbnail.layoutParams.width = sizeInPx
                ivThumbnail.layoutParams.height = sizeInPx
                tvFileName.layoutParams.width = sizeInPx
                
                // Highlight selected items
                root.setCardBackgroundColor(
                    if (isSelected) {
                        root.context.getColor(R.color.item_selected)
                    } else {
                        root.context.getColor(R.color.item_normal)
                    }
                )
                
                tvFileName.text = file.name
                
                // Load thumbnail using Coil
                loadThumbnail(file)
                
                ivThumbnail.setOnClickListener {
                    onFileClick(file)
                }
                
                root.setOnClickListener {
                    onFileClick(file)
                }
                
                root.setOnLongClickListener {
                    onFileLongClick(file)
                    true
                }
            }
        }
        
        @OptIn(ExperimentalCoilApi::class)
        private fun loadThumbnail(file: MediaFile) {
            binding.ivThumbnail.apply {
                // Check if this is a cloud path (cloud://)
                val isCloudPath = file.path.startsWith("cloud://")
                // Check if this is a network path (SMB/SFTP/FTP)
                val isNetworkPath = file.path.startsWith("smb://") || file.path.startsWith("sftp://") || file.path.startsWith("ftp://")
                
                // For local files, check if file exists (skip for content:// URIs)
                if (!isNetworkPath && !isCloudPath && !file.path.startsWith("content://")) {
                    val localFile = File(file.path)
                    if (!localFile.exists()) {
                        Timber.w("File no longer exists: ${file.path}")
                        // Show error placeholder for deleted files
                        when (file.type) {
                            MediaType.IMAGE, MediaType.GIF -> setImageResource(R.drawable.ic_image_error)
                            MediaType.VIDEO -> setImageResource(R.drawable.ic_video_error)
                            MediaType.AUDIO -> {
                                val extension = file.name.substringAfterLast('.', "").uppercase()
                                setImageBitmap(createExtensionBitmap(extension))
                            }
                        }
                        return
                    }
                    
                    // Check if file has been modified since last scan (stale thumbnail)
                    val currentModified = localFile.lastModified()
                    if (currentModified != file.createdDate) {
                        Timber.d("File modified since last scan: ${file.name} (${file.createdDate} -> $currentModified)")
                        // Invalidate Coil cache for this file
                        context.imageLoader.diskCache?.remove(file.path)
                        context.imageLoader.memoryCache?.remove(MemoryCache.Key(file.path))
                    }
                }
                
                when (file.type) {
                    MediaType.IMAGE, MediaType.GIF -> {
                        when {
                            isCloudPath -> {
                                // Load cloud thumbnail using thumbnailUrl if available
                                if (!file.thumbnailUrl.isNullOrEmpty()) {
                                    load(file.thumbnailUrl) {
                                        size(512) // Fixed size for consistent caching across List/Grid modes
                                        crossfade(false)
                                        placeholder(R.drawable.ic_image_placeholder)
                                        error(R.drawable.ic_image_error)
                                        transformations(RoundedCornersTransformation(8f))
                                        memoryCacheKey(file.path)
                                        diskCacheKey(file.path)
                                    }
                                } else {
                                    // Fallback: show placeholder
                                    setImageResource(R.drawable.ic_image_placeholder)
                                }
                            }
                            isNetworkPath -> {
                                // Load network image using NetworkFileData (Coil will use NetworkFileFetcher)
                                load(com.sza.fastmediasorter.data.network.coil.NetworkFileData(file.path, credentialsId)) {
                                    size(512) // Fixed size for consistent caching across List/Grid modes
                                    crossfade(false)
                                    placeholder(R.drawable.ic_image_placeholder)
                                    error(R.drawable.ic_image_error)
                                    transformations(RoundedCornersTransformation(8f))
                                    memoryCacheKey(file.path)
                                    diskCacheKey(file.path)
                                }
                            }
                            else -> {
                                // Support both file:// paths and content:// URIs
                                val data = if (file.path.startsWith("content://")) {
                                    Uri.parse(file.path)
                                } else {
                                    File(file.path)
                                }
                                load(data) {
                                    size(512) // Fixed size for consistent caching across List/Grid modes
                                    crossfade(false)
                                    placeholder(R.drawable.ic_image_placeholder)
                                    error(R.drawable.ic_image_error)
                                    transformations(RoundedCornersTransformation(8f))
                                }
                            }
                        }
                    }
                    MediaType.VIDEO -> {
                        when {
                            isCloudPath -> {
                                // Load cloud video thumbnail using thumbnailUrl if available
                                if (!file.thumbnailUrl.isNullOrEmpty()) {
                                    load(file.thumbnailUrl) {
                                        size(512) // Fixed size for consistent caching across List/Grid modes
                                        crossfade(false)
                                        placeholder(R.drawable.ic_video_placeholder)
                                        error(R.drawable.ic_video_error)
                                        transformations(RoundedCornersTransformation(8f))
                                        memoryCacheKey(file.path)
                                        diskCacheKey(file.path)
                                    }
                                } else {
                                    // Fallback: show placeholder
                                    setImageResource(R.drawable.ic_video_placeholder)
                                }
                            }
                            isNetworkPath -> {
                                // If setting enabled, attempt frame extraction; otherwise show placeholder
                                if (getShowVideoThumbnails()) {
                                    val data = if (file.path.startsWith("content://")) {
                                        Uri.parse(file.path)
                                    } else {
                                        NetworkFileData(file.path, credentialsId)
                                    }
                                    load(data) {
                                        size(512)
                                        crossfade(false)
                                        placeholder(R.drawable.ic_video_placeholder)
                                        error(R.drawable.ic_video_placeholder)
                                        transformations(RoundedCornersTransformation(8f))
                                        // Pass NetworkFileData through parameters for NetworkVideoFrameDecoder
                                        if (data is NetworkFileData) {
                                            parameters(coil.request.Parameters.Builder().apply {
                                                set("network_file_data", data)
                                            }.build())
                                        }
                                    }
                                } else {
                                    // Show placeholder icon immediately (no network delay, no decoding attempt)
                                    setImageResource(R.drawable.ic_video_placeholder)
                                }
                            }
                            else -> {
                                // Support both file:// paths and content:// URIs
                                val data = if (file.path.startsWith("content://")) {
                                    Uri.parse(file.path)
                                } else {
                                    File(file.path)
                                }
                                load(data) {
                                    size(512) // Fixed size for consistent caching across List/Grid modes
                                    crossfade(false)
                                    placeholder(R.drawable.ic_video_placeholder)
                                    error(R.drawable.ic_video_error)
                                    transformations(RoundedCornersTransformation(8f))
                                }
                            }
                        }
                    }
                    MediaType.AUDIO -> {
                        val extension = file.name.substringAfterLast('.', "").uppercase()
                        val bitmap = createExtensionBitmap(extension)
                        setImageBitmap(bitmap)
                    }
                }
            }
        }
        
        private fun createExtensionBitmap(extension: String): Bitmap {
            val size = 200
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            val bgPaint = Paint().apply {
                color = ContextCompat.getColor(binding.root.context, R.color.audio_icon_bg)
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
            
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 60f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val xPos = size / 2f
            val yPos = (size / 2f - (textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(extension, xPos, yPos, textPaint)
            
            return bitmap
        }
    }

    private class MediaFileDiffCallback : DiffUtil.ItemCallback<MediaFile>() {
        override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
            return oldItem == newItem
        }
    }
}
