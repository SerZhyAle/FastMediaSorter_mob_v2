package com.sza.fastmediasorter.wear.domain.documents

/**
 * What came back from trying to read a document, in the shape the reading screen consumes it.
 *
 * S2532: truncation is deliberately a flag on a successful read rather than a member of
 * [WearDocumentFailure]. Strategic goal 4 asks an over-sized file to show partially, and goal 5 asks
 * a read error, an empty file and an unsupported format to stay distinguishable on screen - folding
 * "too big" into the failures would collapse the first requirement into the very state the second
 * one exists to keep apart.
 */
sealed interface WearDocumentContent {

    /**
     * The document read as text.
     *
     * @param truncated the source held more bytes than the cap allowed, so [text] is a prefix.
     * @param totalBytes bytes actually read; the size of the whole source while [truncated] is false.
     */
    data class Text(
        val text: String,
        val truncated: Boolean,
        val totalBytes: Long
    ) : WearDocumentContent

    /** The document was readable and held nothing - not an error, and not an empty screen either. */
    data object Empty : WearDocumentContent

    /** The document could not be turned into text at all. */
    data class Failure(val reason: WearDocumentFailure) : WearDocumentContent
}

/** Why a document could not be read, at the granularity the screen tells the wearer apart. */
enum class WearDocumentFailure {

    /** The file is gone, or was never there - a stale browse entry lands here. */
    NOT_FOUND,

    /** The file exists but this process may not open it. */
    NO_ACCESS,

    /** The bytes are not text in any encoding the watch tries, so showing them would be worse than refusing. */
    UNREADABLE_ENCODING,

    /** The read started and broke - a dropped share, a bad sector, a closed descriptor. */
    IO_ERROR
}
