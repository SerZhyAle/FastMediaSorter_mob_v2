package com.sza.fastmediasorter.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteFileMapperTest {

    @Test
    fun `untitled text note is its text`() {
        val payload = text(title = null, body = "one\ntwo")

        val file = NoteFileMapper.toFileText(payload)

        assertEquals("one\ntwo", file)
        assertEquals(payload, NoteFileMapper.fromFileText(file, synced(null, NoteKind.TEXT), CREATED, UPDATED))
    }

    @Test
    fun `titled text note keeps its title`() {
        val payload = text(title = "Idea", body = "body")

        val file = NoteFileMapper.toFileText(payload)

        assertEquals("Idea\n\nbody", file)
        assertEquals(payload, NoteFileMapper.fromFileText(file, synced("Idea", NoteKind.TEXT), CREATED, UPDATED))
    }

    @Test
    fun `checklist round trips with its ids`() {
        val payload = checklist(
            NoteItem(ITEM_A, "milk", checked = true, position = 0),
            NoteItem(ITEM_B, "bread", checked = false, position = 1),
        )

        val file = NoteFileMapper.toFileText(payload)
        val previous = synced("Shop", NoteKind.CHECKLIST, ITEM_A, ITEM_B)
        val back = NoteFileMapper.fromFileText(file, previous, CREATED, UPDATED)

        assertEquals("Shop\n\n[x] milk\n[ ] bread", file)
        assertEquals(payload, back)
    }

    @Test
    fun `a checklist edited into free text becomes text`() {
        val back = NoteFileMapper.fromFileText(
            "Shop\n\n[x] milk\njust words",
            synced("Shop", NoteKind.CHECKLIST, ITEM_A, ITEM_B),
            CREATED,
            UPDATED,
        )

        assertEquals(NoteKind.TEXT, back.kind)
        assertEquals("[x] milk\njust words", back.text)
    }

    @Test
    fun `an added checklist line gets a new id`() {
        val back = NoteFileMapper.fromFileText(
            "Shop\n\n[x] milk\n[ ] bread\n[ ] eggs",
            synced("Shop", NoteKind.CHECKLIST, ITEM_A, ITEM_B),
            CREATED,
            UPDATED,
        )

        assertEquals(listOf(ITEM_A, ITEM_B), back.items.take(2).map { it.itemId })
        assertNotEquals(ITEM_A, back.items[2].itemId)
        assertNotEquals(ITEM_B, back.items[2].itemId)
    }

    @Test
    fun `a removed blank line after the title drops the title`() {
        val back = NoteFileMapper.fromFileText("Idea\nbody", synced("Idea", NoteKind.TEXT), CREATED, UPDATED)

        assertNull(back.title)
        assertEquals("Idea\nbody", back.text)
    }

    @Test
    fun `hash ignores line end style`() {
        assertEquals(NoteFileMapper.sha256Of("a\nb"), NoteFileMapper.sha256Of("a\r\nb"))
    }

    private fun text(title: String?, body: String) =
        NotePayload(title, NoteKind.TEXT, body, emptyList(), CREATED, UPDATED)

    private fun checklist(vararg items: NoteItem) =
        NotePayload("Shop", NoteKind.CHECKLIST, null, items.toList(), CREATED, UPDATED)

    private fun synced(title: String?, kind: NoteKind, vararg ids: String) =
        SyncedNote(NOTE_ID, 1L, "x", title, kind, ids.toList(), CREATED)

    private companion object {
        const val CREATED = "2026-11-14T10:00:00.000Z"
        const val UPDATED = "2026-11-14T11:00:00.000Z"
        const val NOTE_ID = "0f8fad5b-d9cb-469f-a165-70867728950e"
        const val ITEM_A = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        const val ITEM_B = "16fd2706-8baf-433b-82eb-8c7fada847da"
    }
}
