package com.sza.fastmediasorter.domain.delivery

/**
 * S4459: what the mirror says about the stream catalog it currently serves.
 *
 * [stamp] changes exactly when the maintainer republishes `stream-catalog.zip`, so comparing it with the
 * stamp stored at the last import answers "is there a newer list" without downloading the archive.
 * [lastModifiedMillis] is the mirror's own `Last-Modified`; it is only used for installs that imported
 * the catalog before stamps were stored, where it is compared with the device's last-import time.
 */
data class PublishedStreamCatalog(
    val stamp: String,
    val lastModifiedMillis: Long?,
) {
    companion object {
        private const val LAST_MODIFIED_PREFIX = "lm:"

        /**
         * Builds the stamp from response headers. The probe (`HEAD`) and the import (`GET`) both go through
         * this one function, because a stamp written by one and compared by the other must come out of
         * the same rule or an unchanged archive would look new. No header at all gives null: a weaker
         * stamp such as the length would call a same-size republish "unchanged".
         */
        fun stampOf(etag: String?, lastModified: String?): String? =
            etag?.trim()?.takeIf { it.isNotEmpty() }
                ?: lastModified?.trim()?.takeIf { it.isNotEmpty() }?.let { LAST_MODIFIED_PREFIX + it }
    }
}

/**
 * S4459: reads [PublishedStreamCatalog] off the mirror.
 *
 * Absence is not an error: no network, an HTTP failure, a response without a usable header - every one
 * of them yields null, which the caller reads as "do not offer". An offer that cannot be justified is
 * worse than a missing one, because the whole point of the check is to stop offering for nothing.
 */
interface StreamCatalogPublicationSource {

    suspend fun published(): PublishedStreamCatalog?
}
