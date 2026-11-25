package com.sza.fastmediasorter.data.notes

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotesSyncStateStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "notes-sync/state.json")

    @Test
    fun `write then read gives the same state`() = runBlocking {
        val store = NotesSyncStateStore(file)
        val state = NotesSyncState(
            entryIdentity = "host:7777/serzh",
            notesRevision = REVISION,
            notes = mapOf(
                "Shopping.txt" to
                    SyncedNote(NOTE_ID, REVISION, "ab", "Shopping", NoteKind.CHECKLIST, listOf(ITEM), CREATED),
                "Idea.txt" to SyncedNote(OTHER_ID, 2L, "cd", null, NoteKind.TEXT, emptyList(), CREATED),
            ),
        )

        store.write(state)

        assertEquals(state, NotesSyncStateStore(file).read())
    }

    @Test
    fun `a corrupt file reads empty`() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("{not json")

        assertEquals(NotesSyncState.EMPTY, NotesSyncStateStore(file).read())
    }

    @Test
    fun `a changed entry identity is kept as written`() = runBlocking {
        val store = NotesSyncStateStore(file)
        store.write(NotesSyncState("a:1/x", REVISION, emptyMap()))

        store.update { it.copy(entryIdentity = "b:2/y") }

        assertEquals("b:2/y", NotesSyncStateStore(file).read().entryIdentity)
    }

    private companion object {
        const val REVISION = 5L
        const val CREATED = "2026-11-14T10:00:00.000Z"
        const val NOTE_ID = "0f8fad5b-d9cb-469f-a165-70867728950e"
        const val OTHER_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        const val ITEM = "16fd2706-8baf-433b-82eb-8c7fada847da"
    }
}
