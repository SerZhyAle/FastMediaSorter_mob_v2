package com.sza.fastmediasorter.ui.streams

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import timber.log.Timber

/**
 * S0675: renders the stream catalog as grid tiles. Each cell shows the cached current frame via
 * [frameProvider] (reads [com.sza.fastmediasorter.data.repository.streams.StreamFrameCache]); on a miss
 * it shows the favicon/placeholder fallback and, for http(s) VIDEO sources, enqueues a snapshot via
 * [requestCapture]. The favicon plumbing mirrors [StreamSourceAdapter] (rebind-safe with a boundUrl
 * guard). [repaintUrl] lets the snapshot engine's `onCaptured` callback refresh just one tile.
 *
 * S0700/S0933: the snapshot is captured offscreen by [StreamFrameSnapshotManager] (into its own
 * window-attached off-screen TextureView, decoupled from this cell's views), so [requestCapture] never
 * hands over this cell's own views. On completion the manager calls back into [repaintUrl], which
 * re-binds only the tile whose bound url
 * matches - a recycled/scrolled cell that now shows a different url is never repainted with a stale
 * frame, and the cell carries no live capture surface.
 *
 * S0701: each tile now also carries the same play-status dot (bottom-left) and overflow menu (top-right)
 * as the list row, and S0695's long-press pin/unpin toggle - so grid mode is not a feature-poor sibling
 * of the list. The secondary-command callbacks mirror [StreamSourceAdapter].
 *
 * S1062: the overflow menu leads with an explicit Pin/Unpin item (label reflects [StreamSourceEntity.pinned])
 * and a pinned tile shows a small red top-left badge - a non-clickable indicator, since pin state is only
 * changed through the menu.
 */
class StreamGridAdapter(
    private val onPlay: (StreamSourceEntity) -> Unit,
    private val onPin: (StreamSourceEntity) -> Unit,
    private val onRemove: (StreamSourceEntity) -> Unit,
    // S0938: reorder a pinned channel within the pinned set (mirrors StreamSourceAdapter). Shown only
    // for a pinned tile; disabled at the edges of the pinned block.
    private val onMoveUp: (StreamSourceEntity) -> Unit = {},
    private val onMoveDown: (StreamSourceEntity) -> Unit = {},
    private val onMoveToTop: (StreamSourceEntity) -> Unit = {},
    private val onAddShortcut: (StreamSourceEntity) -> Unit,
    private val onEdit: (StreamSourceEntity) -> Unit,
    private val onShareLink: (StreamSourceEntity) -> Unit,
    // S0783: mirrors StreamSourceAdapter - add/remove the channel from Favorites (feature-gated). The
    // gate + label state are pulled lazily when the menu opens.
    private val onToggleFavorite: (StreamSourceEntity) -> Unit = {},
    private val favoritesEnabled: () -> Boolean = { false },
    private val isFavorite: (StreamSourceEntity) -> Boolean = { false },
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
            // S1062: red top-left indicator for a pinned tile (menu-driven only, not tappable).
            binding.tvPinBadge.isVisible = source.pinned
            binding.root.setOnClickListener { onPlay(source) }
            // S0695: long-press toggles pin/unpin on a tile too (mirrors the list row).
            binding.root.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onPin(source)
                true
            }
            bindSecondaryClickOverflow()
            bindOverflowMenu(source)

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

        // S1111: mouse right-click opens the tile action menu (mirrors StreamSourceAdapter list parity,
        // Rule 16). Extracted from bind() to keep that method within detekt length/complexity limits.
        // A per-tile handler is required: the activity-level mouse fallback only targets the focused view.
        private fun bindSecondaryClickOverflow() {
            binding.root.setOnGenericMotionListener { _, event ->
                if (event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                    event.buttonState == MotionEvent.BUTTON_SECONDARY
                ) {
                    Timber.d("S1111: grid tile right-click -> overflow")
                    binding.btnGridOverflow.performClick()
                    true
                } else {
                    false
                }
            }
        }

        // S1062/S0938/S0783: builds the tile overflow menu (pin/unpin, reorder, favorite, shortcut,
        // edit, share, remove). Extracted from bind() so that method stays within detekt complexity
        // limits after S1111 added the right-click affordance. Mirrors StreamSourceAdapter's menu.
        private fun bindOverflowMenu(source: StreamSourceEntity) {
            binding.btnGridOverflow.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    // S1062: pin/unpin is the topmost menu item; the label reflects the current state and
                    // reuses the already-injected onPin toggle (unpins if pinned, else pins).
                    val pinLabel = if (source.pinned) R.string.streams_unpin else R.string.streams_pin
                    menu.add(Menu.NONE, ID_TOGGLE_PIN, Menu.NONE, pinLabel)
                    // S0938: reorder commands lead the menu for a pinned tile when more than one channel
                    // is pinned. Edge commands are disabled at the ends of the pinned block.
                    val pinnedRows = currentList.filter { it.pinned }
                    if (source.pinned && pinnedRows.size > 1) {
                        val pinnedIndex = pinnedRows.indexOfFirst { it.id == source.id }
                        menu.add(Menu.NONE, ID_MOVE_UP, Menu.NONE, R.string.streams_move_up)
                            .isEnabled = pinnedIndex > 0
                        menu.add(Menu.NONE, ID_MOVE_DOWN, Menu.NONE, R.string.streams_move_down)
                            .isEnabled = pinnedIndex < pinnedRows.lastIndex
                        menu.add(Menu.NONE, ID_MOVE_TO_TOP, Menu.NONE, R.string.streams_move_to_top)
                            .isEnabled = pinnedIndex > 0
                    }
                    // S0783: Favorites toggle leads the menu when the feature is on; label flips on state.
                    if (favoritesEnabled()) {
                        val favLabel = if (isFavorite(source)) {
                            R.string.streams_remove_from_favorites
                        } else {
                            R.string.streams_add_to_favorites
                        }
                        // Order = Menu.NONE on every item, so the menu follows add-order (favorite,
                        // shortcut, edit, share, remove) without magic order literals.
                        menu.add(Menu.NONE, ID_TOGGLE_FAVORITE, Menu.NONE, favLabel)
                    }
                    menu.add(Menu.NONE, ID_ADD_SHORTCUT, Menu.NONE, R.string.streams_add_to_home_screen)
                    // Edit is offered only for user-added channels (mirrors the list row, S0660 §6.4).
                    if (source.sourceOrigin == "MANUAL") {
                        menu.add(Menu.NONE, ID_EDIT, Menu.NONE, R.string.streams_edit)
                    }
                    menu.add(Menu.NONE, ID_SHARE_LINK, Menu.NONE, R.string.streams_send_link)
                    menu.add(Menu.NONE, ID_REMOVE, Menu.NONE, R.string.streams_remove)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            ID_TOGGLE_PIN -> { onPin(source); true }
                            ID_MOVE_UP -> { onMoveUp(source); true }
                            ID_MOVE_DOWN -> { onMoveDown(source); true }
                            ID_MOVE_TO_TOP -> { onMoveToTop(source); true }
                            ID_TOGGLE_FAVORITE -> { onToggleFavorite(source); true }
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
        const val ID_TOGGLE_FAVORITE = 5 // S0783
        const val ID_MOVE_UP = 6 // S0938
        const val ID_MOVE_DOWN = 7 // S0938
        const val ID_MOVE_TO_TOP = 8 // S0938
        const val ID_TOGGLE_PIN = 9 // S1062

        val DIFF = object : DiffUtil.ItemCallback<StreamSourceEntity>() {
            override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem == newItem
        }
    }
}
