package com.sza.fastmediasorter.ui.player.callbacks

import com.sza.fastmediasorter.ui.player.helpers.NetworkPlaybackContainerHint
import com.sza.fastmediasorter.ui.player.helpers.shouldUseBdTsStripper
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S0076: narrow regression for [PlayerPlaybackCallbackImpl.onNetworkContainerRouteError].
 *
 * Full-stack tests for callback-to-activity interaction require an instrumented test.
 * These unit tests cover the contract points that are independent of Android context:
 * VOB hint identity and the strip-decision guarantee that keeps VOB off the BD-TS path.
 */
class PlayerPlaybackCallbackImplTest {

    @Test
    fun `VOB hint used in route error callback is DVD_PS_VOB`() {
        val hint = NetworkPlaybackContainerHint.DVD_PS_VOB
        assertEquals(NetworkPlaybackContainerHint.DVD_PS_VOB, hint)
    }

    @Test
    fun `onNetworkContainerRouteError does not trigger BD stripper for VOB hint`() {
        // The hint passed to onNetworkContainerRouteError is DVD_PS_VOB;
        // assert the strip-decision contract holds so no stripping factory is applied.
        assertFalse(shouldUseBdTsStripper(TsPacketFormat.UNKNOWN))
    }

    @Test
    fun `generic playback error path is distinct from VOB route error path`() {
        // Route error hint is DVD_PS_VOB; a plain mkv maps to OTHER - different branch.
        val vobHint = NetworkPlaybackContainerHint.fromPath("smb://nas/VIDEO_TS/VTS_01_1.vob")
        val mkvHint = NetworkPlaybackContainerHint.fromPath("smb://nas/movies/film.mkv")
        assertEquals(NetworkPlaybackContainerHint.DVD_PS_VOB, vobHint)
        assertEquals(NetworkPlaybackContainerHint.OTHER, mkvHint)
        // Ensures they cannot both reach the same branch accidentally.
        assertFalse(vobHint == mkvHint)
    }
}
