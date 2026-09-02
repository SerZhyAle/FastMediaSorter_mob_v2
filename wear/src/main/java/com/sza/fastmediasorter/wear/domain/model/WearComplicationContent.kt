package com.sza.fastmediasorter.wear.domain.model

/**
 * S2047: representation of content rendered by Wear OS complications.
 */
sealed interface WearComplicationContent {

    /**
     * Complication content available to render.
     */
    data class Value(
        val shortText: String,
        val longText: String?,
        val contentDescription: String,
        val launchTarget: WearLaunchTarget?
    ) : WearComplicationContent

    /**
     * Indicates the source has no content to show (e.g. no favourites, nothing played, no last resource).
     */
    data object Empty : WearComplicationContent
}
