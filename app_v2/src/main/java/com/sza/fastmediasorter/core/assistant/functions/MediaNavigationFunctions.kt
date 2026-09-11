package com.sza.fastmediasorter.core.assistant.functions

import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.core.assistant.MediaAssistantAppFunctionService
import com.sza.fastmediasorter.core.assistant.model.OpenFolderParams
import com.sza.fastmediasorter.core.assistant.model.OpenFolderResult
import com.sza.fastmediasorter.core.assistant.model.OpenMediaParams
import com.sza.fastmediasorter.core.assistant.model.OpenMediaResult
import com.sza.fastmediasorter.core.assistant.navigation.OpenAssistantFolderUseCase
import com.sza.fastmediasorter.core.assistant.navigation.OpenAssistantMediaUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Assistant-callable AppFunctions for opening media in player and browsing folders (S2920).
 */
@RequiresApi(MediaAssistantAppFunctionService.API_LEVEL)
class MediaNavigationFunctions @Inject constructor(
    private val openAssistantMediaUseCase: OpenAssistantMediaUseCase,
    private val openAssistantFolderUseCase: OpenAssistantFolderUseCase
) {

    /**
     * Open a media item in the player.
     *
     * @param params Resource ID and optional file path.
     * @return Status of the open operation.
     */
    fun openMedia(params: OpenMediaParams): OpenMediaResult {
        Timber.d("openMedia resourceId=%d, filePath=%s", params.resourceId, params.filePath)
        return openAssistantMediaUseCase(params)
    }

    /**
     * Open a source or folder in the browser.
     *
     * @param params Resource ID and optional folder path.
     * @return Status of the open operation.
     */
    fun openFolder(params: OpenFolderParams): OpenFolderResult {
        Timber.d("openFolder resourceId=%d, folderPath=%s", params.resourceId, params.folderPath)
        return openAssistantFolderUseCase(params)
    }
}
