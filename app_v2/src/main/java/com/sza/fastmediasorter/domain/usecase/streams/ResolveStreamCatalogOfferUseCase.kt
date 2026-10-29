package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.settings.StreamsSessionStore
import com.sza.fastmediasorter.domain.delivery.PublishedStreamCatalog
import com.sza.fastmediasorter.domain.delivery.StreamCatalogPublicationSource
import javax.inject.Inject

/**
 * S4459: decides whether the Streams screen should offer "update the channel list" right now.
 *
 * The offer used to rest on a timer alone - six hours since the last import - so a maintainer who
 * publishes a new list rarely still saw it on nearly every open, and closing the banner changed nothing.
 * It now rests on what is published: the mirror's stamp is compared with the stamp stored at the last
 * import and with the stamp whose offer the user already closed, and the banner is offered only for a
 * stamp that is neither.
 *
 * Returns the published stamp to offer, or null for "do not offer". The caller keeps the stamp so that
 * closing the banner can store exactly the one it was shown for.
 *
 * S4461: [isUpdateAvailable] serves the automatic policy with the same rule, minus the closed offer. A
 * closed banner belongs to the suggesting policy only; a user who chose automatic updating wants a newly
 * published list whatever he once closed, and the archive is downloaded only when this answers true.
 */
class ResolveStreamCatalogOfferUseCase @Inject constructor(
    private val publication: StreamCatalogPublicationSource,
    private val sessionStore: StreamsSessionStore,
) {

    suspend operator fun invoke(): String? = resolve(honourDismissal = true)

    suspend fun isUpdateAvailable(): Boolean = resolve(honourDismissal = false) != null

    private suspend fun resolve(honourDismissal: Boolean): String? {
        val session = sessionStore.read()
        return decide(
            published = publication.published(),
            installed = session.catalogInstalledStamp,
            dismissed = session.catalogDismissedStamp.takeIf { honourDismissal },
            lastRefreshAt = session.lastCatalogRefreshAt,
        )
    }

    companion object {

        /**
         * The whole rule, free of I/O so every row of it can be tested.
         *
         * The third branch is for installs that imported the catalog before stamps were stored: there is
         * nothing to compare the published stamp with, so the file's own `Last-Modified` is compared with
         * the device's last-import time instead, which keeps the update that introduced stamps from
         * producing one pointless offer. A file with no `Last-Modified` cannot be judged and is not offered.
         * An install that never imported (`lastRefreshAt == 0`) falls through to the last branch, which
         * keeps the first-catalog path the banner exists for.
         */
        internal fun decide(
            published: PublishedStreamCatalog?,
            installed: String?,
            dismissed: String?,
            lastRefreshAt: Long,
        ): String? = when {
            published == null -> null
            published.stamp == installed || published.stamp == dismissed -> null
            installed == null && lastRefreshAt > 0L ->
                published.stamp.takeIf { (published.lastModifiedMillis ?: 0L) > lastRefreshAt }
            else -> published.stamp
        }
    }
}
