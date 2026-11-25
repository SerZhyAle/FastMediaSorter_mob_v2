package com.sza.fastmediasorter.ui.notes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemNoteBinding
import com.sza.fastmediasorter.domain.model.notes.NoteSummary
import com.sza.fastmediasorter.ui.browse.AdapterFileInfoFormatter
import java.io.File

/**
 * S4568: rows of the Notes list - caption, date, excerpt and the per-row actions button.
 *
 * S4865: while [selected] is not empty the list is in selection mode - a click or a long click toggles the
 * row, the row shows its check box and the actions button is hidden; otherwise a long click starts the mode.
 */
class NotesListAdapter(
    private val onOpen: (NoteSummary) -> Unit,
    private val onMore: (NoteSummary, View) -> Unit,
    private val onToggle: (NoteSummary) -> Unit,
) : ListAdapter<NoteSummary, NotesListAdapter.NoteViewHolder>(DIFF) {

    /** Paths of the selected notes (S4865). */
    var selected: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    /** File names of the notes above the sync payload limit (S4572); each such row carries the mark. */
    var tooLarge: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

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
            binding.noteDate.text = AdapterFileInfoFormatter.formatTimestamp(binding.root.context, note.modifiedAt)
            binding.noteTooLarge.visibility =
                if (File(note.path).name in tooLarge) View.VISIBLE else View.GONE
            val selecting = selected.isNotEmpty()
            val isSelected = note.path in selected
            binding.noteSelect.visibility = if (selecting) View.VISIBLE else View.GONE
            binding.noteSelect.isChecked = isSelected
            binding.noteMore.visibility = if (selecting) View.GONE else View.VISIBLE
            binding.root.isActivated = isSelected
            binding.root.setOnClickListener { if (selecting) onToggle(note) else onOpen(note) }
            // Browse parity: a long press starts a selection and, inside one, toggles like a tap.
            binding.root.setOnLongClickListener {
                onToggle(note)
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
