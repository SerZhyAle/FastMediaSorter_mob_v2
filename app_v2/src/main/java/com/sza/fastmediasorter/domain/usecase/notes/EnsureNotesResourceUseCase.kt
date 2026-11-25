package com.sza.fastmediasorter.domain.usecase.notes

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.util.NotesFolderPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S4568: the local resource that holds the notes folder, whether or not it existed a moment ago.
 *
 * The text editor and its save flow are keyed by a resource id, so the Notes screen needs a real row
 * for its folder. One owner of that row's shape, the way the watch resource has one, keeps a second
 * builder from creating a twin that differs in one field.
 */
class EnsureNotesResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val addResourceUseCase: AddResourceUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    data class Outcome(val resourceId: Long, val created: Boolean)

    /**
     * @param name used only when the resource is created; an existing row keeps the name the user may
     *   have given it in the resource editor.
     */
    suspend operator fun invoke(name: String): Result<Outcome> = withContext(ioDispatcher) {
        runCatching {
            val folder = NotesFolderPolicy.ensureNotesDirectory().getOrThrow()
            val existing = resourceRepository.getAllResources().first()
                .firstOrNull { it.type == ResourceType.LOCAL && it.path == folder.absolutePath }
            if (existing != null) {
                Outcome(existing.id, created = false)
            } else {
                val createdId = addResourceUseCase(build(name, folder.absolutePath)).getOrThrow()
                Outcome(createdId, created = true)
            }
        }.onFailure { it.rethrowIfCancellation() }
    }

    private fun build(name: String, path: String): MediaResource = MediaResource(
        id = 0,
        name = name,
        path = path,
        type = ResourceType.LOCAL,
        supportedMediaTypes = setOf(MediaType.TEXT),
        createdDate = System.currentTimeMillis(),
        // A notes folder is a place to write notes, not a sorting target for media.
        isDestination = false,
        isWritable = true,
        isReadOnly = false,
        scanSubdirectories = false,
    )
}
