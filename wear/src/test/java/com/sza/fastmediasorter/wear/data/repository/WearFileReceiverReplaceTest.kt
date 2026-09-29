package com.sza.fastmediasorter.wear.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** S3797: a broken re-send of a name must never cost the copy already stored under it. */
class WearFileReceiverReplaceTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val previous = "previous good file".toByteArray()

    private fun existingTarget(): File =
        File(temporaryFolder.root, "clip.mp4").apply { writeBytes(previous) }

    @Test
    fun `a failed replacement keeps the previous file byte for byte`() {
        val target = existingTarget()

        val thrown = runCatching {
            writeThenReplace(target) { output ->
                output.write("half".toByteArray())
                throw IOException("phone walked out of range")
            }
        }.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertArrayEquals(previous, target.readBytes())
        assertEquals(listOf("clip.mp4"), temporaryFolder.root.list()!!.toList())
    }

    @Test
    fun `an over-budget copy keeps the previous file and leaves no part file`() {
        val target = existingTarget()

        val written = writeThenReplace(target) { output ->
            output.write("too much".toByteArray())
            null
        }

        assertNull(written)
        assertArrayEquals(previous, target.readBytes())
        assertEquals(listOf("clip.mp4"), temporaryFolder.root.list()!!.toList())
    }

    @Test
    fun `a whole copy replaces the previous file`() {
        val target = existingTarget()
        val fresh = "fresh copy".toByteArray()

        val written = writeThenReplace(target) { output ->
            output.write(fresh)
            fresh.size.toLong()
        }

        assertEquals(fresh.size.toLong(), written)
        assertArrayEquals(fresh, target.readBytes())
        assertEquals(listOf("clip.mp4"), temporaryFolder.root.list()!!.toList())
    }
}
