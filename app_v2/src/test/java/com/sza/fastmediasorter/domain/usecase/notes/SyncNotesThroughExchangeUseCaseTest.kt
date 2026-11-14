package com.sza.fastmediasorter.domain.usecase.notes

import com.sza.fastmediasorter.data.notes.NoteDecode
import com.sza.fastmediasorter.data.notes.NoteKind
import com.sza.fastmediasorter.data.notes.NotePayload
import com.sza.fastmediasorter.data.notes.NotePayloadCodec
import com.sza.fastmediasorter.data.notes.NotesSyncStateStore
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.repository.NotesListOutcome
import com.sza.fastmediasorter.domain.repository.NotesPutOutcome
import com.sza.fastmediasorter.domain.repository.NotesSyncRemote
import com.sza.fastmediasorter.domain.repository.RemoteNote
import com.sza.fastmediasorter.domain.repository.RemoteNoteContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncNotesThroughExchangeUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val notesDir: File get() = File(temp.root, "Notes").also { it.mkdirs() }

    private fun useCase(remote: FakeNotesRemote) =
        SyncNotesThroughExchangeUseCase(remote, NotesSyncStateStore(File(temp.root, "state/state.json")))

    private suspend fun SyncNotesThroughExchangeUseCase.sync() = invoke(notesDir, DEVICE)

    private fun noteFiles(): Map<String, String> =
        notesDir.listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readText() }

    @Test
    fun `option off sends nothing`() = runBlocking {
        val remote = FakeNotesRemote(entry = null)
        File(notesDir, "a.txt").writeText("hello")

        assertEquals(NotesSyncOutcome.Off, useCase(remote).sync())
        assertEquals(0, remote.calls)
    }

    @Test
    fun `a server without sealed gets no put`() = runBlocking {
        val remote = FakeNotesRemote(sealed = false)
        File(notesDir, "a.txt").writeText("hello")

        assertEquals(NotesSyncOutcome.ServerNoE2e, useCase(remote).sync())
        assertEquals(0, remote.puts)
    }

    @Test
    fun `a local note becomes one stored record`() = runBlocking {
        val remote = FakeNotesRemote()
        File(notesDir, "a.txt").writeText("hello")

        useCase(remote).sync()

        assertEquals(listOf("hello"), remote.liveTexts())
    }

    @Test
    fun `a remote record becomes a txt file`() = runBlocking {
        val remote = FakeNotesRemote()
        remote.otherDevicePut(NOTE_ID, "from pc")

        useCase(remote).sync()

        assertEquals(listOf("from pc"), noteFiles().values.toList())
        assertTrue(noteFiles().keys.single().endsWith(".txt"))
    }

    @Test
    fun `an edit on the other side comes back`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "first")
        sync.sync()

        remote.otherDevicePut(NOTE_ID, "second")
        sync.sync()

        assertEquals(listOf("second"), noteFiles().values.toList())
    }

    @Test
    fun `a local edit is sent over its revision`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "first")
        sync.sync()

        File(notesDir, noteFiles().keys.single()).writeText("edited here")
        sync.sync()

        assertEquals("edited here", remote.textOf(NOTE_ID))
        assertEquals(1, remote.records.size)
    }

    @Test
    fun `a remote delete removes an unchanged file`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "bye")
        sync.sync()

        remote.otherDeviceDelete(NOTE_ID)
        sync.sync()

        assertTrue(noteFiles().isEmpty())
    }

    @Test
    fun `a remote delete keeps an edited file as a new note`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "base")
        sync.sync()
        File(notesDir, noteFiles().keys.single()).writeText("my edit")

        remote.otherDeviceDelete(NOTE_ID)
        sync.sync()

        assertEquals(listOf("my edit"), noteFiles().values.toList())
        assertEquals(listOf("my edit"), remote.liveTexts())
        assertTrue(remote.records.getValue(NOTE_ID).payload == null)
    }

    @Test
    fun `a local delete against a remote edit keeps the note`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "base")
        sync.sync()
        File(notesDir, noteFiles().keys.single()).delete()

        remote.otherDevicePut(NOTE_ID, "their edit")
        sync.sync()

        assertEquals(listOf("their edit"), noteFiles().values.toList())
        assertEquals(listOf("their edit"), remote.liveTexts())
    }

    @Test
    fun `a local delete racing a remote edit on put keeps the note`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "base")
        sync.sync()
        File(notesDir, noteFiles().keys.single()).delete()

        remote.beforeNextPut = { remote.otherDevicePut(NOTE_ID, "their edit") }
        sync.sync()

        assertEquals(listOf("their edit"), noteFiles().values.toList())
    }

    @Test
    fun `both edited keeps two files and loses no text`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "base")
        sync.sync()
        File(notesDir, noteFiles().keys.single()).writeText("mine")

        remote.otherDevicePut(NOTE_ID, "theirs")
        sync.sync()

        assertEquals(setOf("mine", "theirs"), noteFiles().values.toSet())
        assertEquals(setOf("mine", "theirs"), remote.liveTexts().toSet())
        assertTrue(noteFiles().keys.any { it.contains(DEVICE) })
    }

    @Test
    fun `a put racing a remote edit keeps both versions`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "base")
        sync.sync()
        File(notesDir, noteFiles().keys.single()).writeText("mine")

        remote.beforeNextPut = { remote.otherDevicePut(NOTE_ID, "theirs") }
        sync.sync()

        assertEquals(setOf("mine", "theirs"), noteFiles().values.toSet())
        assertEquals(setOf("mine", "theirs"), remote.liveTexts().toSet())
    }

    @Test
    fun `a put racing a remote delete resends the text as a new note`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "base")
        sync.sync()
        File(notesDir, noteFiles().keys.single()).writeText("mine")

        remote.beforeNextPut = { remote.otherDeviceDelete(NOTE_ID) }
        sync.sync()

        assertEquals(listOf("mine"), noteFiles().values.toList())
        assertEquals(listOf("mine"), remote.liveTexts())
        assertTrue(remote.records.getValue(NOTE_ID).payload == null)
    }

    @Test
    fun `an older copy is refused and the delete stays`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "base")
        sync.sync()
        val oldRevision = remote.records.getValue(NOTE_ID).revision
        File(notesDir, noteFiles().keys.single()).delete()
        sync.sync()

        val late = remote.put(ENTRY, NOTE_ID, oldRevision, NotePayloadCodec.encode(payloadOf("old copy")))
        sync.sync()

        assertTrue(late is NotesPutOutcome.Conflict)
        assertTrue(remote.records.getValue(NOTE_ID).payload == null)
        assertTrue(noteFiles().isEmpty())
    }

    @Test
    fun `a payload above the limit is reported and not sent`() = runBlocking {
        val remote = FakeNotesRemote()
        File(notesDir, "big.txt").writeText("a".repeat(NotePayloadCodec.MAX_PAYLOAD_BYTES + 1))

        val outcome = useCase(remote).sync() as NotesSyncOutcome.Synced

        assertEquals(listOf("big.txt"), outcome.tooLarge)
        assertEquals(0, remote.puts)
    }

    @Test
    fun `a full answer after a purge deletes the purged note`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        remote.otherDevicePut(NOTE_ID, "gone soon")
        sync.sync()

        remote.otherDeviceDelete(NOTE_ID)
        remote.purgeTombstones()
        sync.sync()

        assertTrue(noteFiles().isEmpty())
    }

    @Test
    fun `an unchanged note is not sent again`() = runBlocking {
        val remote = FakeNotesRemote()
        val sync = useCase(remote)
        File(notesDir, "a.txt").writeText("hello")
        sync.sync()
        val putsAfterFirst = remote.puts

        sync.sync()

        assertEquals(putsAfterFirst, remote.puts)
        assertFalse(noteFiles().isEmpty())
    }

    private class Record(val revision: Long, val payload: ByteArray?)

    private class FakeNotesRemote(
        private val entry: DeviceExchangeAccountEntry? = ENTRY,
        private val sealed: Boolean = true,
    ) : NotesSyncRemote {
        val records = linkedMapOf<String, Record>()
        var calls = 0
        var puts = 0
        var beforeNextPut: (() -> Unit)? = null
        private var counter = 0L
        private var fullNext = false

        override suspend fun entry(): DeviceExchangeAccountEntry? = entry

        override suspend fun list(entry: DeviceExchangeAccountEntry, since: Long): NotesListOutcome {
            calls++
            if (!sealed) return NotesListOutcome.NotSealed
            val full = fullNext
            fullNext = false
            val notes = records.filter { full || it.value.revision > since }
                .map { (id, record) -> remoteOf(id, record) }
            return NotesListOutcome.Listed(counter, full, notes)
        }

        override suspend fun put(
            entry: DeviceExchangeAccountEntry,
            noteId: String,
            baseRevision: Long,
            payload: ByteArray?,
        ): NotesPutOutcome {
            calls++
            beforeNextPut?.also { beforeNextPut = null }?.invoke()
            if (sealed) puts++
            val stored = records[noteId]
            val accepted = if (stored == null) baseRevision == 0L else baseRevision == stored.revision
            return when {
                !sealed -> NotesPutOutcome.NotSealed
                !accepted -> NotesPutOutcome.Conflict(remoteOf(noteId, checkNotNull(stored)))
                else -> {
                    records[noteId] = Record(++counter, payload)
                    NotesPutOutcome.Stored(counter)
                }
            }
        }

        fun otherDevicePut(noteId: String, text: String) {
            records[noteId] = Record(++counter, NotePayloadCodec.encode(payloadOf(text)))
        }

        fun otherDeviceDelete(noteId: String) {
            records[noteId] = Record(++counter, null)
        }

        fun purgeTombstones() {
            records.entries.removeAll { it.value.payload == null }
            fullNext = true
        }

        fun textOf(noteId: String): String? = records[noteId]?.payload?.let(::textOf)

        fun liveTexts(): List<String> = records.values.mapNotNull { it.payload?.let(::textOf) }

        private fun textOf(bytes: ByteArray): String? =
            (NotePayloadCodec.decode(bytes) as? NoteDecode.Decoded)?.payload?.text

        private fun remoteOf(noteId: String, record: Record) = RemoteNote(
            noteId = noteId,
            revision = record.revision,
            content = record.payload?.let(RemoteNoteContent::Payload) ?: RemoteNoteContent.Deleted,
        )
    }

    private companion object {
        const val NOTE_ID = "0b9c6f2e-7d41-4a8e-9f3a-1c2d3e4f5a6b"
        const val DEVICE = "Pixel"
        const val STAMP = "2026-11-14T10:00:00Z"
        val ENTRY = DeviceExchangeAccountEntry(
            host = "exchange.local",
            port = 7777,
            login = "owner",
            deviceId = "device-1",
            pinnedCertificate = null,
        )

        fun payloadOf(text: String) = NotePayload(
            title = null,
            kind = NoteKind.TEXT,
            text = text,
            items = emptyList(),
            createdAt = STAMP,
            updatedAt = STAMP,
        )
    }
}
