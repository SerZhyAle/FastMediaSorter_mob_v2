package com.sza.fastmediasorter.data.broadcast.exchange

import java.io.ByteArrayOutputStream

/**
 * A minimal MPEG-TS muxer for the video relay upload of contract DEVICE-EXCHANGE 7.6: H.264 on one PID and,
 * when [hasAudio], ADTS AAC-LC on another. [header] is the PAT and PMT, sent once as the relay's header chunk;
 * every video access unit becomes one PES whose first packet carries the PCR, and a keyframe also sets the
 * random access indicator and repeats the parameter sets, so a joinable chunk decodes on its own.
 *
 * Not thread-safe: one muxer belongs to one encoder session and is called from its callbacks.
 */
class MpegTsMuxer(private val hasAudio: Boolean) {

    private val continuity = IntArray(PID_SPACE)
    private var baseUs = NO_BASE

    /** The SPS and PPS in Annex-B form, prepended to every keyframe. */
    var parameterSets: ByteArray = ByteArray(0)

    val header: ByteArray = section(PID_PAT, patSection()) + section(PID_PMT, pmtSection())

    /** One H.264 access unit in Annex-B form, timestamped in microseconds of the encoder clock. */
    fun video(accessUnit: ByteArray, ptsUs: Long, keyFrame: Boolean): ByteArray {
        val pts = toPts(ptsUs)
        val payload = ByteArrayOutputStream(accessUnit.size + parameterSets.size + AUD.size)
        payload.write(AUD)
        if (keyFrame) payload.write(parameterSets)
        payload.write(accessUnit)
        val pes = pes(STREAM_ID_VIDEO, payload.toByteArray(), pts, bounded = false)
        return packetize(PID_VIDEO, pes, pcr = pts - PCR_LEAD_90K, randomAccess = keyFrame)
    }

    /** One ADTS frame, timestamped like [video]. */
    fun audio(adtsFrame: ByteArray, ptsUs: Long): ByteArray {
        check(hasAudio) { "the program has no audio stream" }
        val pes = pes(STREAM_ID_AUDIO, adtsFrame, toPts(ptsUs), bounded = true)
        return packetize(PID_AUDIO, pes, pcr = null, randomAccess = false)
    }

    // Both encoders stamp the same monotonic clock; the first sample of either opens the timeline.
    private fun toPts(ptsUs: Long): Long {
        if (baseUs == NO_BASE) baseUs = ptsUs
        val elapsedUs = (ptsUs - baseUs).coerceAtLeast(0L)
        return (elapsedUs * CLOCK_90K / MICROS_PER_SECOND + PTS_OFFSET_90K) and TIMESTAMP_MASK
    }

    private fun patSection(): ByteArray {
        val body = ByteArrayOutputStream()
        body.u16(TRANSPORT_STREAM_ID)
        body.syntaxTail()
        body.u16(PROGRAM_NUMBER)
        body.u16(RESERVED_3_BITS shl BYTE_SHIFT or PID_PMT)
        return tableSection(TABLE_PAT, body.toByteArray())
    }

    private fun pmtSection(): ByteArray {
        val body = ByteArrayOutputStream()
        body.u16(PROGRAM_NUMBER)
        body.syntaxTail()
        body.u16(RESERVED_3_BITS shl BYTE_SHIFT or PID_VIDEO)
        body.u16(RESERVED_4_BITS shl BYTE_SHIFT)
        body.elementaryStream(STREAM_TYPE_H264, PID_VIDEO)
        if (hasAudio) body.elementaryStream(STREAM_TYPE_ADTS, PID_AUDIO)
        return tableSection(TABLE_PMT, body.toByteArray())
    }

    // version 0 and current, section 0 of 0
    private fun ByteArrayOutputStream.syntaxTail() {
        write(VERSION_CURRENT)
        write(0)
        write(0)
    }

    // ES_info_length 0
    private fun ByteArrayOutputStream.elementaryStream(type: Int, pid: Int) {
        write(type)
        u16(RESERVED_3_BITS shl BYTE_SHIFT or pid)
        u16(RESERVED_4_BITS shl BYTE_SHIFT)
    }

    private fun ByteArrayOutputStream.u16(value: Int) {
        write((value ushr BYTE_SHIFT) and BYTE_MASK)
        write(value and BYTE_MASK)
    }

    private fun tableSection(tableId: Int, body: ByteArray): ByteArray {
        val section = ByteArrayOutputStream()
        section.write(tableId)
        section.u16(SECTION_SYNTAX shl BYTE_SHIFT or (body.size + CRC_BYTES))
        section.write(body)
        val crc = MpegCrc32.of(section.toByteArray())
        section.u16(crc ushr BYTE_2_SHIFT)
        section.u16(crc)
        return section.toByteArray()
    }

    // A PSI section fits one packet here: a pointer field, the section, stuffing bytes.
    private fun section(pid: Int, table: ByteArray): ByteArray {
        val packet = ByteArray(PACKET_BYTES) { STUFFING }
        writeHeader(packet, pid, payloadStart = true, adaptation = false)
        packet[HEADER_BYTES] = 0x00
        table.copyInto(packet, HEADER_BYTES + 1)
        return packet
    }

    private fun pes(streamId: Int, payload: ByteArray, pts: Long, bounded: Boolean): ByteArray {
        val headerData = ptsBytes(pts)
        // Video PES packets above 64 KiB are legal only unbounded, which TS allows for video.
        val length = if (bounded) PES_FLAG_BYTES + headerData.size + payload.size else 0
        val out = ByteArrayOutputStream(PES_HEAD_BYTES + headerData.size + payload.size)
        out.write(0x00)
        out.write(0x00)
        out.write(0x01)
        out.write(streamId)
        out.u16(length)
        out.write(PES_MARKER)
        out.write(PES_PTS_ONLY)
        out.write(headerData.size)
        out.write(headerData)
        out.write(payload)
        return out.toByteArray()
    }

    private fun packetize(pid: Int, pes: ByteArray, pcr: Long?, randomAccess: Boolean): ByteArray {
        val out = ByteArrayOutputStream((pes.size / PAYLOAD_BYTES + 1) * PACKET_BYTES)
        var at = 0
        var first = true
        while (at < pes.size) {
            val fields = if (first) adaptationFields(pcr, randomAccess) else null
            val room = PAYLOAD_BYTES - (fields?.let { it.size + 1 } ?: 0)
            val take = minOf(pes.size - at, room)
            val adaptation = adaptation(fields, stuffing = room - take)
            val packet = ByteArray(PACKET_BYTES)
            writeHeader(packet, pid, payloadStart = first, adaptation = adaptation != null)
            adaptation?.copyInto(packet, HEADER_BYTES)
            pes.copyInto(packet, HEADER_BYTES + (adaptation?.size ?: 0), at, at + take)
            out.write(packet)
            at += take
            first = false
        }
        return out.toByteArray()
    }

    private fun adaptationFields(pcr: Long?, randomAccess: Boolean): ByteArray? {
        if (pcr == null && !randomAccess) return null
        var flags = 0
        if (randomAccess) flags = flags or AF_RANDOM_ACCESS
        if (pcr != null) flags = flags or AF_PCR
        return byteArrayOf(flags.toByte()) + (pcr?.let(::pcrBytes) ?: ByteArray(0))
    }

    /** The whole adaptation field with its length byte, or null when the packet carries none. */
    private fun adaptation(fields: ByteArray?, stuffing: Int): ByteArray? = when {
        fields != null -> byteArrayOf((fields.size + stuffing).toByte()) + fields + ByteArray(stuffing) { STUFFING }
        stuffing == 0 -> null
        stuffing == 1 -> byteArrayOf(0x00)
        else -> byteArrayOf((stuffing - 1).toByte(), 0x00) + ByteArray(stuffing - EMPTY_FIELD_BYTES) { STUFFING }
    }

    private fun writeHeader(packet: ByteArray, pid: Int, payloadStart: Boolean, adaptation: Boolean) {
        packet[0] = SYNC_BYTE
        packet[1] = ((if (payloadStart) PUSI else 0) or ((pid ushr BYTE_SHIFT) and PID_HIGH_MASK)).toByte()
        packet[2] = pid.toByte()
        val control = if (adaptation) CONTROL_ADAPTATION_AND_PAYLOAD else CONTROL_PAYLOAD_ONLY
        packet[HEADER_BYTES - 1] = (control or continuity[pid]).toByte()
        continuity[pid] = (continuity[pid] + 1) and CONTINUITY_MASK
    }

    private fun ptsBytes(pts: Long): ByteArray {
        val bytes = ByteArray(PTS_BYTES)
        bytes[0] = (PTS_PREFIX or ((pts ushr PTS_SHIFT_1).toInt() and PTS_TOP_MASK) or 1).toByte()
        bytes[1] = (pts ushr PTS_SHIFT_2).toByte()
        bytes[2] = (((pts ushr PTS_SHIFT_3).toInt() and MARKER_CLEAR_MASK) or 1).toByte()
        bytes[PTS_BYTE_3] = (pts ushr PTS_SHIFT_4).toByte()
        bytes[PTS_BYTE_4] = (((pts shl 1).toInt() and MARKER_CLEAR_MASK) or 1).toByte()
        return bytes
    }

    // 33-bit base, 6 reserved bits, 9-bit extension left at zero.
    private fun pcrBytes(pcr: Long): ByteArray {
        val base = pcr.coerceAtLeast(0L) and TIMESTAMP_MASK
        val bytes = ByteArray(PCR_BYTES)
        bytes[0] = (base ushr PCR_SHIFT_1).toByte()
        bytes[1] = (base ushr PCR_SHIFT_2).toByte()
        bytes[2] = (base ushr PCR_SHIFT_3).toByte()
        bytes[PCR_BYTE_3] = (base ushr 1).toByte()
        bytes[PCR_BYTE_4] = (((base and 1L).toInt() shl PCR_LOW_BIT_SHIFT) or PCR_RESERVED_BITS).toByte()
        return bytes
    }

    companion object {
        const val PACKET_BYTES = 188
        const val PID_PAT = 0x0000
        const val PID_PMT = 0x1000
        const val PID_VIDEO = 0x0100
        const val PID_AUDIO = 0x0101
        const val STREAM_TYPE_H264 = 0x1B
        const val STREAM_TYPE_ADTS = 0x0F
        const val CLOCK_90K = 90_000L
        const val PTS_OFFSET_90K = 63_000L
        const val SYNC_BYTE: Byte = 0x47
        const val AF_PCR = 0x10
        const val AF_RANDOM_ACCESS = 0x40

        private const val HEADER_BYTES = 4
        private const val PAYLOAD_BYTES = PACKET_BYTES - HEADER_BYTES
        private const val PID_SPACE = 0x2000
        private const val PID_HIGH_MASK = 0x1F
        private const val PUSI = 0x40
        private const val CONTROL_PAYLOAD_ONLY = 0x10
        private const val CONTROL_ADAPTATION_AND_PAYLOAD = 0x30
        private const val CONTINUITY_MASK = 0x0F
        private const val STUFFING: Byte = 0xFF.toByte()
        private const val EMPTY_FIELD_BYTES = 2
        private const val TABLE_PAT = 0x00
        private const val TABLE_PMT = 0x02
        private const val SECTION_SYNTAX = 0xB0
        private const val VERSION_CURRENT = 0xC1
        private const val TRANSPORT_STREAM_ID = 0x0001
        private const val RESERVED_3_BITS = 0xE0
        private const val RESERVED_4_BITS = 0xF0
        private const val PROGRAM_NUMBER = 0x0001
        private const val CRC_BYTES = 4
        private const val STREAM_ID_VIDEO = 0xE0
        private const val STREAM_ID_AUDIO = 0xC0
        private const val PES_HEAD_BYTES = 9
        private const val PES_FLAG_BYTES = 3
        private const val PES_MARKER = 0x80
        private const val PES_PTS_ONLY = 0x80
        private const val PTS_BYTES = 5
        private const val PTS_BYTE_3 = 3
        private const val PTS_BYTE_4 = 4
        private const val PTS_PREFIX = 0x20
        private const val PTS_TOP_MASK = 0x0E
        private const val PTS_SHIFT_1 = 29
        private const val PTS_SHIFT_2 = 22
        private const val PTS_SHIFT_3 = 14
        private const val PTS_SHIFT_4 = 7
        private const val MARKER_CLEAR_MASK = 0xFE
        private const val PCR_BYTES = 6
        private const val PCR_BYTE_3 = 3
        private const val PCR_BYTE_4 = 4
        private const val PCR_SHIFT_1 = 25
        private const val PCR_SHIFT_2 = 17
        private const val PCR_SHIFT_3 = 9
        private const val PCR_LOW_BIT_SHIFT = 7
        private const val PCR_RESERVED_BITS = 0x7E
        private const val PCR_LEAD_90K = 9_000L
        private const val TIMESTAMP_MASK = 0x1FFFFFFFFL
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val BYTE_MASK = 0xFF
        private const val BYTE_SHIFT = 8
        private const val BYTE_2_SHIFT = 16
        private const val NO_BASE = Long.MIN_VALUE

        // An access unit delimiter opens every H.264 access unit in TS (ITU-T H.222.0 2.14).
        private val AUD = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x09, 0xF0.toByte())
    }
}

/** The CRC-32 of MPEG-2 PSI sections: polynomial 0x04C11DB7, initial value all ones, no reflection, no final xor. */
object MpegCrc32 {

    private const val POLYNOMIAL = 0x04C11DB7
    private const val TOP_BIT = 0x80000000.toInt()
    private const val BYTE_MASK = 0xFF
    private const val BYTE_3_SHIFT = 24
    private const val BITS_PER_BYTE = 8

    fun of(bytes: ByteArray): Int {
        var crc = -1
        for (byte in bytes) {
            crc = crc xor ((byte.toInt() and BYTE_MASK) shl BYTE_3_SHIFT)
            repeat(BITS_PER_BYTE) {
                crc = if (crc and TOP_BIT != 0) (crc shl 1) xor POLYNOMIAL else crc shl 1
            }
        }
        return crc
    }
}

/** The 7-byte ADTS header a raw AAC-LC access unit from MediaCodec needs before it is an ADTS frame. */
object AdtsHeader {

    private const val HEADER_BYTES = 7
    private const val AAC_LC_PROFILE = 1
    private const val MAX_FRAME_BYTES = 0x1FFF
    private const val SYNC_HIGH = 0xFF
    private const val SYNC_LOW_MPEG4_NO_CRC = 0xF1
    private const val PROFILE_SHIFT = 6
    private const val RATE_SHIFT = 2
    private const val CHANNEL_HIGH_SHIFT = 2
    private const val CHANNEL_LOW_MASK = 0x03
    private const val CHANNEL_LOW_SHIFT = 6
    private const val LENGTH_HIGH_SHIFT = 11
    private const val LENGTH_HIGH_MASK = 0x03
    private const val LENGTH_MID_SHIFT = 3
    private const val LENGTH_LOW_MASK = 0x07
    private const val LENGTH_LOW_SHIFT = 5
    private const val BUFFER_FULLNESS_LOW = 0x1F
    private const val BUFFER_FULLNESS_TAIL = 0xFC
    private const val BYTE_MASK = 0xFF
    private const val INDEX_3 = 3
    private const val INDEX_4 = 4
    private const val INDEX_5 = 5
    private const val INDEX_6 = 6

    // ISO/IEC 14496-3 sampling_frequency_index order.
    @Suppress("MagicNumber")
    private val SAMPLE_RATES = intArrayOf(
        96_000,
        88_200,
        64_000,
        48_000,
        44_100,
        32_000,
        24_000,
        22_050,
        16_000,
        12_000,
        11_025,
        8_000,
        7_350,
    )

    /** [raw] wrapped in an ADTS frame, or null for a sample rate ADTS cannot name or a frame too long for it. */
    fun wrap(raw: ByteArray, sampleRateHz: Int, channelCount: Int): ByteArray? {
        val rateIndex = SAMPLE_RATES.indexOf(sampleRateHz)
        val length = raw.size + HEADER_BYTES
        if (rateIndex < 0 || channelCount !in 1..2 || length > MAX_FRAME_BYTES) return null
        val profileRateChannel =
            (AAC_LC_PROFILE shl PROFILE_SHIFT) or (rateIndex shl RATE_SHIFT) or (channelCount ushr CHANNEL_HIGH_SHIFT)
        val channelLength = ((channelCount and CHANNEL_LOW_MASK) shl CHANNEL_LOW_SHIFT) or
            ((length ushr LENGTH_HIGH_SHIFT) and LENGTH_HIGH_MASK)
        val frame = ByteArray(length)
        frame[0] = SYNC_HIGH.toByte()
        frame[1] = SYNC_LOW_MPEG4_NO_CRC.toByte()
        frame[2] = profileRateChannel.toByte()
        frame[INDEX_3] = channelLength.toByte()
        frame[INDEX_4] = ((length ushr LENGTH_MID_SHIFT) and BYTE_MASK).toByte()
        frame[INDEX_5] = (((length and LENGTH_LOW_MASK) shl LENGTH_LOW_SHIFT) or BUFFER_FULLNESS_LOW).toByte()
        frame[INDEX_6] = BUFFER_FULLNESS_TAIL.toByte()
        raw.copyInto(frame, HEADER_BYTES)
        return frame
    }
}
