package com.sza.fastmediasorter.wear.domain.model

/**
 * S1708: One curated catalog row for Wear OS.
 */
data class WearParsedCatalogEntry(
    val category: String,
    val topic: String,
    val name: String,
    val url: String,
    val mediaKind: String,
    val protocol: String,
    val format: String,
    val bitrate: String,
    val isLive: Boolean,
    val https: Boolean,
    val language: String,
    val country: String,
    val homepage: String,
    val sourceKind: String,
    val licenseNote: String,
    val notes: String,
    val confidence: String,
    val faviconIndex: Int?,
    val access: String = ""
)
