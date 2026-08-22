package com.sza.fastmediasorter.wear.domain.model

/**
 * S1836: the network source the watch opened last - [id] addresses it, [name] captions its home row.
 */
data class LastUsedResource(
    val id: String,
    val name: String
)
