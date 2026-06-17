package com.sza.fastmediasorter.core.share

import android.net.Uri
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType

/**
 * What a surface hands to a [ShareTargetHandler], decoupled from any Activity or player state
 * (S0459). A surface builds this from its current file(s); a handler consumes only this - never
 * global UI state - so the same handler works from the player, browse, or a standalone host.
 */
data class ShareableContent(
    val uris: List<Uri>,
    val mime: String,
    val mediaType: MediaType,
    /** Plain-text payload for text-only receivers (e.g. Keep-text); null for binary receivers. */
    val text: String? = null,
    /** Optional human-readable name used as a subject/title by some receivers (e.g. email). */
    val displayName: String? = null,
    /**
     * Source domain file, for receivers that need more than a shareable Uri - notably Print, handed
     * to the host's [SharePrintHost.printMediaFile]. Uri-based receivers ignore it. Null when the
     * surface only has Uris (S0459 ADR-11).
     */
    val mediaFile: MediaFile? = null,
    /**
     * Original file path of the source, which may carry a remote scheme (smb://, sftp://, ftp://).
     * Set by a surface that has no local shareable Uri yet (remote file): [uris] is left empty and the
     * dispatch layer materializes a local cache copy on demand before invoking a file receiver (S0493).
     * Null once content is local (either built local or already materialized).
     */
    val sourcePath: String? = null,
) {
    /** Content scoped to the first file - used by single-file receivers on a multi-selection (ADR-4). */
    fun single(): ShareableContent =
        if (uris.size <= 1) this else copy(uris = listOf(uris.first()))

    /**
     * True when no local Uri exists yet but a remote [sourcePath] is known - the dispatch layer must
     * download a local copy before a file-consuming receiver can run (S0493).
     */
    val requiresMaterialization: Boolean
        get() = uris.isEmpty() && sourcePath != null &&
            (sourcePath.contains("://") || sourcePath.startsWith("cloud:/"))

    /** Content localized to a freshly downloaded [localUri]/[localPath], clearing the remote marker (S0493). */
    fun materializedTo(localUri: Uri, localPath: String): ShareableContent =
        copy(uris = listOf(localUri), mediaFile = mediaFile?.copy(path = localPath), sourcePath = null)

    companion object {
        /**
         * Derive a share MIME type from a file name + [MediaType], for surfaces that build
         * [ShareableContent] from a [MediaFile] (S0459). Mirrors the player's derivation so every
         * host produces the same `mime` for a given file.
         */
        fun mimeForMediaType(name: String, type: MediaType): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (type) {
                MediaType.IMAGE -> when (ext) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "bmp" -> "image/bmp"
                    else -> "image/*"
                }
                MediaType.GIF -> "image/gif"
                MediaType.VIDEO -> when (ext) {
                    "mp4" -> "video/mp4"
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    else -> "video/*"
                }
                MediaType.AUDIO -> when (ext) {
                    "mp3" -> "audio/mpeg"
                    "wav" -> "audio/wav"
                    "ogg" -> "audio/ogg"
                    "flac" -> "audio/flac"
                    else -> "audio/*"
                }
                MediaType.PDF -> "application/pdf"
                MediaType.TEXT -> "text/plain"
                else -> "*/*"
            }
        }
    }
}
