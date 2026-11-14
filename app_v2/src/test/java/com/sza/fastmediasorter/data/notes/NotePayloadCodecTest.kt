package com.sza.fastmediasorter.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The clear payload of DEVICE-EXCHANGE 1.13 item FH. */
class NotePayloadCodecTest {

    @Test
    fun `text note round trips`() {
        val payload = textNote(title = "Shopping", text = "milk\nbread")

        assertEquals(NoteDecode.Decoded(payload), NotePayloadCodec.decode(NotePayloadCodec.encode(payload)))
    }

    @Test
    fun `checklist round trips ordered by position`() {
        val payload = NotePayload(
            title = null,
            kind = NoteKind.CHECKLIST,
            text = null,
            items = listOf(
                NoteItem(ITEM_B, "second", checked = true, position = 2),
                NoteItem(ITEM_A, "first", checked = false, position = 1),
            ),
            createdAt = CREATED,
            updatedAt = UPDATED,
        )

        val decoded = NotePayloadCodec.decode(NotePayloadCodec.encode(payload)) as NoteDecode.Decoded

        assertEquals(listOf(ITEM_A, ITEM_B), decoded.payload.items.map { it.itemId })
        assertEquals(null, decoded.payload.title)
    }

    @Test
    fun `a null title is written as null`() {
        val json = String(NotePayloadCodec.encode(textNote(title = null, text = "x")), Charsets.UTF_8)

        assertTrue(json.contains("\"title\":null"))
    }

    @Test
    fun `CRLF becomes LF`() {
        val json = String(NotePayloadCodec.encode(textNote(title = null, text = "a\r\nb\rc")), Charsets.UTF_8)

        val decoded = NotePayloadCodec.decode(json.toByteArray()) as NoteDecode.Decoded

        assertEquals("a\nb\nc", decoded.payload.text)
    }

    @Test
    fun `a higher formatVersion is unsupported`() {
        val body = """{"formatVersion":2,"title":null,"kind":"text","text":"x",""" +
            """"createdAt":"$CREATED","updatedAt":"$UPDATED"}"""

        assertEquals(NoteDecode.Unsupported, NotePayloadCodec.decode(body.toByteArray()))
    }

    @Test
    fun `an unknown member is ignored`() {
        val body = """{"formatVersion":1,"title":"T","kind":"text","text":"x","colour":"red",""" +
            """"createdAt":"$CREATED","updatedAt":"$UPDATED"}"""

        assertEquals(
            NoteDecode.Decoded(textNote(title = "T", text = "x")),
            NotePayloadCodec.decode(body.toByteArray()),
        )
    }

    @Test
    fun `a payload without dates is malformed`() {
        val body = """{"formatVersion":1,"title":null,"kind":"text","text":"x"}"""

        assertEquals(NoteDecode.Malformed, NotePayloadCodec.decode(body.toByteArray()))
    }

    private fun textNote(title: String?, text: String) = NotePayload(
        title = title,
        kind = NoteKind.TEXT,
        text = text,
        items = emptyList(),
        createdAt = CREATED,
        updatedAt = UPDATED,
    )

    private companion object {
        const val CREATED = "2026-11-14T10:00:00.000Z"
        const val UPDATED = "2026-11-14T11:00:00.000Z"
        const val ITEM_A = "0f8fad5b-d9cb-469f-a165-70867728950e"
        const val ITEM_B = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    }
}
