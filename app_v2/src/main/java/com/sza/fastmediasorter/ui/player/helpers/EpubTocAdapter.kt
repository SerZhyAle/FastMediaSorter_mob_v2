package com.sza.fastmediasorter.ui.player.helpers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R

/**
 * RecyclerView adapter for EPUB Table of Contents navigation.
 * Displays chapter titles with indentation, current position highlight,
 * and chapter numbering.
 *
 * @param chapters List of (title, spineIndex) pairs from flattenToc
 * @param currentChapterSpineIndex Currently displayed chapter spine index
 * @param onChapterSelected Callback when user taps a chapter
 */
class EpubTocAdapter(
    private val chapters: List<Pair<String, Int>>,
    private val currentChapterSpineIndex: Int,
    private val onChapterSelected: (spineIndex: Int) -> Unit
) : RecyclerView.Adapter<EpubTocAdapter.TocViewHolder>() {

    class TocViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvChapterTitle: TextView = itemView.findViewById(R.id.tvChapterTitle)
        val tvChapterNumber: TextView = itemView.findViewById(R.id.tvChapterNumber)
        val currentIndicator: View = itemView.findViewById(R.id.currentChapterIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TocViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_epub_toc, parent, false)
        return TocViewHolder(view)
    }

    override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
        val (title, spineIndex) = chapters[position]
        val isCurrent = spineIndex == currentChapterSpineIndex

        holder.tvChapterTitle.text = title
        holder.tvChapterNumber.text = "${position + 1}"

        // Highlight current chapter
        holder.currentIndicator.visibility = if (isCurrent) View.VISIBLE else View.INVISIBLE
        holder.tvChapterTitle.alpha = if (isCurrent) 1.0f else 0.85f
        holder.tvChapterTitle.setTypeface(
            holder.tvChapterTitle.typeface,
            if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )

        holder.itemView.setOnClickListener {
            val idx = holder.bindingAdapterPosition
            if (idx != RecyclerView.NO_POSITION) {
                onChapterSelected(chapters[idx].second)
            }
        }
    }

    override fun getItemCount(): Int = chapters.size

    /**
     * Find adapter position for the current chapter (for scrolling).
     */
    fun findCurrentChapterPosition(): Int {
        return chapters.indexOfFirst { it.second == currentChapterSpineIndex }
    }
}
