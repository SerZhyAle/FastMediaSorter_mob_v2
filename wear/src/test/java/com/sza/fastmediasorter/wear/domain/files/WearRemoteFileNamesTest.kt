package com.sza.fastmediasorter.wear.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4230: the in-flight name never carries the person's file name and never repeats between transfers. */
class WearRemoteFileNamesTest {

    @Test
    fun `the partial name has the fixed shape`() {
        assertEquals(".fms-ab12cd34.part", WearRemoteFileNames.partialName("ab12cd34"))
    }

    @Test
    fun `a token is eight lowercase hex characters`() {
        val token = WearRemoteFileNames.newToken()

        assertTrue(token.matches(Regex("[0-9a-f]{8}")))
    }

    @Test
    fun `two tokens differ`() {
        assertNotEquals(WearRemoteFileNames.newToken(), WearRemoteFileNames.newToken())
    }

    @Test
    fun `a remote path never doubles the slash`() {
        assertEquals("/music/a.mp3", WearRemoteFileNames.joinRemote("/music/", "a.mp3"))
        assertEquals("/a.mp3", WearRemoteFileNames.joinRemote("/", "a.mp3"))
    }

    @Test
    fun `a share path is backslashed and relative to the share root`() {
        assertEquals("a.mp3", WearRemoteFileNames.joinShare("/", "a.mp3"))
        assertEquals("music\\old\\a.mp3", WearRemoteFileNames.joinShare("/music/old/", "a.mp3"))
        assertEquals("music\\a.mp3", WearRemoteFileNames.joinShare("music", "a.mp3"))
    }
}
