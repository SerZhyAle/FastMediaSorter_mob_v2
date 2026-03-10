package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * RecyclerView adapter for PDF thumbnail grid navigation.
 * Renders low-res page thumbnails (100px wide) for quick navigation.
 *
 * @param rendererWrapper Thread-safe PdfRenderer wrapper
 * @param pageCount Total page count
 * @param coroutineScope Scope for background rendering
 * @param currentPage Currently displayed page (for highlight)
 * @param onPageSelected Callback when user taps a thumbnail
 */
@android.annotation.SuppressLint("SetTextI18n")
class PdfThumbnailAdapter(
    private val rendererWrapper: PdfRendererWrapper,
    private val pageCount: Int,
    private val coroutineScope: CoroutineScope,
    private var currentPage: Int,
    private val onPageSelected: (Int) -> Unit
) : RecyclerView.Adapter<PdfThumbnailAdapter.ThumbnailViewHolder>() {

    // Bounded cache to prevent OOM on large PDFs (M6 fix)
    private val thumbnailCache = object : LruCache<Int, Bitmap>(MAX_CACHED_THUMBNAILS) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (!oldValue.isRecycled && oldValue != newValue) {
                oldValue.recycle()
            }
        }
        override fun sizeOf(key: Int, value: Bitmap): Int = 1
    }

    class ThumbnailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.ivThumbnail)
        val progressBar: ProgressBar = itemView.findViewById(R.id.pbThumbnailLoading)
        val pageNumber: TextView = itemView.findViewById(R.id.tvPageNumber)
        var renderJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_thumbnail, parent, false)
        return ThumbnailViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.renderJob?.cancel()
        holder.pageNumber.text = "${position + 1}"

        // Highlight current page
        holder.itemView.alpha = if (position == currentPage) 1.0f else 0.7f
        holder.itemView.setBackgroundResource(
            if (position == currentPage) R.drawable.bg_thumbnail_selected else 0
        )

        val cached = thumbnailCache.get(position)
        if (cached != null) {
            holder.imageView.setImageBitmap(cached)
            holder.progressBar.visibility = View.GONE
            holder.imageView.visibility = View.VISIBLE
        } else {
            holder.progressBar.visibility = View.VISIBLE
            holder.imageView.visibility = View.INVISIBLE

            holder.renderJob = coroutineScope.launch(Dispatchers.IO) {
                val bitmap = rendererWrapper.renderPage(position, THUMBNAIL_WIDTH, THUMBNAIL_MAX_DIM)
                withContext(Dispatchers.Main) {
                    if (bitmap != null && holder.bindingAdapterPosition == position) {
                        thumbnailCache.put(position, bitmap)
                        holder.imageView.setImageBitmap(bitmap)
                        holder.progressBar.visibility = View.GONE
                        holder.imageView.visibility = View.VISIBLE
                    } else if (bitmap != null) {
                        thumbnailCache.put(position, bitmap)
                    }
                }
            }
        }

        holder.itemView.setOnClickListener {
            val page = holder.bindingAdapterPosition
            if (page != RecyclerView.NO_POSITION) {
                onPageSelected(page)
            }
        }
    }

    override fun onViewRecycled(holder: ThumbnailViewHolder) {
        super.onViewRecycled(holder)
        holder.renderJob?.cancel()
        holder.renderJob = null
        holder.imageView.setImageBitmap(null)
    }

    override fun getItemCount(): Int = pageCount

    /**
     * Update highlighted page without full rebind.
     */
    fun setCurrentPage(page: Int) {
        val oldPage = currentPage
        currentPage = page
        notifyItemChanged(oldPage)
        notifyItemChanged(page)
    }

    /**
     * Release all cached thumbnails.
     */
    fun clearCache() {
        thumbnailCache.evictAll()
    }

    companion object {
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_MAX_DIM = 200
        const val MAX_CACHED_THUMBNAILS = 50
    }
}
