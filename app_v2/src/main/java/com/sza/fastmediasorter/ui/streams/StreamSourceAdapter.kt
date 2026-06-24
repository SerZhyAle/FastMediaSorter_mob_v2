package com.sza.fastmediasorter.ui.streams

import android.content.res.ColorStateList
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
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ItemStreamSourceBinding
import com.sza.fastmediasorter.ui.browse.InlinePlaybackAnimator

/**
 * Renders the stream catalog. Row tap launches playback ([onPlay]); the pin affordance promotes the
 * source locally ([onPin]); long-press removes it ([onRemove]). The now-playing indicator tracks the
 * id passed to [setPlayingId] so the inline-audio row is marked without rebuilding the list.
 *
 * S0660: the overflow (`три точки`) is the canonical surface for secondary channel commands - add to
 * home screen, edit (manual channels only), send link, remove. The long-press remove and the separate
 * pin button stay as transitional duplicates while the menu grows.
 */
class StreamSourceAdapter(
    private val onPlay: (StreamSourceEntity) -> Unit,
    private val onPin: (StreamSourceEntity) -> Unit,
    private val onRemove: (StreamSourceEntity) -> Unit,
    private val onAddShortcut: (StreamSourceEntity) -> Unit,
    private val onEdit: (StreamSourceEntity) -> Unit,
    private val onShareLink: (StreamSourceEntity) -> Unit,
) : ListAdapter<StreamSourceEntity, StreamSourceAdapter.VH>(DIFF) {

    private var playingId: String? = null

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

    override fun onViewRecycled(holder: VH) {
        // S0587/S0579: a recycled row must not keep spinning - cancel its rotation animator.
        holder.stopPlaybackAnimation()
    }

    inner class VH(private val binding: ItemStreamSourceBinding) : RecyclerView.ViewHolder(binding.root) {
        // S0579: reuse the file-browser inline playback rotation on the row kind icon.
        private val playbackAnimator = InlinePlaybackAnimator(binding.ivKind)

        fun stopPlaybackAnimation() = playbackAnimator.stopAll()

        fun bind(source: StreamSourceEntity, isPlaying: Boolean) {
            binding.tvTitle.text = source.title
            binding.tvUrl.text = source.url
            binding.ivKind.setImageResource(kindIcon(source.mediaKind))
            bindPlayStatus(source.lastPlayOutcome)
            binding.tvNowPlaying.visibility = if (isPlaying) View.VISIBLE else View.GONE
            if (isPlaying) playbackAnimator.startNote() else playbackAnimator.stopNote()
            bindChip(binding.tvTopic, source.topic)
            bindChip(binding.tvLanguage, source.language)
            binding.chipRow.visibility =
                if (binding.tvTopic.isVisible || binding.tvLanguage.isVisible) View.VISIBLE else View.GONE
            bindPinState(source.pinned)
            binding.root.setOnClickListener { onPlay(source) }
            binding.root.setOnLongClickListener { onRemove(source); true }
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
            binding.btnOverflow.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menu.add(Menu.NONE, ID_ADD_SHORTCUT, 0, R.string.streams_add_to_home_screen)
                    // Edit is offered only for user-added channels; CATALOG/IMPORTED rows are owned
                    // by their sync and must not be hand-edited (S0660 §6.4).
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
        }

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

        /** Catalog metadata only - manual/imported rows leave topic/language null, so hide the chip. */
        private fun bindChip(view: TextView, value: String?) {
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
