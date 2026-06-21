package com.sza.fastmediasorter.ui.streams

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ItemStreamSourceBinding

/**
 * Renders the stream catalog. Row tap launches playback ([onPlay]); the pin affordance promotes the
 * source locally ([onPin]); long-press removes it ([onRemove]). The now-playing indicator tracks the
 * id passed to [setPlayingId] so the inline-audio row is marked without rebuilding the list.
 */
class StreamSourceAdapter(
    private val onPlay: (StreamSourceEntity) -> Unit,
    private val onPin: (StreamSourceEntity) -> Unit,
    private val onRemove: (StreamSourceEntity) -> Unit,
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

    inner class VH(private val binding: ItemStreamSourceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(source: StreamSourceEntity, isPlaying: Boolean) {
            binding.tvTitle.text = source.title
            binding.tvUrl.text = source.url
            binding.ivKind.setImageResource(kindIcon(source.mediaKind))
            binding.tvNowPlaying.visibility = if (isPlaying) View.VISIBLE else View.GONE
            bindChip(binding.tvTopic, source.topic)
            bindChip(binding.tvLanguage, source.language)
            binding.chipRow.visibility =
                if (binding.tvTopic.isVisible || binding.tvLanguage.isVisible) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onPlay(source) }
            binding.root.setOnLongClickListener { onRemove(source); true }
            binding.btnPin.setOnClickListener { onPin(source) }
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
        val DIFF = object : DiffUtil.ItemCallback<StreamSourceEntity>() {
            override fun areItemsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StreamSourceEntity, newItem: StreamSourceEntity) =
                oldItem == newItem
        }
    }
}
