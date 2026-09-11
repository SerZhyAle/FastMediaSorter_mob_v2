package com.sza.fastmediasorter.core.assistant.model

/**
 * Result schema for assistant media search action (S2920).
 *
 * @property items Found media items matching the search query.
 * @property totalCount Total number of matched items across searched resources.
 */
data class SearchMediaResult(
    val items: List<AssistantMediaItem>,
    val totalCount: Int
)
