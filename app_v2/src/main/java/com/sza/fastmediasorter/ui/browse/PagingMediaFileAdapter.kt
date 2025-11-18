package com.sza.fastmediasorter.ui.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemMediaFileBinding
import com.sza.fastmediasorter.databinding.ItemMediaFileGridBinding
import com.sza.fastmediasorter.data.network.coil.NetworkFileData
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber
import java.io.File
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

/**
 * PagingDataAdapter for large datasets (1000+ files).
 * Efficiently loads files in pages to prevent OOM crashes.
 */
class PagingMediaFileAdapter(
    private val onFileClick: (MediaFile, Int) -> Unit, // Added position parameter
    private val onFileLongClick: (MediaFile) -> Unit,
    private val onSelectionChanged: (MediaFile, Boolean) -> Unit,
    private val onPlayClick: (MediaFile) -> Unit,
    private var isGridMode: Boolean = false,
    private var thumbnailSize: Int = 96,
    private val getShowVideoThumbnails: () -> Boolean = { false } // Callback to get current setting
) : PagingDataAdapter<MediaFile, RecyclerView.ViewHolder>(MediaFileDiffCallback()) {

    private var selectedPaths = setOf<String>()
    private var credentialsId: String? = null

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val PAYLOAD_VIEW_MODE_CHANGE = "view_mode_change"
    }

    fun setCredentialsId(id: String?) {
        credentialsId = id
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
        val file = getItem(position) ?: return
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

                root.setCardBackgroundColor(
                    if (isSelected) {
                        root.context.getColor(R.color.item_selected)
                    } else {
                        root.context.getColor(R.color.item_normal)
                    }
                )

                tvFileName.text = file.name
                tvFileInfo.text = buildFileInfo(file)

                loadThumbnail(file)

                ivThumbnail.setOnClickListener {
                    onFileClick(file, bindingAdapterPosition)
                }

                root.setOnClickListener {
                    onFileClick(file, bindingAdapterPosition)
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

        private fun loadThumbnail(file: MediaFile) {
            binding.ivThumbnail.apply {
                val isNetworkPath = file.path.startsWith("smb://") || file.path.startsWith("sftp://")

                if (!isNetworkPath && !file.path.startsWith("content://")) {
                    val localFile = File(file.path)
                    if (!localFile.exists()) {
                        Timber.w("File no longer exists: ${file.path}")
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
                }

                when (file.type) {
                    MediaType.IMAGE, MediaType.GIF -> {
                        if (isNetworkPath) {
                            load(com.sza.fastmediasorter.data.network.coil.NetworkFileData(file.path, credentialsId)) {
                                crossfade(false)
                                placeholder(R.drawable.ic_image_placeholder)
                                error(R.drawable.ic_image_error)
                                transformations(RoundedCornersTransformation(8f))
                                memoryCacheKey(file.path)
                                diskCacheKey(file.path)
                            }
                        } else {
                            val data = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
                            } else {
                                File(file.path)
                            }
                            load(data) {
                                crossfade(false)
                                placeholder(R.drawable.ic_image_placeholder)
                                error(R.drawable.ic_image_error)
                                transformations(RoundedCornersTransformation(8f))
                            }
                        }
                    }
                    MediaType.VIDEO -> {
                        if (isNetworkPath) {
                            // Show placeholder icon immediately (no network delay, no decoding attempt)
                            setImageResource(R.drawable.ic_video_placeholder)
                        } else {
                            val data = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
                            } else {
                                File(file.path)
                            }
                            load(data) {
                                crossfade(false)
                                placeholder(R.drawable.ic_video_placeholder)
                                error(R.drawable.ic_video_error)
                                transformations(RoundedCornersTransformation(8f))
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

        private fun buildFileInfo(file: MediaFile): String {
            val size = formatFileSize(file.size)
            val date = DateFormat.format("yyyy-MM-dd", Date(file.createdDate))
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

    inner class GridViewHolder(
        private val binding: ItemMediaFileGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            binding.apply {
                val isSelected = file.path in selectedPaths

                val sizeInPx = (thumbnailSize * root.context.resources.displayMetrics.density).toInt()
                ivThumbnail.layoutParams.width = sizeInPx
                ivThumbnail.layoutParams.height = sizeInPx
                tvFileName.layoutParams.width = sizeInPx

                root.setCardBackgroundColor(
                    if (isSelected) {
                        root.context.getColor(R.color.item_selected)
                    } else {
                        root.context.getColor(R.color.item_normal)
                    }
                )

                tvFileName.text = file.name

                loadThumbnail(file)

                ivThumbnail.setOnClickListener {
                    onFileClick(file, bindingAdapterPosition)
                }

                root.setOnClickListener {
                    onFileClick(file, bindingAdapterPosition)
                }

                root.setOnLongClickListener {
                    onFileLongClick(file)
                    true
                }
            }
        }

        private fun loadThumbnail(file: MediaFile) {
            binding.ivThumbnail.apply {
                val isNetworkPath = file.path.startsWith("smb://") || file.path.startsWith("sftp://")

                if (!isNetworkPath && !file.path.startsWith("content://")) {
                    val localFile = File(file.path)
                    if (!localFile.exists()) {
                        Timber.w("File no longer exists: ${file.path}")
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
                }

                when (file.type) {
                    MediaType.IMAGE, MediaType.GIF -> {
                        if (isNetworkPath) {
                            load(com.sza.fastmediasorter.data.network.coil.NetworkFileData(file.path, credentialsId)) {
                                crossfade(false)
                                placeholder(R.drawable.ic_image_placeholder)
                                error(R.drawable.ic_image_error)
                                transformations(RoundedCornersTransformation(8f))
                                memoryCacheKey(file.path)
                                diskCacheKey(file.path)
                            }
                        } else {
                            val data = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
                            } else {
                                File(file.path)
                            }
                            load(data) {
                                crossfade(false)
                                placeholder(R.drawable.ic_image_placeholder)
                                error(R.drawable.ic_image_error)
                                transformations(RoundedCornersTransformation(8f))
                            }
                        }
                    }
                    MediaType.VIDEO -> {
                        if (isNetworkPath) {
                            // If setting enabled, attempt frame extraction; otherwise show placeholder
                            if (getShowVideoThumbnails()) {
                                val data: Any = if (file.path.startsWith("content://")) {
                                    Uri.parse(file.path)
                                } else {
                                    NetworkFileData(file.path, credentialsId)
                                }
                                load(data) {
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
                        } else {
                            val data: Any = if (file.path.startsWith("content://")) {
                                Uri.parse(file.path)
                            } else {
                                File(file.path)
                            }
                            load(data) {
                                crossfade(false)
                                placeholder(R.drawable.ic_video_placeholder)
                                error(R.drawable.ic_video_error)
                                transformations(RoundedCornersTransformation(8f))
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
}

private class MediaFileDiffCallback : DiffUtil.ItemCallback<MediaFile>() {
    override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
        return oldItem == newItem
    }
}
