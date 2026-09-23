package com.sza.fastmediasorter.wear.complication

import android.content.res.Resources
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearComplicationContent
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget

/** The text a complication renders, already in the watch's language. */
data class WearComplicationText(
    val shortText: String,
    val longText: String,
    val contentDescription: String,
    val launchTarget: WearLaunchTarget?
)

/**
 * S3404: turns semantic complication content into localized text.
 *
 * Lives beside the complication service because that is where a Context legitimately exists; the domain
 * use case that loads the content cannot read resources.
 */
class WearComplicationTextFormatter(private val resources: Resources) {

    /** Null for [WearComplicationContent.Empty]: the source renders nothing rather than a placeholder. */
    fun format(content: WearComplicationContent): WearComplicationText? = when (content) {
        WearComplicationContent.Empty -> null
        is WearComplicationContent.LastResource -> WearComplicationText(
            shortText = content.caption,
            longText = content.caption,
            contentDescription = resources.getString(
                R.string.wear_complication_last_resource_a11y,
                content.caption
            ),
            launchTarget = content.launchTarget
        )
        is WearComplicationContent.FavoritesCount -> formatFavoritesCount(content)
        is WearComplicationContent.NowPlaying -> formatNowPlaying(content)
    }

    private fun formatFavoritesCount(content: WearComplicationContent.FavoritesCount): WearComplicationText {
        val label = resources.getQuantityString(
            R.plurals.wear_complication_favorites_count,
            content.count,
            content.count
        )
        return WearComplicationText(
            shortText = content.count.toString(),
            longText = label,
            contentDescription = label,
            launchTarget = content.launchTarget
        )
    }

    private fun formatNowPlaying(content: WearComplicationContent.NowPlaying): WearComplicationText {
        val longText = if (content.subtitle.isNullOrBlank()) {
            content.title
        } else {
            "${content.title} - ${content.subtitle}"
        }
        val descriptionRes = if (content.isPlaying) {
            R.string.wear_complication_now_playing_a11y
        } else {
            R.string.wear_complication_last_played_a11y
        }
        return WearComplicationText(
            shortText = content.title,
            longText = longText,
            contentDescription = resources.getString(descriptionRes, content.title),
            launchTarget = null
        )
    }
}
