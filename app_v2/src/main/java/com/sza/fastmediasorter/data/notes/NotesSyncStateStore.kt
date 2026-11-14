package com.sza.fastmediasorter.data.notes

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the last sync knew about one note file (DEVICE-EXCHANGE 1.13 item FM): the record it maps to, and the
 * fields a `.txt` cannot hold, so a change made on the phone never drops a title, a kind or an item id (item FH).
 */
data class SyncedNote(
    val noteId: String,
    val revision: Long,
    val syncedSha256: String,
    val title: String?,
    val kind: NoteKind,
    val itemIds: List<String>,
    val createdAt: String,
) {
    override fun toString(): String = "SyncedNote(noteId=$noteId, revision=$revision, kind=$kind)"
}

/** The sync state of one device: the entry it syncs with, the last `notesRevision` and every note by file name. */
data class NotesSyncState(
    val entryIdentity: String?,
    val notesRevision: Long,
    val notes: Map<String, SyncedNote>,
) {
    companion object {
        val EMPTY = NotesSyncState(entryIdentity = null, notesRevision = 0L, notes = emptyMap())
    }
}

/**
 * The app-private JSON file holding [NotesSyncState] (S4572 ADR-3), never a Room table. A lost or unreadable file
 * reads as empty, so the next sync treats every note as new - duplicates, never a loss.
 */
@Singleton
class NotesSyncStateStore(private val file: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(File(context.filesDir, DIRECTORY), FILE_NAME))

    private val mutex = Mutex()

    suspend fun read(): NotesSyncState = mutex.withLock { withContext(Dispatchers.IO) { readLocked() } }

    suspend fun write(state: NotesSyncState) = mutex.withLock { withContext(Dispatchers.IO) { writeLocked(state) } }

    /** Reads, transforms and writes under one lock, so two syncs never interleave their updates. */
    suspend fun update(transform: (NotesSyncState) -> NotesSyncState): NotesSyncState = mutex.withLock {
        withContext(Dispatchers.IO) { transform(readLocked()).also(::writeLocked) }
    }

    private fun readLocked(): NotesSyncState {
        if (!file.isFile) return NotesSyncState.EMPTY
        val state = runCatching { stateOf(JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject) }
            .onFailure { Timber.w("Notes sync state unreadable, starting empty (%s)", it.javaClass.simpleName) }
            .getOrNull()
        return state ?: NotesSyncState.EMPTY
    }

    private fun writeLocked(state: NotesSyncState) {
        val parent = file.parentFile?.takeIf { it.isDirectory || it.mkdirs() }
            ?: throw IOException("cannot create the notes sync directory")
        val temp = File(parent, "$FILE_NAME.tmp")
        temp.writeText(jsonOf(state).toString(), Charsets.UTF_8)
        // A JVM test host on Windows refuses to rename over an existing file; Android replaces it atomically.
        val replaced = temp.renameTo(file) || (file.delete() && temp.renameTo(file))
        if (!replaced) throw IOException("cannot replace the notes sync state")
    }

    private fun jsonOf(state: NotesSyncState): JsonObject = JsonObject().apply {
        addProperty(KEY_VERSION, FILE_VERSION)
        add(KEY_ENTRY, state.entryIdentity?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        addProperty(KEY_NOTES_REVISION, state.notesRevision)
        add(
            KEY_NOTES,
            JsonObject().apply { state.notes.forEach { (fileName, note) -> add(fileName, noteJson(note)) } },
        )
    }

    private fun noteJson(note: SyncedNote): JsonObject = JsonObject().apply {
        addProperty(KEY_NOTE_ID, note.noteId)
        addProperty(KEY_REVISION, note.revision)
        addProperty(KEY_SHA256, note.syncedSha256)
        add(KEY_TITLE, note.title?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        addProperty(KEY_KIND, note.kind.wire)
        add(KEY_ITEM_IDS, JsonArray().apply { note.itemIds.forEach(::add) })
        addProperty(KEY_CREATED_AT, note.createdAt)
    }

    private fun stateOf(json: JsonObject): NotesSyncState {
        val notes = json.getAsJsonObject(KEY_NOTES)?.entrySet().orEmpty()
            .mapNotNull { (fileName, value) ->
                value.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let(::noteOf)?.let { fileName to it }
            }
            .toMap()
        return NotesSyncState(
            entryIdentity = json.get(KEY_ENTRY)?.takeUnless(JsonElement::isJsonNull)?.asString,
            notesRevision = json.get(KEY_NOTES_REVISION)?.asLong ?: 0L,
            notes = notes,
        )
    }

    private fun noteOf(json: JsonObject): SyncedNote? = runCatching {
        SyncedNote(
            noteId = json.get(KEY_NOTE_ID).asString,
            revision = json.get(KEY_REVISION).asLong,
            syncedSha256 = json.get(KEY_SHA256).asString,
            title = json.get(KEY_TITLE)?.takeUnless(JsonElement::isJsonNull)?.asString,
            kind = requireNotNull(NoteKind.fromWire(json.get(KEY_KIND).asString)),
            itemIds = json.getAsJsonArray(KEY_ITEM_IDS)?.map(JsonElement::getAsString).orEmpty(),
            createdAt = json.get(KEY_CREATED_AT).asString,
        )
    }.getOrNull()

    private companion object {
        const val DIRECTORY = "notes-sync"
        const val FILE_NAME = "state.json"
        const val FILE_VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_ENTRY = "entryIdentity"
        const val KEY_NOTES_REVISION = "notesRevision"
        const val KEY_NOTES = "notes"
        const val KEY_NOTE_ID = "noteId"
        const val KEY_REVISION = "revision"
        const val KEY_SHA256 = "syncedSha256"
        const val KEY_TITLE = "title"
        const val KEY_KIND = "kind"
        const val KEY_ITEM_IDS = "itemIds"
        const val KEY_CREATED_AT = "createdAt"
    }
}
