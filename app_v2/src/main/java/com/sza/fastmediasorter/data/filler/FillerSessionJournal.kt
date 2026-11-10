package com.sza.fastmediasorter.data.filler

import android.content.Context
import android.util.AtomicFile
import com.sza.fastmediasorter.domain.filler.FillerFile
import com.sza.fastmediasorter.domain.filler.FillerJournal
import com.sza.fastmediasorter.domain.filler.FillerSession
import com.sza.fastmediasorter.domain.filler.FillerTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class FillerJournalEntry(
    val session: FillerSession,
    val directory: String? = null,
    val pendingIndex: Int? = null,
    val files: List<String> = emptyList(),
)

/** Keeps incomplete sessions outside the selected volume so an ejected card can be recovered. */
@Singleton
class FillerSessionJournal @Inject constructor(
    @param:ApplicationContext context: Context,
) : FillerJournal {
    // noBackupFilesDir checks the directory on disk; resolving it lazily keeps that read inside read/change on IO.
    private val file by lazy { AtomicFile(File(context.noBackupFilesDir, JOURNAL_FILE)) }
    private val mutex = Mutex()

    override suspend fun pendingSessions(): List<FillerSession> = read { entries -> entries.map { it.session } }

    override suspend fun begin(target: FillerTarget): FillerSession = change { entries ->
        check(entries.isEmpty())
        val session = FillerSession(UUID.randomUUID().toString(), target)
        entries.add(FillerJournalEntry(session))
        session
    }

    override suspend fun prepareCreate(session: FillerSession, index: Int) = change { entries ->
        entries.update(session) { entry ->
            check(entry.pendingIndex == null)
            entry.copy(pendingIndex = index)
        }
    }

    override suspend fun recordCreated(session: FillerSession, index: Int, file: FillerFile) = change { entries ->
        entries.update(session) { entry ->
            check(entry.pendingIndex == index)
            check(file.id !in entry.files)
            entry.copy(pendingIndex = null, files = entry.files + file.id)
        }
    }

    override suspend fun finish(session: FillerSession) = change { entries ->
        val entry = entries.single { it.session == session }
        check(entry.pendingIndex == null && entry.files.isEmpty() && entry.directory == null)
        entries.remove(entry)
        Unit
    }

    internal suspend fun entry(session: FillerSession): FillerJournalEntry = read { entries ->
        entries.single { it.session == session }
    }

    internal suspend fun setDirectory(session: FillerSession, directory: String?) = change { entries ->
        entries.update(session) { it.copy(directory = directory) }
    }

    internal suspend fun clearPending(session: FillerSession) = change { entries ->
        entries.update(session) { it.copy(pendingIndex = null) }
    }

    internal suspend fun forgetFile(session: FillerSession, fileId: String) = change { entries ->
        entries.update(session) { entry -> entry.copy(files = entry.files - fileId) }
    }

    private suspend fun <T> read(block: (List<FillerJournalEntry>) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block(load()) }
    }

    private suspend fun <T> change(block: (MutableList<FillerJournalEntry>) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = load().toMutableList()
            val result = block(entries)
            save(entries)
            result
        }
    }

    private fun load(): List<FillerJournalEntry> {
        val bytes = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        val json = JSONArray(bytes.toString(Charsets.UTF_8))
        return List(json.length()) { index -> decode(json.getJSONObject(index)) }
    }

    private fun decode(json: JSONObject): FillerJournalEntry {
        val session = FillerSession(
            id = json.getString("id"),
            target = FillerTarget(json.getString("location"), json.getString("volume")),
        )
        val files = json.getJSONArray("files")
        return FillerJournalEntry(
            session = session,
            directory = if (json.isNull("directory")) null else json.getString("directory"),
            pendingIndex = if (json.isNull("pending")) null else json.getInt("pending"),
            files = List(files.length()) { files.getString(it) },
        )
    }

    private fun save(entries: List<FillerJournalEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.session.id)
                    .put("location", entry.session.target.location)
                    .put("volume", entry.session.target.volumeId)
                    .put("directory", entry.directory ?: JSONObject.NULL)
                    .put("pending", entry.pendingIndex ?: JSONObject.NULL)
                    .put("files", JSONArray(entry.files)),
            )
        }
        val output = file.startWrite()
        var complete = false
        try {
            output.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
            complete = true
        } finally {
            if (!complete) file.failWrite(output)
        }
    }

    private fun MutableList<FillerJournalEntry>.update(
        session: FillerSession,
        transform: (FillerJournalEntry) -> FillerJournalEntry,
    ) {
        val index = indexOfFirst { it.session == session }
        check(index >= 0)
        this[index] = transform(this[index])
    }

    private companion object {
        const val JOURNAL_FILE = "filler-sessions.json"
    }
}
