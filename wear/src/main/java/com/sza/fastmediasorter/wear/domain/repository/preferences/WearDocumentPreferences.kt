package com.sza.fastmediasorter.wear.domain.repository.preferences

import com.sza.fastmediasorter.wear.domain.documents.DocumentFontSize
import com.sza.fastmediasorter.wear.domain.documents.DocumentReadingAnchor
import kotlinx.coroutines.flow.Flow

/**
 * What the watch remembers about reading a document (S2532).
 *
 * Its own section rather than a member of an existing one: the module splits settings by theme, and a
 * reader setting joining playback or appearance would put it where nothing else about documents lives.
 */
interface WearDocumentPreferences {

    /** The reader's text size, one flat choice shared by every document. */
    val documentFontSize: Flow<DocumentFontSize>
    suspend fun setDocumentFontSize(size: DocumentFontSize)

    /**
     * Where [key] was left, or null when nothing was remembered for it.
     *
     * Strategic §3.2 requires a changed file to be forgotten silently, and that is decided here rather
     * than on the screen: the screen sees a position and cannot tell a stale one from a fresh one, so a
     * record whose [sizeBytes] no longer matches the file answers exactly as an absent record does - the
     * document opens from its start, with nothing to special-case at the call site.
     */
    suspend fun readingPositionFor(key: String, sizeBytes: Long): DocumentReadingAnchor?

    /** Records where [key] is being read; an earlier record for the same document is replaced. */
    suspend fun setReadingPosition(key: String, sizeBytes: Long, index: Int, offset: Int)
}
