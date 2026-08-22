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
        // S1169: repaint only the label/favicon slot via a payload - a full-list refresh blanked
        // and re-decoded every chip on each rotation (bind used to pre-clear the image).
        notifyItemRangeChanged(0, itemCount, PAYLOAD_LABELS)
    }

    /** Re-binds visible chips once the favicon coords/atlas finish loading after the list was shown. */
    fun refreshFavicons() = notifyItemRangeChanged(0, itemCount, PAYLOAD_LABELS)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMainStreamChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    // S1169: PAYLOAD_LABELS re-applies only label visibility + favicon without resetting click listeners.
    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_LABELS)) {
            holder.applyLabelsAndFavicon(getItem(position))
        } else {
            onBindViewHolder(holder, position)
        }
    }

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
            binding.btnChannelMenu.setOnClickListener { onChannelOverflow(source, binding.btnChannelMenu) }
            applyLabelsAndFavicon(source, fullTitle)
        }

        /**
         * S1169: label-visibility + favicon slot only, no click-listener reset. Called by the full [bind]
         * and by the PAYLOAD_LABELS partial rebind. The image is NOT pre-cleared: a chip keeps its current
         * favicon until the async tile lands (guarded by [boundUrl]), so a rotation never blanks it.
         */
        fun applyLabelsAndFavicon(
            source: StreamSourceEntity,
            fullTitle: String = StreamTitleFormatter.display(source.title),
        ) {
            boundUrl = source.url
            cancelFaviconLoad()
            binding.btnChannelMenu.visibility = if (showLabels) View.VISIBLE else View.GONE
            binding.tvChannelLabel.text = fullTitle.take(SHORT_NAME_MAX_CHARS)
            val index = faviconResolver(source.url)
            if (showLabels) {
                // Wide window: thumbnail (if any) plus the short label.
                binding.tvChannelLabel.visibility = View.VISIBLE
                loadFavicon(index, source.url, fallbackToLabel = false)
            } else if (index == null) {
                // Compact window, no favicon: fall back to the short label so the chip is identifiable.
                binding.ivChannelFavicon.setImageDrawable(null)
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

        // S1169: partial-rebind payload for a label/favicon refresh (rotation / atlas load).
        const val PAYLOAD_LABELS = "panel_labels"

        val DIFF = object : DiffUtil.ItemCallback<StreamSourceEntity>() {
            override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem == newItem
        }
    }
}
