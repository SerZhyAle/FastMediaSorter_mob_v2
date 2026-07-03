package com.sza.fastmediasorter.ui.main.helpers

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ItemMainStreamChannelBinding
import com.sza.fastmediasorter.ui.streams.StreamTitleFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * S0756: pinned stream channels on the main-window streams panel. Each chip shows a favicon thumbnail
 * and/or a short (<=10 char) name; which of the two is visible is chosen by [showLabels] (driven by the
 * window-width bool, not orientation) with a text fallback when a channel has no favicon. The favicon
 * decode is async and rebind-safe, mirroring [com.sza.fastmediasorter.ui.streams.StreamSourceAdapter].
 *
 * The full channel title is always the chip's contentDescription, so TalkBack reads the real name even
 * when only a thumbnail or the truncated label is shown (S0756 accessibility decision).
 */
class StreamPanelChannelAdapter(
    private val onChannelClick: (StreamSourceEntity) -> Unit,
    // S0770: open the per-channel menu (Open / Open-in-new-window / Remove) anchored to the given view.
    private val onChannelOverflow: (StreamSourceEntity, View) -> Unit,
    private val faviconResolver: (String) -> Int? = { null },
    private val faviconTileLoader: suspend (Int) -> Bitmap? = { null },
    private val faviconScope: CoroutineScope? = null,
) : ListAdapter<StreamSourceEntity, StreamPanelChannelAdapter.VH>(DIFF) {

    private var showLabels: Boolean = false

    /** Repaints chips when the available-width label rule flips (rotation / window resize). */
    fun setShowLabels(value: Boolean) {
        if (showLabels == value) return
        showLabels = value
        notifyDataSetChanged()
    }

    /** Re-binds visible chips once the favicon coords/atlas finish loading after the list was shown. */
    fun refreshFavicons() = notifyDataSetChanged()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMainStreamChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: VH) {
        holder.cancelFaviconLoad()
    }

    inner class VH(private val binding: ItemMainStreamChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        private var boundUrl: String? = null
        private var faviconJob: Job? = null

        fun cancelFaviconLoad() {
            faviconJob?.cancel()
            faviconJob = null
        }

        fun bind(source: StreamSourceEntity) {
            val fullTitle = StreamTitleFormatter.display(source.title)
            binding.channelRoot.contentDescription = fullTitle
            binding.channelRoot.setOnClickListener { onChannelClick(source) }
            // S0770: three-dots visible in label mode; long-press on the body covers compact mode.
            binding.channelRoot.setOnLongClickListener {
                onChannelOverflow(source, binding.channelRoot)
                true
            }
            binding.btnChannelMenu.visibility = if (showLabels) View.VISIBLE else View.GONE
            binding.btnChannelMenu.setOnClickListener { onChannelOverflow(source, binding.btnChannelMenu) }
            binding.tvChannelLabel.text = fullTitle.take(SHORT_NAME_MAX_CHARS)

            boundUrl = source.url
            cancelFaviconLoad()
            binding.ivChannelFavicon.setImageDrawable(null)
            val index = faviconResolver(source.url)

            if (showLabels) {
                // Wide window: thumbnail (if any) plus the short label.
                binding.tvChannelLabel.visibility = View.VISIBLE
                loadFavicon(index, source.url, fallbackToLabel = false)
            } else if (index == null) {
                // Compact window, no favicon: fall back to the short label so the chip is identifiable.
                binding.ivChannelFavicon.visibility = View.GONE
                binding.tvChannelLabel.visibility = View.VISIBLE
            } else {
                // Compact window with a favicon: thumbnail only, label hidden (revealed only if decode fails).
                binding.tvChannelLabel.visibility = View.GONE
                loadFavicon(index, source.url, fallbackToLabel = true)
            }
        }

        private fun loadFavicon(index: Int?, url: String, fallbackToLabel: Boolean) {
            val scope = faviconScope
            if (scope == null || index == null) {
                binding.ivChannelFavicon.visibility = View.GONE
                if (fallbackToLabel) binding.tvChannelLabel.visibility = View.VISIBLE
                return
            }
            faviconJob = scope.launch {
                val tile = faviconTileLoader(index)
                if (boundUrl != url) return@launch
                if (tile != null) {
                    binding.ivChannelFavicon.setImageBitmap(tile)
                    binding.ivChannelFavicon.visibility = View.VISIBLE
                } else {
                    binding.ivChannelFavicon.visibility = View.GONE
                    if (fallbackToLabel) binding.tvChannelLabel.visibility = View.VISIBLE
                }
            }
        }
    }

    private companion object {
        const val SHORT_NAME_MAX_CHARS = 10

        val DIFF = object : DiffUtil.ItemCallback<StreamSourceEntity>() {
            override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem == newItem
        }
    }
}
