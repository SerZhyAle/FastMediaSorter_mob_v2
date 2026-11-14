package com.sza.fastmediasorter.ui.notes

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemNoteBinding
import com.sza.fastmediasorter.domain.usecase.notes.NoteSummary

/** S4568: rows of the Notes list - caption, date, excerpt and the per-row actions button. */
class NotesListAdapter(
    private val onOpen: (NoteSummary) -> Unit,
    private val onMore: (NoteSummary, View) -> Unit,
) : ListAdapter<NoteSummary, NotesListAdapter.NoteViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder =
        NoteViewHolder(ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(note: NoteSummary) {
            binding.noteCaption.text = note.caption
            binding.noteExcerpt.text = note.excerpt
            binding.noteExcerpt.visibility = if (note.excerpt.isEmpty()) View.GONE else View.VISIBLE
            binding.noteDate.text = DateUtils.formatDateTime(
                binding.root.context,
                note.modifiedAt,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
            )
            binding.root.setOnClickListener { onOpen(note) }
            binding.root.setOnLongClickListener {
                onMore(note, it)
                true
            }
            binding.noteMore.setOnClickListener { onMore(note, it) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<NoteSummary>() {
            override fun areItemsTheSame(oldItem: NoteSummary, newItem: NoteSummary): Boolean =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: NoteSummary, newItem: NoteSummary): Boolean =
                oldItem == newItem
        }
    }
}
