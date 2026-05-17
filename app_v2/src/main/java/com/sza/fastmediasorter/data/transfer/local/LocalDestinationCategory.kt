package com.sza.fastmediasorter.data.transfer.local

/**
 * Classification of a local filesystem destination for an incoming file
 * (network download, link save, frame export, etc.).
 *
 * Distinguishes between **public collections** managed by MediaStore
 * (Music, Movies, Pictures, DCIM, Downloads, and their cousins) and any
 * other path. On Android 10+ scoped storage, only the MediaStore path
 * is reliably writable without `MANAGE_EXTERNAL_STORAGE`.
 */
sealed interface LocalDestinationCategory {

    /** Filename portion only (no directory part). */
    val displayName: String

    /** MIME type resolved from extension; `application/octet-stream` if unknown. */
    val mimeType: String

    /**
     * Destination is under a canonical public media directory.
     *
     * @param collection MediaStore collection family chosen by physical path
     *                   (path wins over MIME — see strategic §6.3).
     * @param relativePath Value for `MediaStore.MediaColumns.RELATIVE_PATH`,
     *                     including a trailing slash (e.g. `Music/Artist/Album/`).
     */
    data class PublicCollection(
        val collection: Kind,
        val relativePath: String,
        override val displayName: String,
        override val mimeType: String
    ) : LocalDestinationCategory {
        enum class Kind { AUDIO, VIDEO, IMAGES, DOWNLOADS, FILES }
    }

    /**
     * Destination is outside the canonical public collections — a private
     * app folder, a custom directory chosen by the user, or anything else.
     * Falls back to direct `FileOutputStream`.
     */
    data class NonPublic(
        val absolutePath: String,
        override val displayName: String,
        override val mimeType: String
    ) : LocalDestinationCategory
}
