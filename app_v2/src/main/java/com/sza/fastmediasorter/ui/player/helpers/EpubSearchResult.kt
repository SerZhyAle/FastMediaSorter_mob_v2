package com.sza.fastmediasorter.ui.player.helpers

/**
 * Represents a single search result match found in an EPUB chapter.
 */
data class EpubSearchResult(
    /** Spine index of the chapter containing the match */
    val chapterIndex: Int,
    /** Display title of the chapter */
    val chapterTitle: String,
    /** Text snippet around the match (context window) */
    val contextSnippet: String,
    /** Start position of the match within the plain text */
    val matchStartInText: Int,
    /** The matched query string */
    val matchedText: String
)
