package com.sza.fastmediasorter.data.remote.sftp.anywhere

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * The TCP framing of contract ANYWHERE-ACCESS section 6.1: a 4-byte unsigned big-endian length, then
 * exactly that many bytes of UTF-8 JSON. A frame therefore starts with `0x00`, which is how a server
 * serving TLS (`0x16`) and plain frames on one port tells them apart.
 */
object ExchangeFrameCodec {

    const val MAX_FRAME_BODY = 16_384
    private const val LENGTH_BYTES = 4

    fun frame(envelope: ExchangeEnvelope): ByteArray {
        val body = envelope.encode().toByteArray(Charsets.UTF_8)
        if (body.size > MAX_FRAME_BODY) throw ExchangeWireException("envelope above the frame limit")
        return ByteBuffer.allocate(LENGTH_BYTES + body.size).putInt(body.size).put(body).array()
    }

    fun write(output: OutputStream, envelope: ExchangeEnvelope) {
        output.write(frame(envelope))
        output.flush()
    }

    /**
     * Reads the next envelope. Null at a clean end of stream before a frame starts; a zero or oversized
     * length, a truncated body or an invalid envelope throws [ExchangeWireException].
     */
    fun read(input: InputStream): ExchangeEnvelope? {
        val data = DataInputStream(input)
        val first = data.read()
        if (first < 0) return null
        val rest = ByteArray(LENGTH_BYTES - 1)
        val body = try {
            data.readFully(rest)
            val length = ByteBuffer.wrap(byteArrayOf(first.toByte()) + rest).int
            if (length !in 1..MAX_FRAME_BODY) throw ExchangeWireException("frame length out of range")
            ByteArray(length).also(data::readFully)
        } catch (truncated: EOFException) {
            throw ExchangeWireException("stream ended inside a frame", truncated)
        }
        return ExchangeEnvelope.decode(body.toString(Charsets.UTF_8))
    }
}
