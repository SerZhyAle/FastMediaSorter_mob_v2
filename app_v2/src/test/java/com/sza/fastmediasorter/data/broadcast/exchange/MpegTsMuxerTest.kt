package com.sza.fastmediasorter.data.broadcast.exchange

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MpegTsMuxerTest {

    @Test
    fun `the header is a PAT and a PMT whose CRCs check`() {
        val header = MpegTsMuxer(hasAudio = true).header

        assertEquals(2 * MpegTsMuxer.PACKET_BYTES, header.size)
        val pat = header.copyOfRange(0, MpegTsMuxer.PACKET_BYTES)
        val pmt = header.copyOfRange(MpegTsMuxer.PACKET_BYTES, header.size)
        assertEquals(MpegTsMuxer.PID_PAT, pid(pat))
        assertEquals(MpegTsMuxer.PID_PMT, pid(pmt))
        assertEquals(0, MpegCrc32.of(section(pat)))
        assertEquals(0, MpegCrc32.of(section(pmt)))
        val patSection = section(pat)
        assertEquals(MpegTsMuxer.PID_PMT, (patSection[10].u() and 0x1F shl 8) or patSection[11].u())
    }

    @Test
    fun `the PMT lists audio only when the program has it`() {
        val withAudio = section(MpegTsMuxer(hasAudio = true).header.copyOfRange(188, 376))
        val videoOnly = section(MpegTsMuxer(hasAudio = false).header.copyOfRange(188, 376))

        assertEquals(listOf(MpegTsMuxer.STREAM_TYPE_H264, MpegTsMuxer.STREAM_TYPE_ADTS), streamTypes(withAudio))
        assertEquals(listOf(MpegTsMuxer.STREAM_TYPE_H264), streamTypes(videoOnly))
    }

    @Test
    fun `a keyframe opens with PCR and random access and carries the parameter sets`() {
        val muxer = MpegTsMuxer(hasAudio = true)
        val sps = byteArrayOf(0, 0, 0, 1, 0x67, 1, 2, 3)
        val pps = byteArrayOf(0, 0, 0, 1, 0x68, 4, 5)
        muxer.parameterSets = sps + pps
        val frame = ByteArray(1_000) { (it % 251).toByte() }

        val packets = muxer.video(frame, ptsUs = 5_000_000L, keyFrame = true)

        assertEquals(0, packets.size % MpegTsMuxer.PACKET_BYTES)
        val first = packets.copyOfRange(0, MpegTsMuxer.PACKET_BYTES)
        assertEquals(MpegTsMuxer.SYNC_BYTE, first[0])
        assertTrue(first[1].u() and 0x40 != 0)
        assertEquals(MpegTsMuxer.PID_VIDEO, pid(first))
        val flags = first[5].u()
        assertTrue(flags and MpegTsMuxer.AF_PCR != 0)
        assertTrue(flags and MpegTsMuxer.AF_RANDOM_ACCESS != 0)
        val pes = payload(packets)
        assertArrayEquals(byteArrayOf(0, 0, 1, 0xE0.toByte()), pes.copyOfRange(0, 4))
        assertEquals(MpegTsMuxer.PTS_OFFSET_90K, pts(pes))
        val aud = byteArrayOf(0, 0, 0, 1, 0x09, 0xF0.toByte())
        assertArrayEquals(aud + sps + pps + frame, pes.copyOfRange(14, pes.size))
    }

    @Test
    fun `a delta frame carries no parameter sets and no random access`() {
        val muxer = MpegTsMuxer(hasAudio = false)
        muxer.parameterSets = byteArrayOf(0, 0, 0, 1, 0x67)
        val frame = byteArrayOf(0, 0, 0, 1, 0x41, 9)

        val packets = muxer.video(frame, ptsUs = 0L, keyFrame = false)

        assertEquals(MpegTsMuxer.PACKET_BYTES, packets.size)
        assertEquals(0, packets[5].u() and MpegTsMuxer.AF_RANDOM_ACCESS)
        val pes = payload(packets)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x09, 0xF0.toByte()) + frame, pes.copyOfRange(14, pes.size))
    }

    @Test
    fun `audio is a bounded PES on its own PID, timed from the first sample of either stream`() {
        val muxer = MpegTsMuxer(hasAudio = true)
        muxer.video(byteArrayOf(0, 0, 0, 1, 0x65), ptsUs = 10_000_000L, keyFrame = true)
        val frame = checkNotNull(AdtsHeader.wrap(ByteArray(300) { 7 }, 44_100, 2))

        val packets = muxer.audio(frame, ptsUs = 11_000_000L)

        assertEquals(MpegTsMuxer.PID_AUDIO, pid(packets))
        val pes = payload(packets)
        assertEquals(0xC0, pes[3].u())
        assertEquals(3 + 5 + frame.size, (pes[4].u() shl 8) or pes[5].u())
        assertEquals(MpegTsMuxer.PTS_OFFSET_90K + MpegTsMuxer.CLOCK_90K, pts(pes))
        assertArrayEquals(frame, pes.copyOfRange(14, pes.size))
    }

    @Test
    fun `continuity counters advance per PID`() {
        val muxer = MpegTsMuxer(hasAudio = false)
        val packets = muxer.video(ByteArray(600), ptsUs = 0L, keyFrame = false) +
            muxer.video(ByteArray(10), ptsUs = 33_000L, keyFrame = false)

        val counters = (0 until packets.size / 188).map { packets[it * 188 + 3].u() and 0x0F }

        assertEquals((0 until counters.size).map { it and 0x0F }, counters)
    }

    @Test
    fun `an ADTS frame wraps raw AAC whole and the splitter reads it back`() {
        val raw = ByteArray(200) { it.toByte() }

        val wrapped = AdtsHeader.wrap(raw, 48_000, 1)

        assertNotNull(wrapped)
        val frame = checkNotNull(wrapped)
        val cut = AdtsFrameSplitter().feed(frame, frame.size)
        assertEquals(1, cut.size)
        assertArrayEquals(frame, cut.single())
        assertEquals(null, AdtsHeader.wrap(raw, 44_000, 2))
    }

    private fun Byte.u(): Int = toInt() and 0xFF

    private fun pid(packet: ByteArray): Int = (packet[1].u() and 0x1F shl 8) or packet[2].u()

    // The section of a PSI packet: after the pointer field, up to the end of its declared length.
    private fun section(packet: ByteArray): ByteArray {
        val start = 5 + packet[4].u()
        val length = (packet[start + 1].u() and 0x0F shl 8) or packet[start + 2].u()
        return packet.copyOfRange(start, start + 3 + length)
    }

    private fun streamTypes(pmt: ByteArray): List<Int> {
        val end = pmt.size - 4
        var at = 12
        val types = mutableListOf<Int>()
        while (at < end) {
            types += pmt[at].u()
            at += 5
        }
        return types
    }

    private fun payload(packets: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (at in packets.indices step 188) {
            val hasAdaptation = packets[at + 3].u() and 0x20 != 0
            val start = if (hasAdaptation) at + 5 + packets[at + 4].u() else at + 4
            out.write(packets, start, at + 188 - start)
        }
        return out.toByteArray()
    }

    private fun pts(pes: ByteArray): Long =
        ((pes[9].u() shr 1 and 0x07).toLong() shl 30) or
            (pes[10].u().toLong() shl 22) or
            ((pes[11].u() shr 1).toLong() shl 15) or
            (pes[12].u().toLong() shl 7) or
            (pes[13].u() shr 1).toLong()
}
