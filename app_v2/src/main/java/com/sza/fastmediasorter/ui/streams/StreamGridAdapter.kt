package com.sza.fastmediasorter.ui.streams

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ItemStreamGridCellBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * S0675: renders the stream catalog as grid tiles. Each cell shows the cached current frame via
 * [frameProvider] (reads [com.sza.fastmediasorter.data.repository.streams.StreamFrameCache]); on a miss
 * it shows the favicon/placeholder fallback and, for http(s) VIDEO sources, enqueues a snapshot via
 * [requestCapture]. The favicon plumbing mirrors [StreamSourceAdapter] (rebind-safe with a boundUrl
 * guard). [repaintUrl] lets the snapshot engine's `onCaptured` callback refresh just one tile.
 */
class StreamGridAdapter(
    private val onPlay: (StreamSourceEntity) -> Unit,
    private val frameProvider: (url: String) -> Bitmap?,
    private val requestCapture: (url: String, textureViewProvider: () -> TextureView?) -> Unit,
    private val faviconResolver: (String) -> Int? = { null },
    private val faviconTileLoader: suspend (Int) -> Bitmap? = { null },
    private val faviconScope: CoroutineScope? = null,
) : ListAdapter<StreamSourceEntity, StreamGridAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemStreamGridCellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: VH) {
        holder.cancelFaviconLoad()
    }

    /** Repaint the tile bound to [url] (the snapshot engine just cached a fresh frame for it). */
    fun repaintUrl(url: String) {
        val index = currentList.indexOfFirst { it.url == url }
        if (index != RecyclerView.NO_POSITION && index >= 0) notifyItemChanged(index)
    }

    inner class VH(private val binding: ItemStreamGridCellBinding) : RecyclerView.ViewHolder(binding.root) {

        private var boundUrl: String? = null
        private var faviconJob: Job? = null

        fun cancelFaviconLoad() {
            faviconJob?.cancel()
            faviconJob = null
        }

        fun bind(source: StreamSourceEntity) {
            val context = binding.root.context
            boundUrl = source.url
            cancelFaviconLoad()
            binding.tvTitle.text = source.title
            binding.root.setOnClickListener { onPlay(source) }

            val frame = frameProvider(source.url)
            if (frame != null) {
                binding.ivFrame.setImageBitmap(frame)
                binding.root.contentDescription = context.getString(R.string.streams_grid_cell_cd, source.title)
            } else {
                binding.ivFrame.setImageBitmap(null)
                binding.root.contentDescription = context.getString(R.string.streams_grid_no_frame_cd, source.title)
                bindFavicon(source.url)
                if (isCaptureableVideo(source)) {
                    requestCapture(source.url) { if (boundUrl == source.url) binding.textureCapture else null }
                }
            }
        }

        /**
         * Mirror of [StreamSourceAdapter.bind]'s favicon path: async, rebind-safe decode of the atlas
         * tile painted into the frame ImageView as the no-frame fallback.
         */
        private fun bindFavicon(url: String) {
            val scope = faviconScope
            val index = faviconResolver(url)
            if (scope == null || index == null) return
            faviconJob = scope.launch {
                val tile = faviconTileLoader(index)
                if (boundUrl != url) return@launch
                if (tile != null) binding.ivFrame.setImageBitmap(tile)
            }
        }
    }

    private fun isCaptureableVideo(source: StreamSourceEntity): Boolean =
        source.mediaKind == "VIDEO" &&
            (source.url.startsWith("http://") || source.url.startsWith("https://"))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<StreamSourceEntity>() {
            override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem == newItem
        }
    }
}
