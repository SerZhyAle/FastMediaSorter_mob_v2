package com.sza.fastmediasorter.domain.usecase.assistant

import com.sza.fastmediasorter.core.assistant.model.AssistantMediaItem
import com.sza.fastmediasorter.core.assistant.model.SearchMediaParams
import com.sza.fastmediasorter.core.assistant.model.SearchMediaResult
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * UseCase executing assistant-triggered media search across active configured sources (S2920).
 * Searches files and folders in all configured resources matching the query.
 */
class SearchAssistantMediaUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val getMediaFilesUseCase: GetMediaFilesUseCase
) {

    suspend operator fun invoke(params: SearchMediaParams): SearchMediaResult = withContext(Dispatchers.IO) {
        val query = params.query.trim()
        if (query.isEmpty()) {
            return@withContext SearchMediaResult(items = emptyList(), totalCount = 0)
        }

        val allResources = try {
            resourceRepository.getAllResourcesSync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "IO error loading resources for assistant search")
            emptyList()
        } catch (e: IllegalStateException) {
            Timber.w(e, "State error loading resources for assistant search")
            emptyList()
        }

        val matchedItems = mutableListOf<AssistantMediaItem>()

        for (resource in allResources) {
            try {
                val files = getMediaFilesUseCase(resource = resource).first()
                for (file in files) {
                    if (file.name.contains(query, ignoreCase = true)) {
                        matchedItems.add(
                            AssistantMediaItem(
                                resourceId = resource.id,
                                uri = file.contentUri ?: file.path,
                                displayName = file.name,
                                mimeType = null,
                                isFolder = file.isDirectory
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Timber.w(e, "IO error scanning resource %d for assistant search", resource.id)
            } catch (e: IllegalStateException) {
                Timber.w(e, "State error scanning resource %d for assistant search", resource.id)
            }
        }

        val limit = params.maxResults
        val finalItems = if (limit != null && limit > 0) {
            matchedItems.take(limit)
        } else {
            matchedItems
        }

        SearchMediaResult(
            items = finalItems,
            totalCount = matchedItems.size
        )
    }
}
