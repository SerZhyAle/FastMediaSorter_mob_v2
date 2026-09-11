package com.sza.fastmediasorter.core.assistant.functions

import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.core.assistant.MediaAssistantAppFunctionService
import com.sza.fastmediasorter.core.assistant.model.SearchMediaParams
import com.sza.fastmediasorter.core.assistant.model.SearchMediaResult
import com.sza.fastmediasorter.domain.usecase.assistant.SearchAssistantMediaUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Assistant-callable AppFunctions for searching media files and folders across configured sources (S2920).
 */
@RequiresApi(MediaAssistantAppFunctionService.API_LEVEL)
class MediaSearchFunctions @Inject constructor(
    private val searchAssistantMediaUseCase: SearchAssistantMediaUseCase
) {

    /**
     * Search media files and folders across configured sources.
     *
     * @param params Query string and optional max limit of items to return.
     * @return List of matched media items and total match count.
     */
    suspend fun searchMedia(params: SearchMediaParams): SearchMediaResult {
        Timber.d("searchMedia query=%s, maxResults=%d", params.query, params.maxResults)
        return searchAssistantMediaUseCase(params)
    }
}
