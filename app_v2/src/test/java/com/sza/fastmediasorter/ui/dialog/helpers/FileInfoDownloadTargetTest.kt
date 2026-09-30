package com.sza.fastmediasorter.ui.dialog.helpers

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** S3902: "download and open" must never pick a Downloads name the user already holds. */
class FileInfoDownloadTargetTest {

    private val dir = File("downloads")

    private fun taken(vararg names: String): (File) -> Boolean = { it.name in names }

    @Test
    fun `a free name is used as is`() {
        assertEquals("clip.mp4", resolveFreeDownloadTarget(dir, "clip.mp4", taken()).name)
    }

    @Test
    fun `a taken name gets the first free numbered suffix before the extension`() {
        val target = resolveFreeDownloadTarget(dir, "clip.mp4", taken("clip.mp4", "clip (1).mp4"))

        assertEquals("clip (2).mp4", target.name)
        assertEquals(dir, target.parentFile)
    }

    @Test
    fun `a name without an extension or with a leading dot keeps its whole base`() {
        assertEquals("README (1)", resolveFreeDownloadTarget(dir, "README", taken("README")).name)
        assertEquals(".nomedia (1)", resolveFreeDownloadTarget(dir, ".nomedia", taken(".nomedia")).name)
    }

    @Test
    fun `only the last dot separates the extension`() {
        val target = resolveFreeDownloadTarget(dir, "archive.tar.gz", taken("archive.tar.gz"))

        assertEquals("archive.tar (1).gz", target.name)
    }
}
