package com.sza.fastmediasorter.ui.stopwatch.helpers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemMusicTrackBinding
import com.sza.fastmediasorter.domain.model.stopwatch.MusicTrackOption
import java.util.Locale

/**
 * The music picker's list (S2792).
 *
 * One row per MediaStore track, keyed on the uri so a reload rebinds nothing it does not have to.
 * The row's contentDescription repeats the two visible lines, because the subtitle is where the
 * artist lives and TalkBack reads the description rather than the paint.
 */
class MusicTrackAdapter(
    private val onTrackClick: (MusicTrackOption) -> Unit,
) : ListAdapter<MusicTrackOption, MusicTrackAdapter.TrackViewHolder>(TrackDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder =
        TrackViewHolder(
            ItemMusicTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onTrackClick,
        )

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TrackViewHolder(
        private val binding: ItemMusicTrackBinding,
        onTrackClick: (MusicTrackOption) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var bound: MusicTrackOption? = null

        init {
            binding.root.setOnClickListener { bound?.let(onTrackClick) }
        }

        fun bind(track: MusicTrackOption) {
            bound = track
            binding.textMusicTrackTitle.text = track.title
            val duration = formatDuration(track.durationMillis)
            val subtitle = listOfNotNull(track.artist, duration.ifEmpty { null }).joinToString(" - ")
            binding.textMusicTrackSubtitle.text = subtitle
            binding.root.contentDescription =
                if (subtitle.isEmpty()) track.title else "${track.title}, $subtitle"
        }

        /** Track lengths carry no hundredths a picker row needs; an unknown duration (0) is omitted. */
        private fun formatDuration(millis: Long): String {
            if (millis <= 0L) return ""
            val totalSeconds = millis / MILLIS_PER_SECOND
            val minutes = totalSeconds / SECONDS_PER_MINUTE
            val seconds = totalSeconds % SECONDS_PER_MINUTE
            return "%d:%02d".format(Locale.US, minutes, seconds)
        }

        private companion object {
            const val MILLIS_PER_SECOND = 1000L
            const val SECONDS_PER_MINUTE = 60L
        }
    }

    private object TrackDiff : DiffUtil.ItemCallback<MusicTrackOption>() {
        override fun areItemsTheSame(oldItem: MusicTrackOption, newItem: MusicTrackOption): Boolean =
            oldItem.uri == newItem.uri

        override fun areContentsTheSame(oldItem: MusicTrackOption, newItem: MusicTrackOption): Boolean =
            oldItem == newItem
    }
}
