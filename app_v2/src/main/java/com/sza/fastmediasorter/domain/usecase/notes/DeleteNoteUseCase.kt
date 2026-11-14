package com.sza.fastmediasorter.domain.usecase.notes

import com.sza.fastmediasorter.util.NotesFolderPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * S4568: deletes one note of the notes folder.
 *
 * Refuses a path outside that folder, so a stale or forged row can never reach another file.
 */
class DeleteNoteUseCase @Inject constructor() {

    suspend operator fun invoke(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(path)
        val folder = NotesFolderPolicy.notesDirectory().absoluteFile
        when {
            file.absoluteFile.parentFile != folder ->
                Result.failure(IllegalArgumentException("Not a note of the notes folder: $path"))
            file.delete() || !file.exists() -> Result.success(Unit)
            else -> Result.failure(IllegalStateException("Note could not be deleted: $path"))
        }
    }
}
