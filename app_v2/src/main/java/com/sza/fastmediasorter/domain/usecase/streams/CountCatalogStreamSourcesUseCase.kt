package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S1918: how many catalog-origin stream sources are stored locally.
 *
 * Separates "the catalog was never downloaded" (zero) from "the catalog is downloaded", which the
 * welcome page needs before deciding whether a preset-enabled Streams toggle still owes the user an
 * import. Counts on the database side: after a successful import the catalog holds thousands of rows.
 */
class CountCatalogStreamSourcesUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
) {
    suspend operator fun invoke(): Int = repository.catalogSourceCount()
}
