package com.sza.fastmediasorter.domain.exchange

import com.sza.fastmediasorter.data.notes.NoteDecode
import com.sza.fastmediasorter.data.notes.NoteFileMapper
import com.sza.fastmediasorter.data.notes.NoteItem
import com.sza.fastmediasorter.data.notes.NotePayload
import com.sza.fastmediasorter.data.notes.NotePayloadCodec
import com.sza.fastmediasorter.data.notes.NotesSyncState
import com.sza.fastmediasorter.data.notes.SyncedNote
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.notes.NotesSyncOutcome
import com.sza.fastmediasorter.domain.repository.NotesListOutcome
import com.sza.fastmediasorter.domain.repository.NotesPutOutcome
import com.sza.fastmediasorter.domain.repository.NotesSyncRemote
import com.sza.fastmediasorter.domain.repository.RemoteNote
import com.sza.fastmediasorter.domain.repository.RemoteNoteContent
import com.sza.fastmediasorter.util.NotesFolderPolicy
import com.sza.fastmediasorter.util.TextNoteFileNameProvider
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * One run of the two-way notes sync (S4572). Its only caller, SyncNotesThroughExchangeUseCase, serialises the
 * runs and picks the dispatcher: the run does blocking file and network work and must stay off the main thread.
 */
internal class NotesSyncRun(
    private val remote: NotesSyncRemote,
    private val entry: DeviceExchangeAccountEntry,
    private val folder: File,
    private val deviceName: String,
    initial: NotesSyncState,
    private val persist: suspend (NotesSyncState) -> Unit,
) {
    private val notes = initial.notes.toMutableMap()
    private var notesRevision = initial.notesRevision
    private val tooLarge = mutableListOf<String>()
    private var received = 0
    private var sent = 0
    private var conflicts = 0
    private var skipped = 0
    private var refused = 0
    private var limitReached = false
    private var stopped: NotesSyncOutcome? = null

    suspend fun run(): NotesSyncOutcome {
        val listed = remote.list(entry, notesRevision)
        if (listed !is NotesListOutcome.Listed) return outcomeOf(listed)
        pull(listed)
        persist(state())
        push()
        persist(state())
        return stopped ?: synced()
    }

    private fun outcomeOf(listed: NotesListOutcome): NotesSyncOutcome = when (listed) {
        NotesListOutcome.NotOffered -> NotesSyncOutcome.NotOffered
        NotesListOutcome.NotSealed -> NotesSyncOutcome.ServerNoE2e
        NotesListOutcome.Guest -> NotesSyncOutcome.Guest
        is NotesListOutcome.Refused -> NotesSyncOutcome.Refused(listed.reason)
        is NotesListOutcome.Listed -> synced()
    }

    private fun synced() = NotesSyncOutcome.Synced(
        host = entry.host,
        received = received,
        sent = sent,
        conflicts = conflicts,
        skipped = skipped,
        refused = refused,
        tooLarge = tooLarge.toList(),
        limitReached = limitReached,
    )

    private fun state() = NotesSyncState(entry.identity, notesRevision, notes.toMap())

    private fun pull(listed: NotesListOutcome.Listed) {
        listed.notes.forEach(::applyRecord)
        if (listed.full) {
            // A `full` answer lists every live record, so a synced note missing from it was purged as deleted.
            val live = listed.notes.mapTo(HashSet(), RemoteNote::noteId)
            notes.filterValues { it.noteId !in live }.keys.toList().forEach(::applyRemoteDelete)
        }
        notesRevision = listed.notesRevision
    }

    private fun applyRecord(record: RemoteNote) {
        val name = notes.entries.firstOrNull { it.value.noteId == record.noteId }?.key
        val synced = name?.let(notes::get)
        if (synced != null && record.revision <= synced.revision) return
        when (val content = record.content) {
            RemoteNoteContent.Deleted -> name?.let(::applyRemoteDelete)
            is RemoteNoteContent.Payload -> applyRemotePayload(record, content.json, name)
            RemoteNoteContent.Unopened, RemoteNoteContent.Unsupported -> skipped++
        }
    }

    private fun applyRemoteDelete(name: String) {
        val synced = notes.remove(name) ?: return
        val file = File(folder, name)
        // An edited local copy outlives the delete: with no mapping left, the push sends it as a new note.
        val unchanged = file.isFile && readText(file)?.let(NoteFileMapper::sha256Of) == synced.syncedSha256
        if (unchanged && file.delete()) received++
    }

    private fun applyRemotePayload(record: RemoteNote, json: ByteArray, name: String?) {
        val payload = decoded(json)
        val text = payload?.let(NoteFileMapper::toFileText)
        val target = payload?.let { targetFor(name, it) }?.takeIf { text != null && writeText(it, text) }
        if (payload != null && target != null && text != null) {
            notes[target.name] = syncedOf(record.noteId, record.revision, text, payload)
            received++
        } else {
            skipped++
        }
    }

    /** The file a stored version goes to, or null when writing it there could cost local text. */
    private fun targetFor(name: String?, payload: NotePayload): File? {
        val file = name?.let { File(folder, it) } ?: return uniqueFile(baseNameOf(payload))
        val local = if (file.exists()) readText(file) else null
        return when {
            // The local act was a delete and the stored record is a live edit: the edit wins (item FJ).
            !file.exists() -> file
            local == null -> null
            !changedSince(file.name, local) -> file
            keepLocalCopy(file.name, local)?.also { conflicts++ } != null -> file
            else -> null
        }
    }

    private suspend fun push() {
        noteFiles().forEach { file -> if (stopped == null) pushFile(file) }
        notes.keys.filterNot { File(folder, it).exists() }
            .forEach { name -> if (stopped == null) pushDelete(name) }
    }

    private suspend fun pushFile(file: File) {
        val text = readText(file)
        if (text != null && changedSince(file.name, text)) send(file.name, text, notes[file.name], attempt = 1)
    }

    private suspend fun send(name: String, text: String, previous: SyncedNote?, attempt: Int) {
        val stamp = Instant.ofEpochMilli(File(folder, name).lastModified()).toString()
        val payload = NoteFileMapper.fromFileText(text, previous, createdAt = stamp, updatedAt = stamp)
        val bytes = NotePayloadCodec.encode(payload)
        if (bytes.size > NotePayloadCodec.MAX_PAYLOAD_BYTES) {
            tooLarge += name
            return
        }
        val noteId = previous?.noteId ?: newNoteId()
        when (val outcome = remote.put(entry, noteId, previous?.revision ?: 0L, bytes)) {
            is NotesPutOutcome.Stored -> {
                notes[name] = syncedOf(noteId, outcome.revision, text, payload)
                sent++
            }
            is NotesPutOutcome.Conflict -> onEditConflict(name, text, previous, outcome.current, attempt)
            else -> onRefusal(name, outcome)
        }
    }

    private suspend fun onEditConflict(
        name: String,
        text: String,
        previous: SyncedNote?,
        current: RemoteNote,
        attempt: Int,
    ) {
        conflicts++
        if (attempt >= MAX_ATTEMPTS) {
            // A second conflict in one run means another device is still writing; the next sync tries again.
            Timber.w("Notes sync: note stopped after a repeated conflict")
            return
        }
        when (val content = current.content) {
            // The stored record is a tombstone: the local text survives as a new note.
            RemoteNoteContent.Deleted -> send(name, text, previous?.asNewNote(), attempt + 1)
            is RemoteNoteContent.Payload -> keepBoth(name, text, previous, current, content.json, attempt)
            RemoteNoteContent.Unopened, RemoteNoteContent.Unsupported -> skipped++
        }
    }

    // The stored version keeps the noteId and the file; the local version becomes a new note beside it.
    private suspend fun keepBoth(
        name: String,
        text: String,
        previous: SyncedNote?,
        current: RemoteNote,
        json: ByteArray,
        attempt: Int,
    ) {
        val stored = decoded(json)
        val copy = stored?.let { keepLocalCopy(name, text) }
        if (stored == null || copy == null) {
            skipped++
            return
        }
        val storedText = NoteFileMapper.toFileText(stored)
        if (writeText(File(folder, name), storedText)) {
            notes[name] = syncedOf(current.noteId, current.revision, storedText, stored)
            received++
        }
        send(copy.name, text, previous?.asNewNote(), attempt + 1)
    }

    private suspend fun pushDelete(name: String) {
        val synced = notes[name] ?: return
        when (val outcome = remote.put(entry, synced.noteId, synced.revision, null)) {
            is NotesPutOutcome.Stored -> {
                notes.remove(name)
                sent++
            }
            is NotesPutOutcome.Conflict -> onDeleteConflict(name, outcome.current)
            else -> onRefusal(name, outcome)
        }
    }

    private fun onDeleteConflict(name: String, current: RemoteNote) {
        conflicts++
        when (val content = current.content) {
            RemoteNoteContent.Deleted -> notes.remove(name)
            // The local act was a delete and the stored record is a live edit: the edit wins and the note stays.
            is RemoteNoteContent.Payload -> restore(name, current, content.json)
            RemoteNoteContent.Unopened, RemoteNoteContent.Unsupported -> skipped++
        }
    }

    private fun restore(name: String, current: RemoteNote, json: ByteArray) {
        val stored = decoded(json)
        val text = stored?.let(NoteFileMapper::toFileText)
        if (stored != null && text != null && writeText(File(folder, name), text)) {
            notes[name] = syncedOf(current.noteId, current.revision, text, stored)
            received++
        } else {
            skipped++
        }
    }

    private fun onRefusal(name: String, outcome: NotesPutOutcome) {
        when (outcome) {
            NotesPutOutcome.TooLarge -> tooLarge += name
            is NotesPutOutcome.Refused ->
                if (outcome.reason == REASON_LIMIT_REACHED) limitReached = true else refused++
            NotesPutOutcome.NotSealed -> stopped = NotesSyncOutcome.ServerNoE2e
            NotesPutOutcome.NotOffered -> stopped = NotesSyncOutcome.NotOffered
            NotesPutOutcome.Guest -> stopped = NotesSyncOutcome.Guest
            NotesPutOutcome.NoRecipient -> stopped = NotesSyncOutcome.NoRecipient
            is NotesPutOutcome.Stored, is NotesPutOutcome.Conflict -> Unit
        }
    }

    /** Writes [text] beside the note [name] under this device's name; null when the copy could not be written. */
    private fun keepLocalCopy(name: String, text: String): File? {
        val stem = name.substringBeforeLast('.')
        val copy = uniqueFile("$stem (${deviceName.ifBlank { COPY_FALLBACK }}).${NotesFolderPolicy.NOTE_EXTENSION}")
        return copy.takeIf { writeText(it, text) }
    }

    private fun changedSince(name: String, text: String): Boolean =
        notes[name]?.syncedSha256 != NoteFileMapper.sha256Of(text)

    private fun noteFiles(): List<File> = folder
        .listFiles { file -> file.isFile && file.extension.equals(NotesFolderPolicy.NOTE_EXTENSION, true) }
        .orEmpty()
        .sortedBy(File::getName)

    private fun baseNameOf(payload: NotePayload): String {
        val title = payload.title?.replace(UNSAFE_NAME_CHARS, "_")?.trim()?.take(MAX_TITLE_CHARS)
        return if (title.isNullOrBlank()) {
            val created = try {
                Instant.parse(payload.createdAt).toEpochMilli()
            } catch (e: DateTimeParseException) {
                Timber.w("Notes sync: unparsable createdAt (%s)", e.javaClass.simpleName)
                System.currentTimeMillis()
            }
            TextNoteFileNameProvider.defaultName(now = created)
        } else {
            "$title.${NotesFolderPolicy.NOTE_EXTENSION}"
        }
    }

    // A name of a pending local delete is taken too: reusing it would turn that delete into an edit.
    private fun uniqueFile(fileName: String): File {
        val stem = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', NotesFolderPolicy.NOTE_EXTENSION)
        return generateSequence(1) { it + 1 }
            .map { index -> if (index == 1) fileName else "$stem ($index).$extension" }
            .map { File(folder, it) }
            .first { !it.exists() && it.name !in notes }
    }

    private fun readText(file: File): String? = try {
        file.readText(Charsets.UTF_8)
    } catch (e: IOException) {
        Timber.w("Notes sync: unreadable note left untouched (%s)", e.javaClass.simpleName)
        null
    }

    private fun writeText(file: File, text: String): Boolean = try {
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("cannot create the notes folder")
        file.writeText(text, Charsets.UTF_8)
        true
    } catch (e: IOException) {
        Timber.w("Notes sync: note not written (%s)", e.javaClass.simpleName)
        false
    }

    private fun decoded(json: ByteArray): NotePayload? =
        (NotePayloadCodec.decode(json) as? NoteDecode.Decoded)?.payload

    private fun syncedOf(noteId: String, revision: Long, text: String, payload: NotePayload) = SyncedNote(
        noteId = noteId,
        revision = revision,
        syncedSha256 = NoteFileMapper.sha256Of(text),
        title = payload.title,
        kind = payload.kind,
        itemIds = payload.items.sortedBy(NoteItem::position).map(NoteItem::itemId),
        createdAt = payload.createdAt,
    )

    private fun SyncedNote.asNewNote() = copy(noteId = newNoteId(), revision = 0L)

    private fun newNoteId(): String = UUID.randomUUID().toString()

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val MAX_TITLE_CHARS = 80
        const val REASON_LIMIT_REACHED = "limit-reached"
        const val COPY_FALLBACK = "copy"
        val UNSAFE_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")
    }
}
