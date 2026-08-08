package com.sza.fastmediasorter.ui.streams

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.menu.StreamMenuAction
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ItemStreamSourceBinding
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.fastmediasorter.ui.browse.InlinePlaybackAnimator
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.streams.helpers.StreamTopicRubricCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Renders the stream catalog. Row tap launches playback ([onPlay]); the pin affordance promotes the
 * source locally ([onPin]); long-press toggles pin/unpin (S0695). The now-playing indicator tracks the
 * id passed to [setPlayingId] so the inline-audio row is marked without rebuilding the list.
 *
 * S0660: the overflow (`три точки`) is the canonical surface for secondary channel commands - add to
 * home screen, edit (manual channels only), send link, remove. S0695 moved the destructive remove out
 * of long-press (now pin-toggle) so removal is reachable only through the explicit overflow menu.
 */
// The row needs every host command (play/pin/remove/shortcut/edit/share/favorite) plus the favicon
// plumbing as plain collaborators; folding them into a config object would add indirection without
// clarity, mirroring StreamGridAdapter/StreamGridModeManager.
@Suppress("LongParameterList")
class StreamSourceAdapter(
    private val onPlay: (StreamSourceEntity) -> Unit,
    private val onPin: (StreamSourceEntity) -> Unit,
    private val onRemove: (StreamSourceEntity) -> Unit,
    // S0938: reorder a pinned channel within the pinned set. Shown only for a pinned row; disabled at
    // the edges (top row: no up / no to-top; bottom row: no down).
    private val onMoveUp: (StreamSourceEntity) -> Unit = {},
    private val onMoveDown: (StreamSourceEntity) -> Unit = {},
    private val onMoveToTop: (StreamSourceEntity) -> Unit = {},
    private val onAddShortcut: (StreamSourceEntity) -> Unit,
    private val onEdit: (StreamSourceEntity) -> Unit,
    private val onShareLink: (StreamSourceEntity) -> Unit,
    // S1474: opens the "about this channel" window; wired by the streams screen through StreamInfoDialogManager.
    private val onAboutChannel: (StreamSourceEntity) -> Unit = {},
    // S0783: add/remove the channel from the shared Favorites. Independent of pin. The overflow item is
    // shown only when [favoritesEnabled] returns true, and its label flips on [isFavorite]. Both are
    // pulled lazily when the menu opens, so they always reflect current settings/DB without a row rebind.
    private val onToggleFavorite: (StreamSourceEntity) -> Unit = {},
    private val favoritesEnabled: () -> Boolean = { false },
    private val isFavorite: (StreamSourceEntity) -> Boolean = { false },
    // S0668: favicon plumbing kept as plain collaborators (not DI) so the adapter stays test-friendly.
    // faviconResolver maps a url -> its sprite-atlas tile index (null = no favicon -> empty slot);
    // faviconTileLoader decodes that index into a 32 px bitmap off the main thread (the slicer's
    // tileFor). The defaults render every row with an empty slot, so callers without an atlas still work.
    private val faviconResolver: (String) -> Int? = { null },
    private val faviconTileLoader: suspend (Int) -> Bitmap? = { null },
    private val faviconScope: CoroutineScope? = null,
) : ListAdapter<StreamSourceEntity, StreamSourceAdapter.VH>(DIFF) {

    private var playingId: String? = null

    /**
     * S1502: play outcome per channel id, pushed in from its own Flow instead of read off the row.
     * Only the rows whose outcome actually moved are repainted, and only their status bullet.
     */
    var playOutcomes: Map<String, String> = emptyMap()
        set(value) {
            if (field == value) return
            val previous = field
            field = value
            currentList.forEachIndexed { index, item ->
                if (previous[item.id] != value[item.id]) {
                    notifyItemChanged(index, StreamAdapterPayloads.STATUS)
                }
            }
        }

    /** Marks (or clears) the row currently playing inline audio, repainting only the affected rows. */
    fun setPlayingId(id: String?) {
        if (playingId == id) return
        val previous = playingId
        playingId = id
        currentList.forEachIndexed { index, item ->
            if (item.id == previous || item.id == id) notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemStreamSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), getItem(position).id == playingId)
    }

    // S1169: a status- or pin-only change repaints just that affordance - no favicon re-decode or
    // overflow-menu rebuild. Empty payloads fall through to the full bind.
    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val item = getItem(position)
        payloads.forEach { payload ->
            when (payload) {
                StreamAdapterPayloads.STATUS -> holder.bindStatusOnly(playOutcomes[item.id])
                StreamAdapterPayloads.PIN -> holder.bindPinOnly(item.pinned)
            }
        }
    }

    override fun onViewRecycled(holder: VH) {
        // S0587/S0579: a recycled row must not keep spinning - cancel its rotation animator.
        holder.stopPlaybackAnimation()
        // S0668: also cancel any in-flight favicon decode so it cannot paint onto the reused holder.
        holder.cancelFaviconLoad()
    }

    inner class VH(private val binding: ItemStreamSourceBinding) : RecyclerView.ViewHolder(binding.root) {
        // S0579: reuse the file-browser inline playback rotation on the row kind icon.
        private val playbackAnimator = InlinePlaybackAnimator(binding.ivKind)

        // S0668: the url this holder is currently bound to, so an async favicon load that resolves after
        // the row was recycled/rebound is dropped instead of painting a stale tile (mirrors setPlayingId).
        private var boundUrl: String? = null
        private var faviconJob: Job? = null

        fun stopPlaybackAnimation() = playbackAnimator.stopAll()

        /** S0668: cancel the in-flight favicon load when the row is recycled. */
        fun cancelFaviconLoad() {
            faviconJob?.cancel()
            faviconJob = null
        }

        fun bind(source: StreamSourceEntity, isPlaying: Boolean) {
            binding.tvTitle.text = StreamTitleFormatter.display(source.title)
            binding.tvUrl.text = source.url
            binding.ivKind.setImageResource(kindIcon(source.mediaKind))
            bindFavicon(source)
            bindPlayStatus(playOutcomes[source.id])
            binding.tvNowPlaying.visibility = if (isPlaying) View.VISIBLE else View.GONE
            if (isPlaying) playbackAnimator.startNote() else playbackAnimator.stopNote()
            // S1117: region-restriction badge leads the chip row when the catalog flagged this row "geo".
            bindGeoChip(binding.tvGeo, source.access)
            // S1477: the row shows the localized rubric; the catalog id behind it stays English.
            bindChip(binding.tvTopic, StreamTopicRubricCatalog.label(binding.root.context, source.topic))
            // Country is shown before language as flag+code (e.g. "🇺🇦 UA"); manual rows leave it null.
            bindChip(binding.tvCountry, countryChipText(binding.tvCountry, source.country))
            bindChip(binding.tvLanguage, source.language)
            val anyChipVisible = listOf(
                binding.tvGeo,
                binding.tvTopic,
                binding.tvCountry,
                binding.tvLanguage
            ).any { it.isVisible }
            binding.chipRow.visibility = if (anyChipVisible) View.VISIBLE else View.GONE
            bindPinState(source.pinned)
            binding.root.setOnClickListener { onPlay(source) }
            // S0695: long-press toggles pin/unpin (the destructive remove now lives only in the overflow
            // menu). The pin icon flips on rebind, so the gesture is self-evident without a confirmation.
            binding.root.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onPin(source)
                true
            }
            // Mouse right-click opens the row action menu (the visible overflow affordance), matching
            // the file-browser/resource-list right-click pattern. A per-row handler is required: the
            // activity-level mouse fallback only targets the focused view, never the row under the cursor.
            binding.root.setOnGenericMotionListener { _, event ->
                if (event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                    event.buttonState == MotionEvent.BUTTON_SECONDARY
                ) {
                    binding.btnOverflow.performClick()
                    true
                } else {
                    false
                }
            }
            binding.btnPin.setOnClickListener { onPin(source) }
            binding.btnOverflow.setOnClickListener { anchor -> showOverflowMenu(source, anchor) }
        }

        /**
         * S0660: build and show the per-row overflow menu (reorder/favorite/shortcut/edit/share/remove).
         * Extracted from [bind] so that method's cyclomatic complexity stays within the detekt budget;
         * the menu content still resolves current state (pins/favorites) lazily at open time.
         */
        private fun showOverflowMenu(source: StreamSourceEntity, anchor: View) {
            PopupMenu(anchor.context, anchor).apply {
                // S1424: which rows exist comes from the shared catalog; pin/unpin is withheld here
                // because this row renders it as its own button (S1062), not as a menu entry.
                buildStreamMenu(menu, source) { it != StreamMenuAction.TOGGLE_PIN }
                setOnMenuItemClickListener { item -> onStreamActionSelected(item.itemId, source) }
                show()
            }
        }

        /**
         * S0668: render the leading channel-logo thumbnail sliced from the favicon sprite-atlas. The
         * decode is async and rebind-safe: it is cancelled on rebind and the result is dropped unless the
         * holder is still bound to the same url (a recycled row must not flash a previous channel's logo).
         *
         * S0785: when the url has no tile (null index or a decode that yields nothing), fall back to the
         * channel's country flag in the same slot so the row keeps its alignment instead of collapsing;
         * rows carrying neither a tile nor a country still leave the slot empty (owner decision S0668).
         */
        private fun bindFavicon(source: StreamSourceEntity) {
            val url = source.url
            boundUrl = url
            cancelFaviconLoad()
            binding.ivFavicon.setImageDrawable(null)
            binding.ivFavicon.visibility = View.GONE
            binding.tvFaviconFlag.visibility = View.GONE
            val scope = faviconScope
            val index = faviconResolver(url)
            if (scope == null || index == null) {
                showCountryFlagFallback(source.country)
                return
            }
            faviconJob = scope.launch {
                val tile = faviconTileLoader(index)
                // The holder may have been rebound to another row while decoding - drop a stale result.
                if (boundUrl != url) return@launch
                if (tile != null) {
                    binding.tvFaviconFlag.visibility = View.GONE
                    binding.ivFavicon.setImageBitmap(tile)
                    binding.ivFavicon.visibility = View.VISIBLE
                } else {
                    showCountryFlagFallback(source.country)
                }
            }
        }

        /** S0785: put the stream's country flag in the leading slot when no favicon tile is available. */
        private fun showCountryFlagFallback(country: String?) {
            val code = country?.trim()?.takeIf { it.isNotBlank() }
            if (code == null || !LanguageFlagFormatter.applyCountryFlagGlyph(binding.tvFaviconFlag, code)) {
                binding.tvFaviconFlag.visibility = View.GONE
                return
            }
            binding.ivFavicon.visibility = View.GONE
            binding.tvFaviconFlag.visibility = View.VISIBLE
        }

        // S1169: partial-rebind entry points used by the payload path - repaint one affordance only.
        fun bindStatusOnly(outcome: String?) = bindPlayStatus(outcome)

        fun bindPinOnly(pinned: Boolean) = bindPinState(pinned)

        /**
         * S0593: local play-status bullet. Shape AND colour both encode the state (TalkBack reads the
         * contentDescription), so the meaning survives colour-blindness and greyscale: a hollow ring =
         * not played yet, a check = played OK here, an exclamation = the last attempt failed.
         */
        private fun bindPlayStatus(outcome: String?) {
            val (iconRes, colorRes, descRes) = when (outcome) {
                RecordStreamPlayOutcomeUseCase.OUTCOME_OK ->
                    Triple(R.drawable.ic_stream_status_ok, R.color.stream_status_ok, R.string.stream_status_ok)
                RecordStreamPlayOutcomeUseCase.OUTCOME_FAIL ->
                    Triple(R.drawable.ic_stream_status_failed, R.color.stream_status_failed, R.string.stream_status_failed)
                else ->
                    Triple(R.drawable.ic_stream_status_unknown, R.color.stream_status_unknown, R.string.stream_status_unknown)
            }
            val context = binding.ivPlayStatus.context
            binding.ivPlayStatus.setImageResource(iconRes)
            ImageViewCompat.setImageTintList(
                binding.ivPlayStatus,
                ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))
            )
            binding.ivPlayStatus.contentDescription = context.getString(descRes)
        }

        /** S0588: a pinned row shows the filled accent pin; others the neutral outline. */
        private fun bindPinState(pinned: Boolean) {
            binding.btnPin.setImageResource(if (pinned) R.drawable.ic_pin else R.drawable.ic_pin_outline)
            val attr = if (pinned) {
                androidx.appcompat.R.attr.colorPrimary
            } else {
                androidx.appcompat.R.attr.colorControlNormal
            }
            ImageViewCompat.setImageTintList(
                binding.btnPin,
                ColorStateList.valueOf(MaterialColors.getColor(binding.btnPin, attr))
            )
        }

        /**
         * Catalog country is an ISO 3166-1 alpha-2 code; render it as "flag code" (e.g. "🇺🇦 UA"). RU/BY
         * use the custom image-flag contract from [LanguageFlagFormatter]; unmapped values degrade to the
         * bare code, mirroring [bindChip].
         */
        private fun countryChipText(view: TextView, country: String?): CharSequence? {
            val code = country?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return LanguageFlagFormatter.compactCountryCodeLabel(view, code)
        }

        /**
         * S1117: region-restriction badge. Shown only when the catalog flagged the row `access = "geo"`
         * (HTTP 403/451 from the maintainer's network - may still play in the user's own region). Icon +
         * text, not colour alone; the full hint lives in contentDescription for TalkBack.
         */
        private fun bindGeoChip(view: TextView, access: String?) {
            if (!access.equals(ACCESS_GEO, ignoreCase = true)) {
                view.visibility = View.GONE
                return
            }
            val context = view.context
            view.text = context.getString(R.string.stream_access_geo)
            view.contentDescription = context.getString(R.string.stream_access_geo_desc)
            view.visibility = View.VISIBLE
        }

        /** Catalog metadata only - manual/imported rows leave topic/language null, so hide the chip. */
        private fun bindChip(view: TextView, value: CharSequence?) {
            if (value.isNullOrBlank()) {
                view.visibility = View.GONE
            } else {
                view.text = value
                view.visibility = View.VISIBLE
            }
        }
    }

    private fun kindIcon(mediaKind: String): Int = when (mediaKind) {
        "AUDIO" -> R.drawable.ic_audio
        else -> R.drawable.ic_video
    }

    /**
     * S1424: composition comes from the shared catalog through [StreamMenuBinder], so this row and
     * the tile can no longer disagree about what a channel offers (strategic ADR-1).
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
            StreamMenuAction.ABOUT_CHANNEL -> onAboutChannel(source)
            StreamMenuAction.SHARE_LINK -> onShareLink(source)
            StreamMenuAction.REMOVE -> onRemove(source)
        }
    }

    private companion object {
        // S1117: catalog access flag value that turns on the region-restriction badge.
        const val ACCESS_GEO = "geo"

        val DIFF = object : DiffUtil.ItemCallback<StreamSourceEntity>() {
            override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem == newItem

            // S1169: narrow a status- or pin-only change to a payload so the row does not full-rebind.
            override fun getChangePayload(oldItem: StreamSourceEntity, newItem: StreamSourceEntity): Any? =
                streamRowChangePayload(oldItem, newItem)
        }
    }
}
