package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import java.util.UUID
import javax.inject.Inject

class AddStreamSourceUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
    private val classifier: StreamMediaKindClassifier,
    private val statsSink: StatsSink,
) {
    suspend operator fun invoke(url: String, title: String?): AddResult {
        val trimmedUrl = url.trim()
        if (!classifier.isSupportedScheme(trimmedUrl)) return AddResult.InvalidUrl

        // S1147: a row already owning this url collides with the unique index_stream_sources_url, and
        // dao.upsert only resolves conflicts on the primary key - the raw insert would crash with an
        // unhandled SQLiteConstraintException. Reject as Duplicate up front (mirrors the S1145 edit path).
        if (repository.getByUrl(trimmedUrl) != null) return AddResult.Duplicate

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
        statsSink.record(StatsEvent.StreamAdded)
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

        /** S1147: url already belongs to an existing row (unique index_stream_sources_url collision). */
        data object Duplicate : AddResult
    }
}
