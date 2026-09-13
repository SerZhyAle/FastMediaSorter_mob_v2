package com.sza.fastmediasorter.ui.broadcast.helpers

import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastShareLinkFactoryTest {

    @Test
    fun `shared link carries the descriptor and browser fallback`() {
        val link = BroadcastShareLinkFactory.create("FMSBCAST1:payload+/=")

        assertTrue(link.startsWith("intent://import?payload=FMSBCAST1%3Apayload%2B%2F%3D#Intent"))
        assertTrue(link.contains("scheme=fmsbcast"))
        assertTrue(link.contains("package=com.sza.fastmediasorter"))
        assertTrue(link.contains("S.browser_fallback_url="))
    }
}
