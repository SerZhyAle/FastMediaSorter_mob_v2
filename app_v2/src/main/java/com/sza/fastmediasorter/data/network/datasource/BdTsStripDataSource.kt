package com.sza.fastmediasorter.data.network.datasource

import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import timber.log.Timber

// Strips the 4-byte Blu-ray timestamp prefix from each 192-byte BD-TS packet,
// exposing a standard 188-byte MPEG-TS stream to ExoPlayer's TsExtractor.
internal class BdTsStripDataSource(private val upstream: DataSource) : DataSource {

    companion object {
        private const val BD_PACKET_SIZE = 192
        private const val TS_PACKET_SIZE = 188
        private const val BD_HEADER_SIZE = BD_PACKET_SIZE - TS_PACKET_SIZE // 4
    }

    private val packetBuf = ByteArray(BD_PACKET_SIZE)
    // Current read cursor and end within packetBuf (TS payload portion only, bytes [4..191])
    private var tsPayloadOffset = BD_HEADER_SIZE
    private var tsPayloadEnd = BD_HEADER_SIZE
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        // Translate logical TS position → BD-TS byte position in the upstream.
        // Open at the BD-packet boundary so read() correctly strips bytes [0..3] as the BD header.
        val tsPos = dataSpec.position
        val packetIndex = tsPos / TS_PACKET_SIZE
        val byteWithinPacket = (tsPos % TS_PACKET_SIZE).toInt()
        val bdPos = packetIndex * BD_PACKET_SIZE

        val translatedSpec = dataSpec.buildUpon().setPosition(bdPos).build()
        val upstreamLength = upstream.open(translatedSpec)
        opened = true

        // State: position inside the current packet (for mid-packet seeks)
        tsPayloadOffset = BD_HEADER_SIZE + byteWithinPacket
        tsPayloadEnd = BD_HEADER_SIZE // no full packet loaded yet; first read() will load it

        val tsLength = if (upstreamLength == C.LENGTH_UNSET.toLong()) {
            C.LENGTH_UNSET.toLong()
        } else {
            val fullPackets = upstreamLength / BD_PACKET_SIZE
            val tail = (upstreamLength % BD_PACKET_SIZE).coerceAtMost(TS_PACKET_SIZE.toLong())
            fullPackets * TS_PACKET_SIZE + tail
        }
        Timber.d("BdTsStripDataSource: open tsPos=%d bdPos=%d upLen=%d tsLen=%d", tsPos, bdPos, upstreamLength, tsLength)
        return tsLength
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var bytesRead = 0
        while (bytesRead < length) {
            if (tsPayloadOffset >= tsPayloadEnd) {
                // Load next BD-TS packet: read exactly BD_PACKET_SIZE bytes from upstream
                var totalRead = 0
                while (totalRead < BD_PACKET_SIZE) {
                    val n = upstream.read(packetBuf, totalRead, BD_PACKET_SIZE - totalRead)
                    if (n == C.RESULT_END_OF_INPUT) {
                        return if (bytesRead > 0) bytesRead else C.RESULT_END_OF_INPUT
                    }
                    totalRead += n
                }
                // Payload is bytes [BD_HEADER_SIZE .. BD_PACKET_SIZE-1]
                tsPayloadOffset = BD_HEADER_SIZE
                tsPayloadEnd = BD_PACKET_SIZE
            }
            val toCopy = minOf(tsPayloadEnd - tsPayloadOffset, length - bytesRead)
            System.arraycopy(packetBuf, tsPayloadOffset, buffer, offset + bytesRead, toCopy)
            tsPayloadOffset += toCopy
            bytesRead += toCopy
        }
        return bytesRead
    }

    override fun getUri() = upstream.uri

    override fun close() {
        if (opened) {
            upstream.close()
            opened = false
            tsPayloadOffset = BD_HEADER_SIZE
            tsPayloadEnd = BD_HEADER_SIZE
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }
}
