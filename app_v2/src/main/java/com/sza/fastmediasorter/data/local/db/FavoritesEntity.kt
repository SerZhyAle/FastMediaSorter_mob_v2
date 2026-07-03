package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["uri"], unique = true, name = "idx_favorites_uri"),
        Index(value = ["addedTimestamp"], name = "idx_favorites_addedTimestamp"),
        Index(value = ["resourceId"], name = "idx_favorites_resource_id")
    ]
)
data class FavoritesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val uri: String, // Full path or URI of the file
    val resourceId: Long, // ID of the resource this file belongs to (for credentials/context)
    val displayName: String,
    val mediaType: Int, // Media type flag (1=Image, 2=Video, etc) - keeping consistent with other parts
    val size: Long,
    val lastKnownPath: String, // Redundant with uri but good for fallback if URI scheme changes
    val dateModified: Long, // File modification date
    val addedTimestamp: Long = System.currentTimeMillis(), // When it was favorited
    // S0783: discriminator so the shared favorites table can also hold live channels. FILE rows are
    // unchanged; STREAM rows carry the channel URL in [uri] and its media kind in [streamMediaKind],
    // and are opened in the stream player instead of a file viewer. File-only consumers (widget,
    // export/backup, Wear) read the kind = 'FILE' slice.
    val kind: String = KIND_FILE,
    val streamMediaKind: String? = null // AUDIO / VIDEO / RTSP for STREAM rows; null for files
) {
    companion object {
        const val KIND_FILE = "FILE"
        const val KIND_STREAM = "STREAM"
    }
}
