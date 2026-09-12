package com.sza.fastmediasorter.wear.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2532: none of these cases is reachable on a watch without editing DataStore by hand - a document
 * read twice, a history longer than the cap, a record that lost a field - so the rules that produce
 * them are pinned here rather than on the device.
 */
class DocumentReadingPositionsTest {

    @Test
    fun `a position survives the joined form unchanged`() {
        val entries = listOf(first(), second())

        assertEquals(entries, DocumentReadingPositions.decode(DocumentReadingPositions.encode(entries)))
    }

    @Test
    fun `an absent history decodes to nothing`() {
        assertTrue(DocumentReadingPositions.decode(null).isEmpty())
    }

    @Test
    fun `reading a remembered document again replaces its position instead of repeating it`() {
        val moved = first().copy(index = MOVED_INDEX)

        val pushed = DocumentReadingPositions.push(listOf(first(), second()), moved)

        assertEquals(listOf(moved, second()), pushed)
    }

    @Test
    fun `a history never grows past the cap and drops the oldest record`() {
        val full = (1..DocumentReadingPositions.MAX_ENTRIES).map {
            DocumentReadingPosition("$FIRST_KEY-$it", it, 0, SIZE_BYTES)
        }

        val pushed = DocumentReadingPositions.push(full, first())

        assertEquals(DocumentReadingPositions.MAX_ENTRIES, pushed.size)
        assertEquals(FIRST_KEY, pushed.first().key)
        assertTrue(pushed.none { it.key == "$FIRST_KEY-${DocumentReadingPositions.MAX_ENTRIES}" })
    }

    @Test
    fun `a record that lost a field is dropped rather than reported`() {
        val orphan = SECOND_KEY + FIELD_SEPARATOR + MOVED_INDEX

        val decoded = DocumentReadingPositions.decode(
            DocumentReadingPositions.encode(listOf(first())) + RECORD_SEPARATOR + orphan
        )

        assertEquals(listOf(first()), decoded)
    }

    private fun first() = DocumentReadingPosition(FIRST_KEY, FIRST_INDEX, FIRST_OFFSET, SIZE_BYTES)

    private fun second() = DocumentReadingPosition(SECOND_KEY, SECOND_INDEX, 0, SIZE_BYTES)

    private companion object {
        const val RECORD_SEPARATOR = "\u001E"
        const val FIELD_SEPARATOR = "\u001F"
        const val FIRST_KEY = "file:///sdcard/Documents/notes.txt"
        const val SECOND_KEY = "smb://nas/share/readme.md"
        const val FIRST_INDEX = 7
        const val FIRST_OFFSET = -12
        const val SECOND_INDEX = 3
        const val MOVED_INDEX = 21
        const val SIZE_BYTES = 4096L
    }
}
