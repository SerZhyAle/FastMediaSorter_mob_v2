package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * S3961: a model download cut short must never leave a non-empty `<lang>.traineddata` behind, because the
 * fast path in [TesseractManager] accepts any non-empty file as a finished model.
 */
class TesseractModelFileTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FailingStream(private val bytesBeforeFailure: Int) : InputStream() {
        private var served = 0
        override fun read(): Int {
            if (served >= bytesBeforeFailure) throw IOException("connection reset")
            served++
            return 'x'.code
        }
    }

    @Test
    fun `a completed download lands under the final name and leaves no part file`() {
        val target = File(tmp.root, "rus.traineddata")
        val payload = "model-bytes".toByteArray()

        writeModelFileAtomically(ByteArrayInputStream(payload), target)

        assertArrayEquals(payload, target.readBytes())
        assertFalse(File(tmp.root, "rus.traineddata.part").exists())
    }

    @Test
    fun `a download that fails midway leaves neither the model nor the part file`() {
        val target = File(tmp.root, "eng.traineddata")

        try {
            writeModelFileAtomically(FailingStream(bytesBeforeFailure = 16), target)
            fail("the stream failure must propagate")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("reset"))
        }

        assertFalse(target.exists())
        assertFalse(File(tmp.root, "eng.traineddata.part").exists())
    }

    @Test
    fun `an empty leftover model is replaced by the completed download`() {
        val target = File(tmp.root, "ukr.traineddata").apply { createNewFile() }
        val payload = "fresh".toByteArray()

        writeModelFileAtomically(ByteArrayInputStream(payload), target)

        assertArrayEquals(payload, target.readBytes())
    }
}
