package com.sza.fastmediasorter.wear.domain.model

/**
 * S2488: the ordered set of addresses a source may be reached at.
 *
 * Kept pure and free of any Android or socket type: where candidates come from is separate from how
 * one is picked, so a later iteration that discovers the companion on the watch itself adds a source
 * of candidates without touching the connect path.
 */
object WearEndpointGroup {

    /**
     * Candidates for [source] in probe order. Never empty. A source with no imported group yields a
     * single element, which is what makes a hand-added source behave exactly as before.
     */
    fun candidatesFor(source: NetworkSource): List<WearEndpoint> {
        val primary = WearEndpoint(source.server, source.port)
        val imported = source.endpoints.orEmpty()
        if (imported.isEmpty()) return listOf(primary)
        return (imported + primary).distinct()
    }
}
