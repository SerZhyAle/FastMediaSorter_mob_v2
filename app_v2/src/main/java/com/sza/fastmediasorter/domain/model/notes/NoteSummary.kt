package com.sza.fastmediasorter.domain.model.notes

/** S4568: one row of the Notes list. The caption is derived from the body on every read, never stored. */
data class NoteSummary(
    val path: String,
    val caption: String,
    val excerpt: String,
    val modifiedAt: Long,
)
