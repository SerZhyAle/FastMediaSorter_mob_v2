package com.sza.fastmediasorter_v2.ui.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.RoundedCornersTransformation
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.databinding.ItemMediaFileBinding
import com.sza.fastmediasorter_v2.databinding.ItemMediaFileGridBinding
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaType
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
    private var thumbnailSize: Int = 96 // Default size in dp
) : ListAdapter<MediaFile, RecyclerView.ViewHolder>(MediaFileDiffCallback()) {

    private var selectedPaths = setOf<String>()
    
    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }
    
    fun setGridMode(enabled: Boolean, iconSize: Int = 96) {
        if (isGridMode != enabled || thumbnailSize != iconSize) {
            isGridMode = enabled
            thumbnailSize = iconSize
            notifyDataSetChanged()
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
                        root.context.getColor(com.sza.fastmediasorter_v2.R.color.item_selected)
                    } else {
                        root.context.getColor(com.sza.fastmediasorter_v2.R.color.item_normal)
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
        
        private fun loadThumbnail(file: MediaFile) {
            binding.ivThumbnail.apply {
                when (file.type) {
                    MediaType.IMAGE, MediaType.GIF -> {
                        // Load image/GIF thumbnail using Coil
                        load(File(file.path)) {
                            crossfade(true)
                            placeholder(R.drawable.ic_image_placeholder)
                            error(R.drawable.ic_image_error)
                            transformations(RoundedCornersTransformation(8f))
                        }
                    }
                    MediaType.VIDEO -> {
                        // Load video first frame using Coil with video frame decoder
                        load(File(file.path)) {
                            crossfade(true)
                            placeholder(R.drawable.ic_video_placeholder)
                            error(R.drawable.ic_video_error)
                            transformations(RoundedCornersTransformation(8f))
                        }
                    }
                    MediaType.AUDIO -> {
                        // For audio files, create a bitmap with file extension
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
        
        private fun loadThumbnail(file: MediaFile) {
            binding.ivThumbnail.apply {
                when (file.type) {
                    MediaType.IMAGE, MediaType.GIF -> {
                        load(File(file.path)) {
                            crossfade(true)
                            placeholder(R.drawable.ic_image_placeholder)
                            error(R.drawable.ic_image_error)
                            transformations(RoundedCornersTransformation(8f))
                        }
                    }
                    MediaType.VIDEO -> {
                        load(File(file.path)) {
                            crossfade(true)
                            placeholder(R.drawable.ic_video_placeholder)
                            error(R.drawable.ic_video_error)
                            transformations(RoundedCornersTransformation(8f))
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
