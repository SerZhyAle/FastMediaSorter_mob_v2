package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S0660: in-place edit of a user channel. Manual-only (strategic §6.4): CATALOG/IMPORTED rows are
 * refreshed by their own sync and must not be hand-edited, so a non-MANUAL source is rejected here
 * even though the DAO query is also scoped to MANUAL. The media kind is re-derived from the new url
 * so an edit that changes the scheme keeps the launch routing correct.
 */
class UpdateStreamSourceUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
    private val classifier: StreamMediaKindClassifier,
) {
    suspend operator fun invoke(source: StreamSourceEntity, url: String, title: String?): UpdateResult {
        if (source.sourceOrigin != "MANUAL") return UpdateResult.NotEditable

        val trimmedUrl = url.trim()
        if (!classifier.isSupportedScheme(trimmedUrl)) return UpdateResult.InvalidUrl

        val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: source.title
        repository.updateUserFields(
            id = source.id,
            url = trimmedUrl,
            title = resolvedTitle,
            mediaKind = classifier.classify(trimmedUrl),
        )
        return UpdateResult.Success
    }

    sealed interface UpdateResult {
        data object Success : UpdateResult
        data object InvalidUrl : UpdateResult
        data object NotEditable : UpdateResult
    }
}
