package com.sza.fastmediasorter.util

import android.os.Environment
import java.io.File

/**
 * S4568: the one folder the Notes sub-program reads and writes.
 *
 * Public `Documents/Notes` through the file API gives notes exactly the reach the existing text-note
 * fallback and the "All documents" resource already have in each flavor: every note with all-files
 * access, every note this install wrote without it.
 */
object NotesFolderPolicy {

    private const val NOTES_DIRECTORY_NAME = "Notes"

    const val NOTE_EXTENSION = "txt"

    fun notesDirectory(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), NOTES_DIRECTORY_NAME)

    /**
     * S4717: whether [path] is the notes folder itself, compared the way [notesDirectory] builds it, so a
     * trailing separator does not make the same folder look like another one.
     */
    fun isNotesDirectory(path: String): Boolean = File(path).path == notesDirectory().path

    fun ensureNotesDirectory(): Result<File> {
        val dir = notesDirectory()
        if (dir.isDirectory) return Result.success(dir)
        return if (dir.mkdirs() || dir.isDirectory) {
            Result.success(dir)
        } else {
            Result.failure(IllegalStateException("Notes directory is not available: ${dir.absolutePath}"))
        }
    }
}
