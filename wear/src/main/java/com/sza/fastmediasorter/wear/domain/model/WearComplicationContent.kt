package com.sza.fastmediasorter.wear.domain.model

/**
 * S2047: representation of content rendered by Wear OS complications.
 *
 * S3404: the variants carry meaning, never display text. The domain has no Context, so text built here
 * could only be an English literal; the complication service turns these into localized text.
 */
sealed interface WearComplicationContent {

    data class LastResource(
        val caption: String,
        val launchTarget: WearLaunchTarget
    ) : WearComplicationContent

    data class FavoritesCount(
        val count: Int,
        val launchTarget: WearLaunchTarget
    ) : WearComplicationContent

    data class NowPlaying(
        val title: String,
        val subtitle: String?,
        val isPlaying: Boolean
    ) : WearComplicationContent

    /**
     * Indicates the source has no content to show (e.g. no favorites, nothing played, no last resource).
     */
    data object Empty : WearComplicationContent
}
