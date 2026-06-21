package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import java.util.UUID
import javax.inject.Inject

class AddStreamSourceUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
    private val classifier: StreamMediaKindClassifier
) {
    suspend operator fun invoke(url: String, title: String?): AddResult {
        val trimmedUrl = url.trim()
        if (!classifier.isSupportedScheme(trimmedUrl)) return AddResult.InvalidUrl

        val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: deriveTitle(trimmedUrl)
        val entity = StreamSourceEntity(
            id = UUID.randomUUID().toString(),
            url = trimmedUrl,
            title = resolvedTitle,
            mediaKind = classifier.classify(trimmedUrl),
            sourceOrigin = "MANUAL",
            // New manual items take the default order; pin-to-top handles promotion afterwards.
            sortIndex = 0,
            addedAt = System.currentTimeMillis()
        )
        repository.add(entity)
        return AddResult.Success
    }

    private fun deriveTitle(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.takeIf { it.isNotEmpty() } ?: url
    }

    sealed interface AddResult {
        data object Success : AddResult
        data object InvalidUrl : AddResult

        /** Kept for the importer's count semantics; a single upserting add cannot detect it here. */
        data object Duplicate : AddResult
    }
}
