package com.sza.fastmediasorter.core.assistant.model

/**
 * Parameters for assistant media search action (S2920).
 *
 * @property query Search query matching media titles or filenames.
 * @property maxResults Optional ceiling on the number of returned media items.
 */
data class SearchMediaParams(
    val query: String,
    val maxResults: Int? = null
)
