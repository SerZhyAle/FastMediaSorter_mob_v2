package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S0660: in-place edit of a user channel. Manual-only (strategic §6.4): CATALOG/IMPORTED rows are
 * refreshed by their own sync and must not be hand-edited, so a non-MANUAL source is rejected here
 * even though the DAO query is also scoped to MANUAL.
 *
 * S1145: [mediaKindOverride] lets the caller set the type explicitly (Audio/Video); a null override
 * keeps the S0660 behaviour of re-deriving the kind from the url (so an unchanged rtsp url stays RTSP).
 * The edit dialog drives the unique-`url` write path directly, so a url that already belongs to
 * another stream is rejected as [UpdateResult.Duplicate] rather than crashing on the unique index.
 *
 * S4606: [category] is the user's free-text list label, stored trimmed; a blank value clears it. The edit
 * dialog always submits the whole field, so a missing parameter is not "keep" - it has no default, and a
 * caller that forgets it fails to compile instead of wiping the label.
 */
class UpdateStreamSourceUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
    private val classifier: StreamMediaKindClassifier,
) {
    suspend operator fun invoke(
        source: StreamSourceEntity,
        url: String,
        title: String?,
        mediaKindOverride: String? = null,
        category: String?,
    ): UpdateResult {
        val trimmedUrl = url.trim()
        val rejection = rejectionFor(source, trimmedUrl)
        if (rejection != null) return rejection

        val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: source.title
        val resolvedKind = mediaKindOverride ?: classifier.classify(trimmedUrl)
        repository.updateUserFields(
            id = source.id,
            url = trimmedUrl,
            title = resolvedTitle,
            mediaKind = resolvedKind,
            category = category?.trim()?.takeIf { it.isNotEmpty() },
        )
        return UpdateResult.Success
    }

    private suspend fun rejectionFor(source: StreamSourceEntity, trimmedUrl: String): UpdateResult? = when {
        source.sourceOrigin != "MANUAL" -> UpdateResult.NotEditable
        !classifier.isSupportedScheme(trimmedUrl) -> UpdateResult.InvalidUrl
        isOwnedByAnother(source, trimmedUrl) -> UpdateResult.Duplicate
        else -> null
    }

    private suspend fun isOwnedByAnother(source: StreamSourceEntity, trimmedUrl: String): Boolean =
        repository.getByUrl(trimmedUrl)?.let { it.id != source.id } == true

    sealed interface UpdateResult {
        data object Success : UpdateResult
        data object InvalidUrl : UpdateResult
        data object NotEditable : UpdateResult
        data object Duplicate : UpdateResult
    }
}
