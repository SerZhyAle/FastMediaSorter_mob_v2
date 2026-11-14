package com.sza.fastmediasorter.domain.usecase.notes

import com.sza.fastmediasorter.util.NotesFolderPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** S4568: one row of the Notes list. The caption is derived from the body on every read, never stored. */
data class NoteSummary(
    val path: String,
    val caption: String,
    val excerpt: String,
    val modifiedAt: Long,
)

/**
 * S4568: the notes of the notes folder, newest first, optionally narrowed by a search query.
 *
 * A row needs only the first two non-blank lines, so a bounded prefix is read per file; the whole body
 * is read only while a query has to look inside it.
 */
class ListNotesUseCase @Inject constructor() {

    suspend operator fun invoke(query: String = ""): List<NoteSummary> = withContext(Dispatchers.IO) {
        val files = NotesFolderPolicy.notesDirectory()
            .listFiles { file -> file.isFile && file.extension.equals(NotesFolderPolicy.NOTE_EXTENSION, true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
        val needle = query.trim()
        files.mapNotNull { file -> summarize(file, needle) }
    }

    private fun summarize(file: File, needle: String): NoteSummary? {
        val text = readNote(file, needle) ?: return null
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(2).toList()
        val caption = lines.firstOrNull() ?: file.nameWithoutExtension
        val matches = needle.isEmpty() ||
            caption.contains(needle, ignoreCase = true) ||
            text.contains(needle, ignoreCase = true)
        return if (matches) {
            NoteSummary(
                path = file.absolutePath,
                caption = caption.take(MAX_LINE_CHARS),
                excerpt = lines.getOrNull(1).orEmpty().take(MAX_LINE_CHARS),
                modifiedAt = file.lastModified(),
            )
        } else {
            null
        }
    }

    private fun readNote(file: File, needle: String): String? = try {
        if (needle.isEmpty()) readPrefix(file) else file.readText(Charsets.UTF_8)
    } catch (e: IOException) {
        Timber.w(e, "ListNotesUseCase: unreadable note skipped: ${file.name}")
        null
    }

    private fun readPrefix(file: File): String = file.bufferedReader(Charsets.UTF_8).use { reader ->
        val buffer = CharArray(PREFIX_CHARS)
        val read = reader.read(buffer)
        if (read <= 0) "" else String(buffer, 0, read)
    }

    private companion object {
        const val PREFIX_CHARS = 2048
        const val MAX_LINE_CHARS = 200
    }
}
