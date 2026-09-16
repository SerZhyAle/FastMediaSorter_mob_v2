package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastShareLinkFactoryTest {

    @Test
    fun `shared link carries the descriptor and browser fallback`() {
        val link = BroadcastShareLinkFactory.create("FMSBCAST1:payload+/=")

        assertTrue(link.startsWith("intent://import?payload=FMSBCAST1%3Apayload%2B%2F%3D#Intent"))
        assertTrue(link.contains("scheme=fmsbcast"))
        assertTrue(link.contains("S.browser_fallback_url="))
    }

    @Test
    fun `shared link names the sending build's own package`() {
        val link = BroadcastShareLinkFactory.create("FMSBCAST1:payload")

        assertTrue(link.contains("package=${BuildConfig.APPLICATION_ID};"))
    }
}
