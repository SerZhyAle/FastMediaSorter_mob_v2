package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearCastAttempt
import com.sza.fastmediasorter.wear.domain.model.WearCastOrigin
import com.sza.fastmediasorter.wear.domain.model.WearCastRequest
import com.sza.fastmediasorter.wear.domain.model.WearCastSubject
import com.sza.fastmediasorter.wear.domain.repository.WearCastRepository
import java.util.UUID
import javax.inject.Inject

/** S2531: turns what a watch screen is showing into one cast request the phone can act on. */
class RequestCastOnPhoneUseCase @Inject constructor(
    private val wearCastRepository: WearCastRepository
) {

    suspend operator fun invoke(subject: WearCastSubject): WearCastAttempt {
        val request = requestFor(subject) ?: return WearCastAttempt.NotCastable
        return WearCastAttempt.Answered(wearCastRepository.requestCast(request))
    }

    private fun requestFor(subject: WearCastSubject): WearCastRequest? = when (subject) {
        is WearCastSubject.Stream -> streamRequest(subject)
        is WearCastSubject.NetworkFile -> networkRequest(subject)
        is WearCastSubject.WatchLocalFile -> null
    }

    private fun streamRequest(subject: WearCastSubject.Stream): WearCastRequest? {
        if (subject.url.isBlank()) {
            return null
        }
        return WearCastRequest(
            requestId = UUID.randomUUID().toString(),
            origin = WearCastOrigin.STREAM,
            address = subject.url,
            sourceId = 0L,
            mediaType = subject.mediaType,
            displayName = subject.displayName
        )
    }

    /**
     * A source added by hand on the watch has an identifier this watch invented, so it is refused here
     * rather than sent: only a source that arrived from the phone carries the phone's own numeric id.
     */
    private fun networkRequest(subject: WearCastSubject.NetworkFile): WearCastRequest? {
        val sourceId = subject.source.id.toLongOrNull() ?: return null
        return WearCastRequest(
            requestId = UUID.randomUUID().toString(),
            origin = WearCastOrigin.NETWORK_SOURCE,
            address = subject.relativePath,
            sourceId = sourceId,
            mediaType = subject.mediaType,
            displayName = subject.displayName
        )
    }
}
