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
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemMediaFileBinding
import com.sza.fastmediasorter.databinding.ItemMediaFileGridBinding
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.data.cloud.glide.GoogleDriveThumbnailData
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.util.ExtensionThumbnailGenerator
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import com.sza.fastmediasorter.utils.setOnLongClickListenerDebounced
import timber.log.Timber
import java.io.File
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

/** PagingDataAdapter for large datasets (1000+ files); loads in pages to prevent OOM. */
class PagingMediaFileAdapter(
    private val onFileClick: (MediaFile, Int) -> Unit, // Added position parameter
    private val onFileLongClick: (MediaFile) -> Unit,
    private val onSelectionChanged: (MediaFile, Boolean) -> Unit,
    private val onSelectionRangeRequested: (MediaFile) -> Unit = {}, // Long click on checkbox
    private val onPlayClick: (MediaFile) -> Unit,
    private var isGridMode: Boolean = false,
    private var thumbnailSize: Int = 96,
    private val getShowVideoThumbnails: () -> Boolean = { false }, // Callback to get current setting
    private val getShowPdfThumbnails: () -> Boolean = { false } // Callback to get PDF thumbnail setting
) : PagingDataAdapter<MediaFile, RecyclerView.ViewHolder>(MediaFileDiffCallback()) {

    private var selectedPaths = setOf<String>()
    private var credentialsId: String? = null
    private var useCompactElements: Boolean = false

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val PAYLOAD_VIEW_MODE_CHANGE = "view_mode_change"
        
        private const val SMB_EPUB_MAX_SIZE = 50 * 1024 * 1024L // 50 MB for SMB
        private const val NETWORK_EPUB_MAX_SIZE = 10 * 1024 * 1024L // 10 MB for SFTP/FTP/Cloud
    }

    fun setCredentialsId(id: String?) {
        credentialsId = id
    }

    fun setUseCompactElements(enabled: Boolean) {
        if (useCompactElements != enabled) {
            useCompactElements = enabled
            notifyDataSetChanged()
        }
    }

    fun setGridMode(enabled: Boolean, iconSize: Int = 96) {
        if (isGridMode != enabled || thumbnailSize != iconSize) {
            val modeChanged = isGridMode != enabled
            val sizeChanged = thumbnailSize != iconSize
            isGridMode = enabled
            thumbnailSize = iconSize
            
            // When only size changes, force full refresh to update view layouts
            if (sizeChanged && !modeChanged) {
                notifyDataSetChanged()
            } else {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_VIEW_MODE_CHANGE)
            }
        }
    }

    fun setSelectedPaths(paths: Set<String>) {
        val oldSelected = selectedPaths
        selectedPaths = paths

        snapshot().forEachIndexed { index, file ->
            if (file != null && (file.path in oldSelected || file.path in paths)) {
                notifyItemChanged(index)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_GRID -> GridViewHolder(ItemMediaFileGridBinding.inflate(inflater, parent, false))
            else -> ListViewHolder(ItemMediaFileBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val file = getItem(position) ?: return
        when (holder) {
            is ListViewHolder -> holder.bind(file, selectedPaths)
            is GridViewHolder -> holder.bind(file, selectedPaths)
        }
    }
    
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        // Explicitly clear Glide requests when view is recycled to free up
        // ConnectionThrottleManager slots immediately. Critical for network resources.
        when (holder) {
            is ListViewHolder -> holder.clearImage()
            is GridViewHolder -> holder.clearImage()
        }
    }

    // Shared helpers - accessed by both inner ViewHolder classes
    private fun createExtensionBitmap(extension: String): Bitmap =
        ExtensionThumbnailGenerator.generate(extension, 200)

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

    private fun createPlaceholderBitmap(file: MediaFile): Bitmap =
        createExtensionBitmap(getPlaceholderExtension(file))

    private fun showGeneratedPlaceholder(imageView: android.widget.ImageView, file: MediaFile) {
        imageView.setImageBitmap(createPlaceholderBitmap(file))
    }

    inner class ListViewHolder(
        private val binding: ItemMediaFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun clearImage() {
            Glide.with(binding.ivThumbnail.context).clear(binding.ivThumbnail)
        }

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            binding.apply {
                val isSelected = file.path in selectedPaths

                val effectiveThumbnailSize = if (useCompactElements) thumbnailSize / 2 else thumbnailSize
                val sizeInPx = (effectiveThumbnailSize * root.context.resources.displayMetrics.density).toInt()
                ivThumbnail.layoutParams.width = sizeInPx
                ivThumbnail.layoutParams.height = sizeInPx

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

                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = isSelected
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(file, isChecked)
                }
                
                // Long click on checkbox: select range from last selected to this file
                cbSelect.setOnLongClickListener {
                    if (!isSelected) onSelectionRangeRequested(file)
                    true
                }
                root.setBackgroundColor(
                    if (isSelected) {
                        root.context.getColor(com.sza.fastmediasorter.R.color.item_selected)
                    } else {
                        root.context.getColor(com.sza.fastmediasorter.R.color.item_normal)
                    }
                )

                tvFileName.text = file.name
                tvFileInfo.text = buildFileInfo(file)

                loadThumbnail(file)

                ivThumbnail.setOnClickListenerDebounced {
                    onFileClick(file, bindingAdapterPosition)
                }

                root.setOnClickListenerDebounced {
                    onFileClick(file, bindingAdapterPosition)
                }

                root.setOnLongClickListenerDebounced {
                    onFileLongClick(file)
                    true
                }
            }
        }

        private fun loadThumbnail(file: MediaFile) {
            val imageView = binding.ivThumbnail
            val context = imageView.context
            val generatedPlaceholder = createPlaceholderDrawable(file)
            val isCloudPath = file.path.startsWith("cloud://") || file.path.startsWith("cloud:/")
            val isNetworkPath = file.path.startsWith("smb://") || file.path.startsWith("sftp://") || file.path.startsWith("ftp://")
            val cacheKey = "${file.path}_${file.size}"

            if (!isNetworkPath && !isCloudPath && !file.path.startsWith("content://")) {
                val localFile = File(file.path)
                if (!localFile.exists()) {
                    Timber.w("File no longer exists: ${file.path}")
                    when (file.type) {
                        MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO -> showGeneratedPlaceholder(imageView, file)
                        else -> imageView.setImageBitmap(createExtensionBitmap(
                            file.name.substringAfterLast('.', "").uppercase()))
                    }
                    return
                }
            }

            when (file.type) {
                MediaType.IMAGE, MediaType.GIF -> {
                    if (isCloudPath) {
                        if (!file.thumbnailUrl.isNullOrEmpty()) {
                            val fileId = file.path.substringAfterLast("/")
                            Glide.with(context)
                                .load(GoogleDriveThumbnailData(
                                    thumbnailUrl = file.thumbnailUrl,
                                    fileId = fileId
                                ))
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .override(200, 200)
                                .centerCrop()
                                .transform(RoundedCorners(8))
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        } else {
                            Timber.w("No thumbnailUrl for cloud file: ${file.name}")
                            showGeneratedPlaceholder(imageView, file)
                        }
                    } else if (isNetworkPath) {
                        Glide.with(context)
                            .load(NetworkFileData(path = file.path, credentialsId = credentialsId, size = file.size, createdDate = file.createdDate))
                            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded for persistence
                            .override(200, 200)
                            .centerCrop()
                            .transform(RoundedCorners(8))
                            .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                            .into(imageView)
                    } else {
                        val data: Any = if (file.path.startsWith("content://")) Uri.parse(file.path) else File(file.path)
                        Glide.with(context)
                            .load(data)
                            .signature(ObjectKey(cacheKey))
                            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded (critical for GIF persistence)
                            .override(200, 200)
                            .centerCrop()
                            .transform(RoundedCorners(8))
                            .dontAnimate() // avoid placeholder flash on disk-cache hit
                            .placeholder(generatedPlaceholder)
                            .error(generatedPlaceholder)
                            .into(imageView)
                    }
                }
                MediaType.VIDEO -> {
                    if (isCloudPath) {
                        if (!file.thumbnailUrl.isNullOrEmpty()) {
                            val fileId = file.path.substringAfterLast("/")
                            Glide.with(context)
                                .load(GoogleDriveThumbnailData(
                                    thumbnailUrl = file.thumbnailUrl,
                                    fileId = fileId
                                ))
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .override(200, 200)
                                .centerCrop()
                                .transform(RoundedCorners(8))
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        } else {
                            Timber.w("No thumbnailUrl for cloud video: ${file.name}")
                            showGeneratedPlaceholder(imageView, file)
                        }
                    } else if (isNetworkPath) {
                        // S0063: skip formats known to fail on network streams before issuing Glide request.
                        // Avoids wasting a 10-second MediaMetadataRetriever timeout slot per file.
                        val extCheck = file.name.substringAfterLast('.', "").lowercase()
                        if (com.sza.fastmediasorter.data.network.glide.NetworkThumbnailExtractionPolicy
                                .shouldSkipNetworkExtraction(extCheck)) {
                            Timber.d("[scope=thumbnail S0063] Blocked network format '$extCheck' - showing placeholder: ${file.name}")
                            showGeneratedPlaceholder(imageView, file)
                            imageView.contentDescription = context.getString(
                                R.string.thumbnail_unavailable_network_format, extCheck.uppercase())
                            return
                        }
                        Glide.with(context)
                            .load(NetworkFileData(path = file.path, credentialsId = credentialsId, size = file.size, createdDate = file.createdDate, highPriority = false))
                            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded for persistence
                            .override(200, 200)
                            .centerCrop()
                            .transform(RoundedCorners(8))
                            .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                            .into(imageView)
                    } else {
                        val data: Any = if (file.path.startsWith("content://")) Uri.parse(file.path) else File(file.path)
                        Glide.with(context)
                            .load(data)
                            .signature(ObjectKey(cacheKey))
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .override(200, 200)
                            .centerCrop()
                            .transform(RoundedCorners(8))
                            .dontAnimate() // avoid placeholder flash on disk-cache hit
                            .placeholder(generatedPlaceholder)
                            .error(generatedPlaceholder)
                            .into(imageView)
                    }
                }
                MediaType.AUDIO, MediaType.TEXT ->
                    imageView.setImageBitmap(createExtensionBitmap(
                        file.name.substringAfterLast('.', "").uppercase()))
                MediaType.PDF -> {
                    // Network PDF: load thumbnail; size limit via "Large PDF Thumbnails" setting
                    if (isNetworkPath) {
                        Glide.with(context)
                            .asBitmap()
                            .load(NetworkFileData(
                                path = file.path,
                                size = file.size,
                                credentialsId = credentialsId
                            ))
                            .apply(com.bumptech.glide.request.RequestOptions()
                                .set(com.sza.fastmediasorter.data.glide.NetworkPdfThumbnailLoader.OPTION_FULL_PDF_DOWNLOAD, getShowPdfThumbnails()))
                            .signature(ObjectKey(cacheKey))
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                            .placeholder(generatedPlaceholder)
                            .error(generatedPlaceholder)
                            .into(imageView)
                    } else {
                        imageView.setImageBitmap(createExtensionBitmap(
                            file.name.substringAfterLast('.', "").uppercase()))
                    }
                }
                MediaType.EPUB -> {
                    if (!isCloudPath && !isNetworkPath) {
                        val epubFile = File(file.path)
                        if (epubFile.exists()) {
                            Glide.with(context)
                                .asBitmap()
                                .load(epubFile)
                                .signature(ObjectKey(cacheKey))
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache extracted cover
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        } else {
                            showGeneratedPlaceholder(imageView, file)
                        }
                    } else if (isNetworkPath) {
                        // Network EPUB (SMB/SFTP/FTP): respect size limits (same as PDF)
                        val isSmbPath = file.path.startsWith("smb://")
                        val maxSize = if (isSmbPath) SMB_EPUB_MAX_SIZE else NETWORK_EPUB_MAX_SIZE
                        if (file.size > maxSize) {
                            showGeneratedPlaceholder(imageView, file)
                        } else {
                            Glide.with(context)
                                .asBitmap()
                                .load(NetworkFileData(path = file.path, size = file.size, credentialsId = credentialsId))
                                .signature(ObjectKey(cacheKey))
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .into(imageView)
                        }
                    } else {
                        showGeneratedPlaceholder(imageView, file)
                    }
                }
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER ->
                    imageView.setImageBitmap(createExtensionBitmap(
                        file.name.substringAfterLast('.', "").uppercase()))
            }
        }

        private fun createPlaceholderDrawable(file: MediaFile): BitmapDrawable =
            BitmapDrawable(binding.root.resources, createPlaceholderBitmap(file))

        private fun buildFileInfo(file: MediaFile): String {
            val size = formatFileSize(file.size)
            val date = DateFormat.format("yy-MM-dd HH:mm", Date(file.createdDate))
            return "$size • $date"
        }

        private fun formatFileSize(size: Long): String {
            return com.sza.fastmediasorter.core.util.formatFileSize(size)
        }
    }

    inner class GridViewHolder(
        private val binding: ItemMediaFileGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun clearImage() {
            Glide.with(binding.ivThumbnail.context).clear(binding.ivThumbnail)
        }

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            binding.apply {
                val isSelected = file.path in selectedPaths

                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = isSelected
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(file, isChecked)
                }
                cbSelect.setOnLongClickListener {
                    if (!isSelected) onSelectionRangeRequested(file)
                    true
                }

                val sizeInPx = (thumbnailSize * root.context.resources.displayMetrics.density).toInt()
                
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

                cvCard.setCardBackgroundColor(
                    if (isSelected) {
                        root.context.getColor(R.color.item_selected)
                    } else {
                        root.context.getColor(R.color.item_normal)
                    }
                )

                tvFileName.text = file.name

                loadThumbnail(file)

                ivThumbnail.setOnClickListenerDebounced {
                    onFileClick(file, bindingAdapterPosition)
                }

                root.setOnClickListenerDebounced {
                    onFileClick(file, bindingAdapterPosition)
                }

                root.setOnLongClickListenerDebounced {
                    onFileLongClick(file)
                    true
                }
            }
        }

        private fun loadThumbnail(file: MediaFile) {
            val imageView = binding.ivThumbnail
            val context = imageView.context
            val generatedPlaceholder = createPlaceholderDrawable(file)
            val isCloudPath = file.path.startsWith("cloud://") || file.path.startsWith("cloud:/")
            val isNetworkPath = file.path.startsWith("smb://") || file.path.startsWith("sftp://") || file.path.startsWith("ftp://")
            val cacheKey = "${file.path}_${file.size}"

            if (!isNetworkPath && !isCloudPath && !file.path.startsWith("content://")) {
                val localFile = File(file.path)
                if (!localFile.exists()) {
                    Timber.w("File no longer exists: ${file.path}")
                    when (file.type) {
                        MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO -> showGeneratedPlaceholder(imageView, file)
                        else -> imageView.setImageBitmap(createExtensionBitmap(
                            file.name.substringAfterLast('.', "").uppercase()))
                    }
                    return
                }
            }

            when (file.type) {
                MediaType.IMAGE, MediaType.GIF -> {
                    if (isCloudPath) {
                        if (!file.thumbnailUrl.isNullOrEmpty()) {
                            val fileId = file.path.substringAfterLast("/")
                            Glide.with(context)
                                .load(GoogleDriveThumbnailData(
                                    thumbnailUrl = file.thumbnailUrl,
                                    fileId = fileId
                                ))
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .override(200, 200)
                                .centerCrop()
                                .transform(RoundedCorners(8))
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        } else {
                            Timber.w("No thumbnailUrl for cloud file: ${file.name}")
                            showGeneratedPlaceholder(imageView, file)
                        }
                    } else if (isNetworkPath) {
                        Glide.with(context)
                            .load(NetworkFileData(path = file.path, credentialsId = credentialsId, size = file.size, createdDate = file.createdDate))
                            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded for persistence
                            .override(200, 200)
                            .centerCrop()
                            .transform(RoundedCorners(8))
                            .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                            .into(imageView)
                    } else {
                        val data: Any = if (file.path.startsWith("content://")) Uri.parse(file.path) else File(file.path)
                        Glide.with(context)
                            .load(data)
                            .signature(ObjectKey(cacheKey))
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .override(200, 200)
                            .centerCrop()
                            .transform(RoundedCorners(8))
                            .dontAnimate() // avoid placeholder flash on disk-cache hit
                            .placeholder(generatedPlaceholder)
                            .error(generatedPlaceholder)
                            .into(imageView)
                    }
                }
                MediaType.VIDEO -> {
                    if (isCloudPath) {
                        if (!file.thumbnailUrl.isNullOrEmpty()) {
                            val fileId = file.path.substringAfterLast("/")
                            Glide.with(context)
                                .load(GoogleDriveThumbnailData(
                                    thumbnailUrl = file.thumbnailUrl,
                                    fileId = fileId
                                ))
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .override(200, 200)
                                .centerCrop()
                                .transform(RoundedCorners(8))
                                .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                                .into(imageView)
                        } else {
                            Timber.w("No thumbnailUrl for cloud video: ${file.name}")
                            showGeneratedPlaceholder(imageView, file)
                        }
                    } else if (isNetworkPath) {
                        // S0063: skip formats known to fail on network streams before issuing Glide request.
                        // Avoids wasting a 10-second MediaMetadataRetriever timeout slot per file.
                        val extCheck = file.name.substringAfterLast('.', "").lowercase()
                        if (com.sza.fastmediasorter.data.network.glide.NetworkThumbnailExtractionPolicy
                                .shouldSkipNetworkExtraction(extCheck)) {
                            Timber.d("[scope=thumbnail S0063] Blocked network format '$extCheck' - showing placeholder: ${file.name}")
                            showGeneratedPlaceholder(imageView, file)
                            imageView.contentDescription = context.getString(
                                R.string.thumbnail_unavailable_network_format, extCheck.uppercase())
                            return
                        }
                        Glide.with(context)
                            .load(NetworkFileData(path = file.path, credentialsId = credentialsId, size = file.size, createdDate = file.createdDate, highPriority = false))
                            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded for persistence
                            .override(200, 200)
                            .centerCrop()
                            .transform(RoundedCorners(8))
                            .dontAnimate() // avoid placeholder flash on disk-cache hit
                                .placeholder(generatedPlaceholder)
                                .error(generatedPlaceholder)
                            .into(imageView)
                    } else {
                        val data: Any = if (file.path.startsWith("content://")) Uri.parse(file.path) else File(file.path)
                        Glide.with(context)
                            .load(data)
                            .signature(ObjectKey(cacheKey))
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .override(200, 200)
                            .centerCrop()
                            .transform(RoundedCorners(8))
                            .dontAnimate() // avoid placeholder flash on disk-cache hit
                            .placeholder(generatedPlaceholder)
                            .error(generatedPlaceholder)
                            .into(imageView)
                    }
                }
                MediaType.AUDIO, MediaType.TEXT, MediaType.EPUB ->
                    imageView.setImageBitmap(createExtensionBitmap(
                        file.name.substringAfterLast('.', "").uppercase()))
                MediaType.PDF -> {
                    if (isNetworkPath) {
                        Glide.with(context)
                            .asBitmap()
                            .load(NetworkFileData(
                                path = file.path,
                                size = file.size,
                                credentialsId = credentialsId
                            ))
                            .apply(com.bumptech.glide.request.RequestOptions()
                                .set(com.sza.fastmediasorter.data.glide.NetworkPdfThumbnailLoader.OPTION_FULL_PDF_DOWNLOAD, getShowPdfThumbnails()))
                            .signature(ObjectKey(cacheKey))
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                            .placeholder(generatedPlaceholder)
                            .error(generatedPlaceholder)
                            .into(imageView)
                    } else {
                        imageView.setImageBitmap(createExtensionBitmap(
                            file.name.substringAfterLast('.', "").uppercase()))
                    }
                }
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER ->
                    imageView.setImageBitmap(createExtensionBitmap(
                        file.name.substringAfterLast('.', "").uppercase()))
            }
        }

        private fun createPlaceholderDrawable(file: MediaFile): BitmapDrawable =
            BitmapDrawable(binding.root.resources, createPlaceholderBitmap(file))
    }
}




