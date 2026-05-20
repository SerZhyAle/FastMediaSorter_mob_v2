package com.sza.fastmediasorter.data.link.nolegal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YtDlpFormatStringSanityTest {

    @Test
    fun audioOnlyCascadeContainsPinnedYoutubeAudioFormatIds() {
        val candidates = listOf(
            File("app_v2/src/noLegal/python/ytdlp_utils.py"),
            File("src/noLegal/python/ytdlp_utils.py"),
        )
        val pythonFile = candidates.firstOrNull { it.exists() }
        assertTrue("ytdlp_utils.py not found from test working dir", pythonFile != null)

        val contents = pythonFile!!.readText()
        val requiredIds = listOf("140", "251", "250", "249")

        requiredIds.forEach { formatId ->
            assertTrue(
                "Expected audio-only cascade to contain format id $formatId",
                contents.contains("/$formatId/") || contents.contains("/$formatId'") || contents.contains("/$formatId\""),
            )
        }
    }
}