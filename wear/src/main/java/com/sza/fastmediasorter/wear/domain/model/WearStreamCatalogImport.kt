package com.sza.fastmediasorter.wear.domain.model

/**
 * S1708/S2669: the halves of a downloaded catalog archive - the CSV text, the optional favicon
 * sprite atlas, and the optional curated-collections payload - handed from the ZIP reader to the
 * import step. `collectionsJson` is null when the archive carried no such entry, which leaves the
 * stored collections untouched rather than clearing them.
 */
internal data class CatalogPayload(
    val csv: String,
    val atlasPng: ByteArray?,
    val collectionsJson: String? = null
)

/**
 * S1708: Outcome of one curated stream-catalog import on Wear OS.
 */
sealed interface CatalogImportResult {
    data class Success(val count: Int) : CatalogImportResult
    data object Empty : CatalogImportResult
    data class Failure(val reason: String) : CatalogImportResult
}
