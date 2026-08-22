package com.sza.fastmediasorter.wear.domain.model

/**
 * S1708: The two halves of a downloaded catalog archive - the CSV text and the optional favicon
 * sprite atlas - handed from the ZIP reader to the import step.
 */
internal data class CatalogPayload(val csv: String, val atlasPng: ByteArray?)

/**
 * S1708: Outcome of one curated stream-catalog import on Wear OS.
 */
sealed interface CatalogImportResult {
    data class Success(val count: Int) : CatalogImportResult
    data object Empty : CatalogImportResult
    data class Failure(val reason: String) : CatalogImportResult
}
