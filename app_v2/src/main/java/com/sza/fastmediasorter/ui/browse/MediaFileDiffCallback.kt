package com.sza.fastmediasorter.ui.browse

import androidx.recyclerview.widget.DiffUtil
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType

class MediaFileDiffCallback : DiffUtil.ItemCallback<MediaFile>() {

    companion object {
        const val PAYLOAD_AUDIO_METADATA = "audio_metadata"
    }

    override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
        oldItem.path == newItem.path

    override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
        oldItem == newItem

    override fun getChangePayload(oldItem: MediaFile, newItem: MediaFile): Any? {
        // If only isFavorite changed, return FAVORITE_CHANGED payload for partial update
        if (oldItem.isFavorite != newItem.isFavorite) {
            if (oldItem.copy(isFavorite = newItem.isFavorite) == newItem) {
                return "FAVORITE_CHANGED"
            }
        }
        // If only audio metadata changed (artist/album/title/duration), partial text update
        if (oldItem.type == MediaType.AUDIO &&
            (oldItem.artist != newItem.artist || oldItem.album != newItem.album ||
                oldItem.title != newItem.title || oldItem.duration != newItem.duration)
        ) {
            if (oldItem.copy(
                    artist = newItem.artist,
                    album = newItem.album,
                    title = newItem.title,
                    duration = newItem.duration
                ) == newItem
            ) {
                return PAYLOAD_AUDIO_METADATA
            }
        }
        return null // Full rebind needed for other changes
    }
}
