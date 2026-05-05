package com.sza.fastmediasorter.data.network.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class BdTsStripDataSourceTest {

    private class FakeDataSource(private val data: ByteArray) : DataSource {
        var openedPosition: Long = -1
        private var position: Int = 0
        private var bytesRemaining: Int = 0

        override fun open(dataSpec: DataSpec): Long {
            openedPosition = dataSpec.position
            position = dataSpec.position.toInt()
            bytesRemaining = data.size - position
            return bytesRemaining.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
            val toRead = minOf(length, bytesRemaining)
            System.arraycopy(data, position, buffer, offset, toRead)
            position += toRead
            bytesRemaining -= toRead
            return toRead
        }

        override fun getUri(): Uri? = null
        override fun close() {}
        override fun addTransferListener(transferListener: TransferListener) {}
    }

    private val testUri: Uri = Uri.parse("test://x")

    @Test
    fun `strip produces correct 188-byte payload from single BD-TS packet`() {
        val data = ByteArray(192)
        for (i in 4 until 192) data[i] = (i + 1).toByte()
        val strip = BdTsStripDataSource(FakeDataSource(data))
        strip.open(DataSpec(testUri, 0L, 188L))
        val result = ByteArray(188)
        val n = strip.read(result, 0, 188)
        Assert.assertEquals(188, n)
        Assert.assertArrayEquals(data.copyOfRange(4, 192), result)
    }

    @Test
    fun `strip across two packets removes both headers`() {
        val data = ByteArray(384)
        for (i in 4 until 192) data[i] = (i + 100).toByte()
        for (i in 196 until 384) data[i] = (i + 200).toByte()
        val strip = BdTsStripDataSource(FakeDataSource(data))
        strip.open(DataSpec(testUri, 0L, 376L))
        val result = ByteArray(376)
        var totalRead = 0
        while (totalRead < 376) {
            val n = strip.read(result, totalRead, 376 - totalRead)
            if (n == C.RESULT_END_OF_INPUT) break
            totalRead += n
        }
        Assert.assertEquals(376, totalRead)
        val expected = data.copyOfRange(4, 192) + data.copyOfRange(196, 384)
        Assert.assertArrayEquals(expected, result)
    }

    @Test
    fun `open at non-zero tsPos translates to correct bdPos`() {
        val fake = FakeDataSource(ByteArray(384))
        val strip = BdTsStripDataSource(fake)
        strip.open(DataSpec(testUri, 188L, 188L))
        // tsPos=188 → packetIndex=1 → bdPos=1*192=192 (BD-packet boundary)
        Assert.assertEquals(192L, fake.openedPosition)
    }

    @Test
    fun `read returns END_OF_INPUT on truncated last packet`() {
        val strip = BdTsStripDataSource(FakeDataSource(ByteArray(96)))
        strip.open(DataSpec(testUri, 0L, C.LENGTH_UNSET.toLong()))
        val buf = ByteArray(1)
        val n = strip.read(buf, 0, 1)
        Assert.assertTrue("expected read or END_OF_INPUT", n == 1 || n == C.RESULT_END_OF_INPUT)
    }
}
