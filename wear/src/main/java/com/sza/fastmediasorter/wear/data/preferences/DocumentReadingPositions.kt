package com.sza.fastmediasorter.wear.data.preferences

/**
 * One document's remembered reading anchor.
 *
 * @param key the document's address, stable across restarts - see [DocumentReadingPositions].
 * @param index the list item that sat in the middle of the display, counted in lazy items rather than
 * in paragraphs: the reader draws a title and the font-size row above the text, and the screen both
 * writes and reads this number, so the constant header offset never has to be added or subtracted.
 * @param offset the scroll offset inside that item.
 * @param sizeBytes the file's length when the anchor was taken; a different length retires the record.
 */
internal data class DocumentReadingPosition(
    val key: String,
    val index: Int,
    val offset: Int,
    val sizeBytes: Long
)

/**
 * S2532: the joined form of the reader's per-document position memory.
 *
 * A separate object rather than private helpers on the preferences section, for the reason
 * [LastUsedResourceHistory] is one: a truncated history, a re-read document and a record that lost a
 * field are unreachable on a watch without editing DataStore by hand, so they are pinned by a test that
 * must not need DataStore to run - and the two separators are the same control characters for the same
 * reason, that no path, URL or number can contain either.
 *
 * The history is one string because the module's store is flat key-value with no per-document map, and
 * it is bounded because a wearer who opens a hundred documents must not carry a hundred records into
 * every read of the key.
 */
internal object DocumentReadingPositions {

    /** Control characters, so no document address can contain either separator. */
    private const val RECORD_SEPARATOR = "\u001E"
    private const val FIELD_SEPARATOR = "\u001F"

    private const val KEY_INDEX = 0
    private const val INDEX_INDEX = 1
    private const val OFFSET_INDEX = 2
    private const val SIZE_INDEX = 3
    private const val FIELD_COUNT = 4

    /**
     * How many documents keep a position. Reading on a watch is a handful of files, not a library, and
     * every record is re-encoded on each write - the cap is what keeps that cost flat.
     */
    const val MAX_ENTRIES = 16

    fun encode(entries: List<DocumentReadingPosition>): String =
        entries.joinToString(RECORD_SEPARATOR) {
            it.key + FIELD_SEPARATOR + it.index + FIELD_SEPARATOR + it.offset +
                FIELD_SEPARATOR + it.sizeBytes
        }

    /** A record that lost a field addresses nothing, so it is dropped rather than reported. */
    fun decode(stored: String?): List<DocumentReadingPosition> = stored
        ?.split(RECORD_SEPARATOR)
        ?.mapNotNull(::decodeRecord)
        .orEmpty()

    private fun decodeRecord(record: String): DocumentReadingPosition? {
        val fields = record.split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) {
            return null
        }
        val key = fields[KEY_INDEX]
        return if (key.isEmpty()) {
            null
        } else {
            positionOf(
                key = key,
                index = fields[INDEX_INDEX].toIntOrNull(),
                offset = fields[OFFSET_INDEX].toIntOrNull(),
                sizeBytes = fields[SIZE_INDEX].toLongOrNull()
            )
        }
    }

    // The three numbers are judged apart from the key so neither test grows into one condition
    // detekt reads as too complex; an unparsable number is the same "addresses nothing" as a lost field.
    private fun positionOf(
        key: String,
        index: Int?,
        offset: Int?,
        sizeBytes: Long?
    ): DocumentReadingPosition? = if (index == null || offset == null || sizeBytes == null) {
        null
    } else {
        DocumentReadingPosition(key, index, offset, sizeBytes)
    }

    /**
     * Puts [entry] at the front, dropping any earlier record for the same document: leaving a document
     * twice records where it was left the second time, never two answers to one question.
     *
     * The match is on the key alone and deliberately ignores the size, so a file that changed replaces
     * its stale record instead of standing beside it forever.
     */
    fun push(
        current: List<DocumentReadingPosition>,
        entry: DocumentReadingPosition
    ): List<DocumentReadingPosition> =
        (listOf(entry) + current.filterNot { it.key == entry.key }).take(MAX_ENTRIES)
}
