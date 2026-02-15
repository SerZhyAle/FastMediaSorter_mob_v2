package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * RecyclerView adapter for vertical PDF scroll mode.
 * Renders PDF pages on demand using [PdfRendererWrapper] with [PdfBitmapCache].
 *
 * Design:
 * - Each ViewHolder displays one page as an ImageView
 * - Pages are rendered lazily when they become visible
 * - LruCache keeps most recent pages in memory
 * - All rendering goes through PdfRendererWrapper (Mutex-protected)
 */
class PdfPageAdapter(
    private val rendererWrapper: PdfRendererWrapper,
    private val bitmapCache: PdfBitmapCache,
    private val coroutineScope: CoroutineScope,
    private val renderWidth: Int
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private var colorFilter: android.graphics.ColorFilter? = null

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.ivPdfPage)
        val progressBar: ProgressBar = itemView.findViewById(R.id.pbPdfPageLoading)
        var renderJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // Cancel any previous render job for this ViewHolder
        holder.renderJob?.cancel()

        val cached = bitmapCache.get(position)
        if (cached != null) {
            holder.imageView.setImageBitmap(cached)
            holder.imageView.colorFilter = colorFilter
            holder.progressBar.visibility = View.GONE
            holder.imageView.visibility = View.VISIBLE
            return
        }

        // Show loading state
        holder.progressBar.visibility = View.VISIBLE
        holder.imageView.visibility = View.INVISIBLE

        holder.renderJob = coroutineScope.launch(Dispatchers.IO) {
            val bitmap = rendererWrapper.renderPage(position, renderWidth)
            withContext(Dispatchers.Main) {
                if (bitmap != null && holder.bindingAdapterPosition == position) {
                    bitmapCache.put(position, bitmap)
                    holder.imageView.setImageBitmap(bitmap)
                    holder.imageView.colorFilter = colorFilter
                    holder.progressBar.visibility = View.GONE
                    holder.imageView.visibility = View.VISIBLE
                } else if (bitmap != null) {
                    // ViewHolder was recycled, cache bitmap for later
                    bitmapCache.put(position, bitmap)
                }
            }
        }
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.renderJob?.cancel()
        holder.renderJob = null
        holder.imageView.setImageBitmap(null)
    }

    override fun getItemCount(): Int = rendererWrapper.pageCount

    /**
     * Apply a color filter to all page ImageViews (for night mode, sepia).
     */
    fun setColorFilter(filter: android.graphics.ColorFilter?) {
        this.colorFilter = filter
        notifyDataSetChanged()
    }

    /**
     * Invalidate all cached bitmaps and force re-render.
     */
    fun invalidateCache() {
        bitmapCache.clear()
        notifyDataSetChanged()
    }
}
