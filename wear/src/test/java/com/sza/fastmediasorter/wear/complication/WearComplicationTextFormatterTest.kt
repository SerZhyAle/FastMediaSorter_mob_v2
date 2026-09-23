package com.sza.fastmediasorter.wear.complication

import android.content.res.Resources
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearComplicationContent
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearComplicationTextFormatterTest {

    private val resources: Resources = mockk()
    private val formatter = WearComplicationTextFormatter(resources)
    private val favoritesTarget = WearLaunchTarget.Open(WearTileTargetRef.Favourites)
    private val streamTarget = WearLaunchTarget.Open(WearTileTargetRef.Stream("ch1"))

    @Test
    fun emptyContentRendersNothing() {
        assertNull(formatter.format(WearComplicationContent.Empty))
    }

    @Test
    fun lastResourceDescriptionComesFromResources() {
        every {
            resources.getString(R.string.wear_complication_last_resource_a11y, "Movies")
        } returns "Last resource: Movies"

        val text = formatter.format(WearComplicationContent.LastResource("Movies", streamTarget))

        assertEquals(
            WearComplicationText("Movies", "Movies", "Last resource: Movies", streamTarget),
            text
        )
    }

    @Test
    fun favoritesCountUsesPluralsForLabelAndDescription() {
        every {
            resources.getQuantityString(R.plurals.wear_complication_favorites_count, 2, 2)
        } returns "2 favorites"

        val text = formatter.format(WearComplicationContent.FavoritesCount(2, favoritesTarget))

        assertEquals(WearComplicationText("2", "2 favorites", "2 favorites", favoritesTarget), text)
    }

    @Test
    fun nowPlayingWhilePlayingUsesPlayingDescription() {
        every {
            resources.getString(R.string.wear_complication_now_playing_a11y, "Track A")
        } returns "Playing: Track A"

        val text = formatter.format(WearComplicationContent.NowPlaying("Track A", "Artist B", isPlaying = true))

        assertEquals(WearComplicationText("Track A", "Track A - Artist B", "Playing: Track A", null), text)
    }

    @Test
    fun pausedNowPlayingWithBlankSubtitleUsesLastPlayedDescriptionAndTitleOnly() {
        every {
            resources.getString(R.string.wear_complication_last_played_a11y, "Track A")
        } returns "Last played: Track A"

        val text = formatter.format(WearComplicationContent.NowPlaying("Track A", " ", isPlaying = false))

        assertEquals(WearComplicationText("Track A", "Track A", "Last played: Track A", null), text)
    }
}
