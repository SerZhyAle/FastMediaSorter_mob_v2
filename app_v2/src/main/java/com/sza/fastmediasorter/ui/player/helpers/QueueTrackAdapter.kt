package com.sza.fastmediasorter.ui.player.helpers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.NowPlayingViewModel.QueueItem

/**
 * Adapter for the queue panel inside NowPlayingBottomSheetFragment.
 * Highlights the currently playing track; clicking any row notifies [onItemClick].
 */
class QueueTrackAdapter(
    private val onItemClick: (index: Int) -> Unit
) : ListAdapter<QueueItem, QueueTrackAdapter.ViewHolder>(DIFF) {

    private var currentIndex: Int = 0

    fun setCurrentIndex(index: Int) {
        if (currentIndex != index) {
            val previous = currentIndex
            currentIndex = index
            notifyItemChanged(previous)
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue_track, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == currentIndex)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIndex: TextView = itemView.findViewById(R.id.tvQueueIndex)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvQueueTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvQueueArtist)
        private val playingIndicator: View = itemView.findViewById(R.id.viewPlayingIndicator)

        fun bind(item: QueueItem, isCurrent: Boolean) {
            tvIndex.text = (item.index + 1).toString()
            tvTitle.text = item.title
            tvArtist.text = item.artist ?: ""
            tvArtist.visibility = if (item.artist.isNullOrEmpty()) View.GONE else View.VISIBLE
            playingIndicator.visibility = if (isCurrent) View.VISIBLE else View.INVISIBLE

            val colorRes = if (isCurrent) R.color.blue_500 else android.R.color.white
            tvTitle.setTextColor(ContextCompat.getColor(itemView.context, colorRes))

            itemView.contentDescription = "${item.title}, position ${item.index + 1}"
            itemView.setOnClickListener { onItemClick(item.index) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<QueueItem>() {
            override fun areItemsTheSame(old: QueueItem, new: QueueItem) = old.index == new.index
            override fun areContentsTheSame(old: QueueItem, new: QueueItem) = old == new
        }
    }
}
