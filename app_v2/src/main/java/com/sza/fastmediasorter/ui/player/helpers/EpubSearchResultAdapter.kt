package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R

/**
 * RecyclerView adapter for displaying cross-chapter EPUB search results.
 * Highlights the matched text in the context snippet.
 */
class EpubSearchResultAdapter(
    private val results: List<EpubSearchResult>,
    private val onResultClicked: (EpubSearchResult) -> Unit
) : RecyclerView.Adapter<EpubSearchResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvChapterTitle: TextView = view.findViewById(R.id.tvResultChapterTitle)
        val tvContextSnippet: TextView = view.findViewById(R.id.tvResultSnippet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_epub_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        
        holder.tvChapterTitle.text = result.chapterTitle
        
        // Highlight matched text in snippet
        val snippet = result.contextSnippet
        val matchStart = snippet.indexOf(result.matchedText, ignoreCase = true)
        
        if (matchStart >= 0) {
            val spannable = SpannableString(snippet)
            val matchEnd = matchStart + result.matchedText.length
            spannable.setSpan(
                BackgroundColorSpan(0x40FFC107), // Semi-transparent yellow
                matchStart, matchEnd,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                matchStart, matchEnd,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            holder.tvContextSnippet.text = spannable
        } else {
            holder.tvContextSnippet.text = snippet
        }
        
        holder.itemView.setOnClickListener {
            onResultClicked(result)
        }
    }

    override fun getItemCount(): Int = results.size
}
