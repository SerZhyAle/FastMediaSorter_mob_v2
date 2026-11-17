package com.sza.fastmediasorter.ui.streams.helpers

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHeadlessPlayManagerTest {
    @Test
    fun exchangeUrlWithoutDescriptorIsNotEligibleForHeadlessPlay() {
        val source = StreamSourceEntity(
            id = "row",
            url = "https://exchange.example.org/v2/b/id/stream",
            title = "Broadcast",
            mediaKind = "AUDIO",
            sourceOrigin = "MANUAL",
            sortIndex = 0,
            addedAt = 0,
        )
        assertFalse(StreamHeadlessPlayManager.qualifiesForHeadlessPlay(source, true, true))
        assertTrue(
            StreamHeadlessPlayManager.qualifiesForHeadlessPlay(
                source.copy(url = "http://192.0.2.1/live"),
                true,
                true,
            )
        )
    }
}
