package com.sza.fastmediasorter.core.playback

import android.net.Uri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A re-arm used to drop a still-open sink without closing it, leaking its handle and its buffered
 * tail. Robolectric supplies a real android.net.Uri for the address match.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class ListenRecordingSinkHolderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val url = "http://watch.local/stream"
    private val payload = byteArrayOf(1, 2, 3, 4)

    @Test
    fun `re-arm flushes and closes the sink left open by the previous session`() {
        val holder = ListenRecordingSinkHolder()
        val first = folder.newFile("first.bin")
        holder.arm(url, first)
        val sink = holder.openSinkFor(Uri.parse(url))
        assertNotNull(sink)
        sink!!.write(payload)

        holder.arm(url, folder.newFile("second.bin"))
        sink.write(byteArrayOf(9))

        assertArrayEquals(payload, first.readBytes())
    }

    @Test
    fun `re-arm opens a fresh sink for the new session`() {
        val holder = ListenRecordingSinkHolder()
        holder.arm(url, folder.newFile("first.bin"))
        assertNotNull(holder.openSinkFor(Uri.parse(url)))

        holder.arm(url, folder.newFile("second.bin"))

        assertNotNull(holder.openSinkFor(Uri.parse(url)))
    }

    @Test
    fun `second open in one session is refused and another address is never served`() {
        val holder = ListenRecordingSinkHolder()
        holder.arm(url, folder.newFile("take.bin"))

        assertNull(holder.openSinkFor(Uri.parse("http://radio.example/live")))
        assertNotNull(holder.openSinkFor(Uri.parse(url)))
        assertNull(holder.openSinkFor(Uri.parse(url)))
    }

    @Test
    fun `closeAndTake returns the filled file and disarms`() {
        val holder = ListenRecordingSinkHolder()
        val file = folder.newFile("take.bin")
        holder.arm(url, file)
        holder.openSinkFor(Uri.parse(url))!!.write(payload)

        assertEquals(file, holder.closeAndTake())
        assertFalse(holder.isArmed())
        assertArrayEquals(payload, file.readBytes())
    }

    @Test
    fun `closeAndTake without an opened sink returns null`() {
        val holder = ListenRecordingSinkHolder()
        holder.arm(url, folder.newFile("take.bin"))
        assertTrue(holder.isArmed())

        assertNull(holder.closeAndTake())
    }
}
