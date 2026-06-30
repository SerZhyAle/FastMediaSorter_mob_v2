package com.sza.fastmediasorter.ui.streams

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ItemStreamGridCellBinding
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * S0675: renders the stream catalog as grid tiles. Each cell shows the cached current frame via
 * [frameProvider] (reads [com.sza.fastmediasorter.data.repository.streams.StreamFrameCache]); on a miss
 * it shows the favicon/placeholder fallback and, for http(s) VIDEO sources, enqueues a snapshot via
 * [requestCapture]. The favicon plumbing mirrors [StreamSourceAdapter] (rebind-safe with a boundUrl
 * guard). [repaintUrl] lets the snapshot engine's `onCaptured` callback refresh just one tile.
 *
 * S0700: the snapshot is captured offscreen by [StreamFrameSnapshotManager] (its own ImageReader
 * surface, decoupled from this cell's views), so [requestCapture] no longer hands over a TextureView.
 * On completion the manager calls back into [repaintUrl], which re-binds only the tile whose bound url
 * matches - a recycled/scrolled cell that now shows a different url is never repainted with a stale
 * frame, and the cell carries no live capture surface.
 *
 * S0701: each tile now also carries the same play-status dot (bottom-left) and overflow menu (top-right)
 * as the list row, and S0695's long-press pin/unpin toggle - so grid mode is not a feature-poor sibling
 * of the list. The secondary-command callbacks mirror [StreamSourceAdapter].
 */
class StreamGridAdapter(
    private val onPlay: (StreamSourceEntity) -> Unit,
    private val onPin: (StreamSourceEntity) -> Unit,
    private val onRemove: (StreamSourceEntity) -> Unit,
    private val onAddShortcut: (StreamSourceEntity) -> Unit,
    private val onEdit: (StreamSourceEntity) -> Unit,
    private val onShareLink: (StreamSourceEntity) -> Unit,
    private val frameProvider: (url: String) -> Bitmap?,
    private val requestCapture: (url: String) -> Unit,
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
            binding.tvTitle.text = StreamTitleFormatter.display(source.title)
            bindPlayStatus(source.lastPlayOutcome)
            binding.root.setOnClickListener { onPlay(source) }
            // S0695: long-press toggles pin/unpin on a tile too (mirrors the list row).
            binding.root.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onPin(source)
                true
            }
            binding.btnGridOverflow.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menu.add(Menu.NONE, ID_ADD_SHORTCUT, 0, R.string.streams_add_to_home_screen)
                    // Edit is offered only for user-added channels (mirrors the list row, S0660 §6.4).
                    if (source.sourceOrigin == "MANUAL") {
                        menu.add(Menu.NONE, ID_EDIT, 1, R.string.streams_edit)
                    }
                    menu.add(Menu.NONE, ID_SHARE_LINK, 2, R.string.streams_send_link)
                    menu.add(Menu.NONE, ID_REMOVE, 3, R.string.streams_remove)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            ID_ADD_SHORTCUT -> { onAddShortcut(source); true }
                            ID_EDIT -> { onEdit(source); true }
                            ID_SHARE_LINK -> { onShareLink(source); true }
                            ID_REMOVE -> { onRemove(source); true }
                            else -> false
                        }
                    }
                    show()
                }
            }

            val frame = frameProvider(source.url)
            if (frame != null) {
                binding.ivFrame.setImageBitmap(frame)
                binding.root.contentDescription = context.getString(R.string.streams_grid_cell_cd, source.title)
            } else {
                binding.ivFrame.setImageBitmap(null)
                binding.root.contentDescription = context.getString(R.string.streams_grid_no_frame_cd, source.title)
                bindFavicon(source.url)
                if (isCaptureableVideo(source)) {
                    // S0700: capture is offscreen (no cell surface); the bound-url guard now lives in
                    // repaintUrl, so a recycled tile never receives another url's frame.
                    requestCapture(source.url)
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

        /** S0701: same tri-state play-status mapping as [StreamSourceAdapter.bindPlayStatus]. */
        private fun bindPlayStatus(outcome: String?) {
            val (iconRes, colorRes, descRes) = when (outcome) {
                RecordStreamPlayOutcomeUseCase.OUTCOME_OK ->
                    Triple(R.drawable.ic_stream_status_ok, R.color.stream_status_ok, R.string.stream_status_ok)
                RecordStreamPlayOutcomeUseCase.OUTCOME_FAIL ->
                    Triple(R.drawable.ic_stream_status_failed, R.color.stream_status_failed, R.string.stream_status_failed)
                else ->
                    Triple(R.drawable.ic_stream_status_unknown, R.color.stream_status_unknown, R.string.stream_status_unknown)
            }
            val context = binding.ivGridStatus.context
            binding.ivGridStatus.setImageResource(iconRes)
            ImageViewCompat.setImageTintList(
                binding.ivGridStatus,
                ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))
            )
            binding.ivGridStatus.contentDescription = context.getString(descRes)
        }
    }

    private fun isCaptureableVideo(source: StreamSourceEntity): Boolean =
        source.mediaKind == "VIDEO" &&
            (source.url.startsWith("http://") || source.url.startsWith("https://"))

    private companion object {
        const val ID_ADD_SHORTCUT = 1
        const val ID_REMOVE = 2
        const val ID_EDIT = 3
        const val ID_SHARE_LINK = 4

        val DIFF = object : DiffUtil.ItemCallback<StreamSourceEntity>() {
            override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem == newItem
        }
    }
}
