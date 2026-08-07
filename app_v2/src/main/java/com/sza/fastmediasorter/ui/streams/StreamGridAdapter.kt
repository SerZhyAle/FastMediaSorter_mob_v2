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
import com.sza.fastmediasorter.core.menu.StreamMenuAction
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
@Suppress("LongParameterList")
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
    // S1154: atlas-preview tier - resolves a channel-preview tile for a VIDEO url with no captured
    // frame, sitting between the captured frame (wins) and the favicon fallback. Null falls through.
    private val atlasPreviewLoader: suspend (url: String) -> Bitmap? = { null },
    // S1201: logo tier - resolves a station-logo tile for any media kind, sitting between the atlas
    // preview (VIDEO only) and the 32 px favicon. This is the only picture a radio channel can get.
    private val logoTileLoader: suspend (url: String) -> Bitmap? = { null },
    private val faviconScope: CoroutineScope? = null,
) : ListAdapter<StreamSourceEntity, StreamGridAdapter.VH>(DIFF) {

    // S1142: id of the channel currently playing inline and its live now-playing track line. Only the
    // active tile renders the track; inactive tiles show the station name (ADR-4).
    private var playingId: String? = null
    private var nowPlayingLine: String? = null

    /**
     * Update the active channel + its now-playing track and repaint only the affected tiles (mirrors
     * [StreamSourceAdapter.setPlayingId] - no full rebind). Passing a null [track] clears the line.
     */
    fun setNowPlaying(id: String?, track: String?) {
        if (playingId == id && nowPlayingLine == track) return
        val previousIndex = currentList.indexOfFirst { it.id == playingId }
        playingId = id
        nowPlayingLine = track
        if (previousIndex != RecyclerView.NO_POSITION && previousIndex >= 0) {
            notifyItemChanged(previousIndex)
        }
        val currentIndex = currentList.indexOfFirst { it.id == id }
        if (currentIndex != RecyclerView.NO_POSITION && currentIndex >= 0 && currentIndex != previousIndex) {
            notifyItemChanged(currentIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemStreamGridCellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    // S1169: a status- or pin-only change repaints just that affordance - no frame re-set, favicon
    // re-decode, or overflow-menu rebuild. Empty payloads fall through to the full bind.
    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val item = getItem(position)
        payloads.forEach { payload ->
            when (payload) {
                StreamAdapterPayloads.STATUS -> {
                    holder.bindStatusOnly(item.lastPlayOutcome)
                }
                StreamAdapterPayloads.PIN -> holder.bindPinOnly(item.pinned)
            }
        }
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
            // S1142: the active tile shows `station - track`; inactive tiles show the station name only.
            val stationTitle = StreamTitleFormatter.display(source.title)
            val activeTrack = nowPlayingLine?.takeIf { source.id == playingId && it.isNotBlank() }
            binding.tvTitle.text = if (activeTrack != null) "$stationTitle - $activeTrack" else stationTitle
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
                showArtwork(frame)
                binding.root.contentDescription = context.getString(R.string.streams_grid_cell_cd, source.title)
            } else {
                // A channel with no captured frame, no atlas tile and no favicon (about a third of the
                // catalog has no favicon at all) used to render as an empty grey rectangle. Start from
                // the media-kind icon so every tile is always identifiable; the async favicon/atlas
                // paths below overwrite it when they resolve.
                showKindPlaceholder(source.mediaKind)
                binding.root.contentDescription = context.getString(R.string.streams_grid_no_frame_cd, source.title)
                if (isCaptureableVideo(source)) {
                    // S1154: try the atlas preview first (VIDEO only); it falls through to the favicon
                    // when no atlas tile exists for this url.
                    bindAtlasPreview(source.url, source.title)
                    // S0700: capture is offscreen (no cell surface); the bound-url guard now lives in
                    // repaintUrl, so a recycled tile never receives another url's frame.
                    requestCapture(source.url)
                } else {
                    // S1201: radio has no frame to capture and no preview tile, so the logo tier is its
                    // first real chance at a picture; it falls through to the favicon on a miss.
                    bindLogo(source.url, source.title)
                }
            }
            // S1142: TalkBack should announce the live track on the active tile (§3.2 accessibility).
            if (activeTrack != null) {
                binding.root.contentDescription = "${binding.root.contentDescription} - $activeTrack"
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
                    // S1424: the tile offers every row the catalog has, pin/unpin included - unlike the
                    // list row, which renders that one as its own button (S1062).
                    buildStreamMenu(menu, source) { true }
                    setOnMenuItemClickListener { item -> onStreamActionSelected(item.itemId, source) }
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
                // A favicon is a 32 px icon, not artwork: centre it at its own scale instead of
                // letting centerCrop blow it up to the full tile.
                if (tile != null) showIcon(tile)
            }
        }

        /** Full-bleed artwork: a captured frame or an atlas preview tile. */
        private fun showArtwork(bitmap: Bitmap) {
            binding.ivFrame.setPadding(0, 0, 0, 0)
            binding.ivFrame.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            ImageViewCompat.setImageTintList(binding.ivFrame, null)
            binding.ivFrame.setImageBitmap(bitmap)
        }

        /**
         * A favicon: a 32 px source, so it is inset and centred rather than cropped to the full tile -
         * upscaling it edge to edge turned every icon into a blurry smear.
         */
        private fun showIcon(bitmap: Bitmap) {
            val view = binding.ivFrame
            val pad = view.resources.getDimensionPixelSize(R.dimen.stream_grid_placeholder_padding)
            view.setPadding(pad, pad, pad, pad)
            view.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            ImageViewCompat.setImageTintList(view, null)
            view.setImageBitmap(bitmap)
        }

        /**
         * A station logo: a square tile whose own margins are transparent, so it is fitted into the
         * 16:9 cell rather than cropped, and takes the cell's colour around itself. The small inset
         * keeps it off the cell edges - unlike a captured frame, a logo is not meant to bleed.
         */
        private fun showLogo(bitmap: Bitmap) {
            val view = binding.ivFrame
            val pad = view.resources.getDimensionPixelSize(R.dimen.stream_grid_logo_padding)
            view.setPadding(pad, pad, pad, pad)
            view.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            ImageViewCompat.setImageTintList(view, null)
            view.setImageBitmap(bitmap)
        }

        /** Last-resort tile content: the media-kind glyph, tinted for the current theme. */
        private fun showKindPlaceholder(mediaKind: String) {
            val view = binding.ivFrame
            val pad = view.resources.getDimensionPixelSize(R.dimen.stream_grid_placeholder_padding)
            view.setPadding(pad, pad, pad, pad)
            view.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            view.setImageResource(if (mediaKind == "AUDIO") R.drawable.ic_audio else R.drawable.ic_video)
            ImageViewCompat.setImageTintList(
                view,
                ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(
                        view,
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
            )
        }

        /**
         * S1154: atlas-preview tier for a VIDEO tile with no captured frame. Reuses the favicon
         * scope/job + boundUrl guard so a recycled tile is never painted with a stale atlas tile. A
         * null result (no atlas or url not in it) falls through to the favicon path.
         */
        private fun bindAtlasPreview(url: String, title: String) {
            val scope = faviconScope ?: return bindFavicon(url)
            faviconJob = scope.launch {
                val tile = atlasPreviewLoader(url)
                if (boundUrl != url) return@launch
                if (tile != null) {
                    Timber.d("S1154: grid atlas-preview tile applied")
                    showArtwork(tile)
                    // The tile is a real preview, so the cell is no longer "no frame".
                    binding.root.contentDescription =
                        binding.root.context.getString(R.string.streams_grid_cell_cd, title)
                } else {
                    bindLogo(url, title)
                }
            }
        }

        /**
         * S1201: logo tier - the station's own artwork, for any media kind. Reuses the same scope/job
         * and boundUrl guard as the tiers around it. A null result (no atlas, or this url has no logo)
         * falls through to the 32 px favicon.
         */
        private fun bindLogo(url: String, title: String) {
            val scope = faviconScope ?: return bindFavicon(url)
            faviconJob = scope.launch {
                val tile = logoTileLoader(url)
                if (boundUrl != url) return@launch
                if (tile != null) {
                    showLogo(tile)
                    // A logo identifies the channel, so the cell is no longer "no frame".
                    binding.root.contentDescription =
                        binding.root.context.getString(R.string.streams_grid_cell_cd, title)
                } else {
                    bindFavicon(url)
                }
            }
        }

        // S1169: partial-rebind entry points used by the payload path - repaint one affordance only.
        fun bindStatusOnly(outcome: String?) = bindPlayStatus(outcome)

        fun bindPinOnly(pinned: Boolean) {
            binding.tvPinBadge.isVisible = pinned
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

    /**
     * S1424: composition comes from the shared catalog through [StreamMenuBinder], so this tile and
     * the list row can no longer disagree about what a channel offers (strategic ADR-1).
     */
    private fun buildStreamMenu(
        menu: Menu,
        source: StreamSourceEntity,
        canRun: (StreamMenuAction) -> Boolean,
    ) {
        val pinnedRows = currentList.filter { it.pinned }
        val facts = StreamMenuBinder.factsOf(source, pinnedRows, favoritesEnabled(), isFavorite(source))
        StreamMenuBinder.build(menu, source, pinnedRows, facts, canRun)
    }

    private fun onStreamActionSelected(itemId: Int, source: StreamSourceEntity): Boolean {
        val action = StreamMenuAction.byMenuItemId(itemId) ?: return false
        route(action, source)
        return true
    }

    /** Routing stays here rather than in the binder: these callbacks belong to this adapter. */
    private fun route(action: StreamMenuAction, source: StreamSourceEntity) {
        when (action) {
            StreamMenuAction.TOGGLE_PIN -> onPin(source)
            StreamMenuAction.MOVE_UP -> onMoveUp(source)
            StreamMenuAction.MOVE_DOWN -> onMoveDown(source)
            StreamMenuAction.MOVE_TO_TOP -> onMoveToTop(source)
            StreamMenuAction.TOGGLE_FAVORITE -> onToggleFavorite(source)
            StreamMenuAction.ADD_SHORTCUT -> onAddShortcut(source)
            StreamMenuAction.EDIT -> onEdit(source)
            StreamMenuAction.SHARE_LINK -> onShareLink(source)
            StreamMenuAction.REMOVE -> onRemove(source)
        }
    }

    private companion object {

        val DIFF = object : DiffUtil.ItemCallback<StreamSourceEntity>() {
            override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem == newItem

            // S1169: narrow a status- or pin-only change to a payload so the tile does not full-rebind.
            override fun getChangePayload(oldItem: StreamSourceEntity, newItem: StreamSourceEntity): Any? =
                streamRowChangePayload(oldItem, newItem)
        }
    }
}
