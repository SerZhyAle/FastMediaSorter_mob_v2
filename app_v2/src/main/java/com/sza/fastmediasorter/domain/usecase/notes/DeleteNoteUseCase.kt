package com.sza.fastmediasorter.domain.usecase.notes

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.util.NotesFolderPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * S4568: deletes one note of the notes folder.
 *
 * Refuses a path outside that folder, so a stale or forged row can never reach another file.
 */
class DeleteNoteUseCase @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(path: String): Result<Unit> = withContext(ioDispatcher) {
        val file = File(path)
        val folder = NotesFolderPolicy.notesDirectory().absoluteFile
        when {
            file.absoluteFile.parentFile != folder ->
                Result.failure(IllegalArgumentException("Not a note of the notes folder: $path"))
            file.delete() || !file.exists() -> Result.success(Unit)
            else -> Result.failure(IllegalStateException("Note could not be deleted: $path"))
        }
    }

    /**
     * S4865: deletes a set of notes and returns how many could not be deleted. Every path goes through
     * [invoke], so the notes-folder guard holds for each one and a single failure never stops the rest.
     */
    suspend fun deleteAll(paths: Collection<String>): Int = paths.count { path -> invoke(path).isFailure }
}
