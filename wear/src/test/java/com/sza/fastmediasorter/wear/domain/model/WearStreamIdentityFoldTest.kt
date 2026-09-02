package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S2149: the fold is the only thing that lets a pin filed on the phone match a channel in the watch
 * catalogue, and the storage key is the only thing that keeps marks the user already placed resolvable.
 * Both are pinned here because they must move in opposite directions - one may widen, the other may not.
 */
class WearStreamIdentityFoldTest {

    @Test
    fun `http and https spellings of one channel fold equal`() {
        assertEquals(
            foldWearStreamIdentity("https://host.tv/a"),
            foldWearStreamIdentity("http://Host.TV/a/")
        )
    }

    @Test
    fun `folded web address carries the single scheme token`() {
        assertEquals("web://host.tv/a", foldWearStreamIdentity("https://host.tv/a/"))
    }

    @Test
    fun `rtsp never folds to the web token`() {
        assertEquals("rtsp://host.tv/a", foldWearStreamIdentity("RTSP://Host.TV/a/"))
        assertNotEquals(foldWearStreamIdentity("rtsp://host.tv/a"), foldWearStreamIdentity("https://host.tv/a"))
    }

    @Test
    fun `default ports are still dropped after folding`() {
        assertEquals("web://host.tv/a", foldWearStreamIdentity("http://host.tv:80/a"))
        assertEquals("web://host.tv/a", foldWearStreamIdentity("https://host.tv:443/a"))
        assertEquals("rtsp://host.tv/a", foldWearStreamIdentity("rtsp://host.tv:554/a"))
    }

    @Test
    fun `non default port survives the fold`() {
        assertEquals("web://host.tv:8080/a", foldWearStreamIdentity("http://host.tv:8080/a"))
    }

    @Test
    fun `malformed address survives the fold unchanged`() {
        assertEquals("not a url", foldWearStreamIdentity("not a url"))
    }

    @Test
    fun `stored key keeps its scheme and is not folded`() {
        // S2149 freezes this value: the favourites store is keyed by it, so a change here silently
        // orphans every mark the user has already placed - the failure S2039 recorded.
        assertEquals("https://host.tv/a", normalizeWearStreamUrl("https://host.tv/a/"))
        assertEquals(
            "stream:https://host.tv/a",
            favoriteIdentityKey(SOURCE_ID_STREAM, "https://host.tv/a/")
        )
    }
}
