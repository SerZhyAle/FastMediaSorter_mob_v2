package com.sza.fastmediasorter.data.remote.exchange

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * The framing of contract DEVICE-EXCHANGE section 7.1: a 4-byte unsigned big-endian length, then exactly
 * that many bytes of one UTF-8 JSON object. A frame therefore starts with `0x00`, which is how the server's
 * one port tells an envelope stream from a TLS handshake (`0x16`) and, inside TLS, from an HTTP request.
 */
object DeviceExchangeFrameCodec {

    /**
     * The limit of 7.1 as written. Contract question 14.6.1 (a larger frame after `welcome`, or a paged
     * directory) may change it; this constant is the only place that knows the number.
     */
    const val MAX_FRAME_BODY = 16_384
    private const val LENGTH_BYTES = 4

    fun frame(envelope: DeviceExchangeEnvelope): ByteArray {
        val body = envelope.encode().toByteArray(Charsets.UTF_8)
        if (body.size > MAX_FRAME_BODY) throw DeviceExchangeWireException("envelope above the frame limit")
        return ByteBuffer.allocate(LENGTH_BYTES + body.size).putInt(body.size).put(body).array()
    }

    fun write(output: OutputStream, envelope: DeviceExchangeEnvelope) {
        output.write(frame(envelope))
        output.flush()
    }

    /**
     * Reads the next envelope. Null at a clean end of stream before a frame starts; a zero or oversized
     * length, a truncated body or an invalid envelope throws [DeviceExchangeWireException].
     */
    fun read(input: InputStream): DeviceExchangeEnvelope? {
        val data = DataInputStream(input)
        val first = data.read()
        if (first < 0) return null
        val rest = ByteArray(LENGTH_BYTES - 1)
        val body = try {
            data.readFully(rest)
            val length = ByteBuffer.wrap(byteArrayOf(first.toByte()) + rest).int
            if (length !in 1..MAX_FRAME_BODY) throw DeviceExchangeWireException("frame length out of range")
            ByteArray(length).also(data::readFully)
        } catch (truncated: EOFException) {
            throw DeviceExchangeWireException("stream ended inside a frame", truncated)
        }
        return DeviceExchangeEnvelope.decode(body.toString(Charsets.UTF_8))
    }
}
