package com.sza.fastmediasorter.util

import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * CAPTURE-OUTPUT rule 16: a video or audio recording carries its capture time in its container's
 * creation time (the `mvhd` box creation_time in ISO-BMFF MP4 / M4A containers).
 *
 * This reader/writer parses ISO-BMFF containers directly using [FileChannel], enabling fast and
 * deterministic verification on JVM and Android without depending on native MediaMetadataRetriever.
 */
object Mp4ContainerTimeReader {

    /**
     * Difference in seconds between the MP4 epoch (1904-01-01 00:00:00 UTC) and the Unix epoch
     * (1970-01-01 00:00:00 UTC): 66 years, including 17 leap years = 24,107 days = 2,082,844,800 seconds.
     */
    const val MP4_EPOCH_OFFSET_SECONDS = 2082844800L

    private const val BOX_HEADER_SIZE = 8L
    private const val LARGE_BOX_HEADER_SIZE = 16L
    private const val MILLIS_PER_SECOND = 1000L
    private const val UINT32_MASK = 0xFFFFFFFFL
    private const val INT_SIZE = 4
    private const val LONG_SIZE = 8
    private const val FLAGS_OFFSET = 4L
    private const val V0_TIME_BUFFER_SIZE = 8
    private const val V1_TIME_BUFFER_SIZE = 16
    private const val TYPE_BYTE_COUNT = 4
    private const val VERSION_BUFFER_SIZE = 1

    private data class BoxHeader(
        val type: String,
        val offset: Long,
        val contentOffset: Long,
        val size: Long,
    ) {
        val end: Long get() = offset + size
    }

    /**
     * Reads the container creation time from [file] in seconds since Unix epoch (1970-01-01 UTC).
     * Returns null if [file] does not exist, is not an MP4/M4A container, or has no `mvhd` box.
     */
    fun readCreationTimeSeconds(file: File): Long? {
        if (!file.exists() || !file.isFile || file.length() < BOX_HEADER_SIZE) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val channel = raf.channel
                findMvhdBox(channel)?.let { mvhd ->
                    readMvhdCreationTimeSeconds(channel, mvhd)
                }
            }
        }.getOrElse { error ->
            Timber.w(error, "Mp4ContainerTimeReader: failed to read creation time from ${file.name}")
            null
        }
    }

    /**
     * Reads the container creation time from [file] in milliseconds since Unix epoch (1970-01-01 UTC).
     */
    fun readCreationTimeMillis(file: File): Long? =
        readCreationTimeSeconds(file)?.let { it * MILLIS_PER_SECOND }

    /**
     * Updates the container creation and modification time in [file] in place.
     * [unixEpochSeconds] is seconds since 1970-01-01 00:00:00 UTC.
     * Returns true on success, false if the `mvhd` box was not found or could not be updated.
     */
    fun writeCreationTimeSeconds(file: File, unixEpochSeconds: Long): Boolean {
        if (!file.exists() || !file.isFile || file.length() < BOX_HEADER_SIZE) return false
        return runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                val channel = raf.channel
                val mvhd = findMvhdBox(channel) ?: return@use false

                channel.position(mvhd.contentOffset)
                val versionBuf = ByteBuffer.allocate(VERSION_BUFFER_SIZE)
                channel.read(versionBuf)
                versionBuf.flip()
                val version = versionBuf.get().toInt()

                val mp4Seconds = unixEpochSeconds + MP4_EPOCH_OFFSET_SECONDS

                // Skip 3 bytes flags
                channel.position(mvhd.contentOffset + FLAGS_OFFSET)
                if (version == 0) {
                    val timeBuf = ByteBuffer.allocate(V0_TIME_BUFFER_SIZE)
                    timeBuf.putInt(mp4Seconds.toInt()) // creation_time
                    timeBuf.putInt(mp4Seconds.toInt()) // modification_time
                    timeBuf.flip()
                    channel.write(timeBuf)
                } else {
                    val timeBuf = ByteBuffer.allocate(V1_TIME_BUFFER_SIZE)
                    timeBuf.putLong(mp4Seconds) // creation_time
                    timeBuf.putLong(mp4Seconds) // modification_time
                    timeBuf.flip()
                    channel.write(timeBuf)
                }
                true
            }
        }.getOrElse { error ->
            Timber.w(error, "Mp4ContainerTimeReader: failed to write creation time to ${file.name}")
            false
        }
    }

    /**
     * Updates the container creation and modification time in [file] in place using [timestampMillis].
     */
    fun writeCreationTimeMillis(file: File, timestampMillis: Long): Boolean =
        writeCreationTimeSeconds(file, timestampMillis / MILLIS_PER_SECOND)

    private fun findMvhdBox(channel: FileChannel): BoxHeader? {
        val fileSize = channel.size()
        var offset = 0L
        var foundMoov: BoxHeader? = null
        while (offset + BOX_HEADER_SIZE <= fileSize && foundMoov == null) {
            val header = readHeader(channel, offset, fileSize)
            if (header != null && header.size > 0L && header.end <= fileSize) {
                if (header.type == "moov") {
                    foundMoov = header
                } else {
                    offset = header.end
                }
            } else {
                offset = fileSize
            }
        }
        return foundMoov?.let { moov ->
            findBoxInside(channel, moov.contentOffset, moov.end, "mvhd")
        }
    }

    private fun findBoxInside(
        channel: FileChannel,
        start: Long,
        end: Long,
        targetType: String,
    ): BoxHeader? {
        var offset = start
        var foundBox: BoxHeader? = null
        while (offset + BOX_HEADER_SIZE <= end && foundBox == null) {
            val header = readHeader(channel, offset, end)
            if (header != null && header.size > 0L && header.end <= end) {
                if (header.type == targetType) {
                    foundBox = header
                } else {
                    offset = header.end
                }
            } else {
                offset = end
            }
        }
        return foundBox
    }

    private fun readMvhdCreationTimeSeconds(channel: FileChannel, mvhd: BoxHeader): Long? {
        channel.position(mvhd.contentOffset)
        val headerBuf = ByteBuffer.allocate(INT_SIZE)
        if (channel.read(headerBuf) < INT_SIZE) return null
        headerBuf.flip()

        val version = headerBuf.get().toInt()
        return readCreationTimeForVersion(channel, version)
    }

    private fun readCreationTimeForVersion(channel: FileChannel, version: Int): Long? {
        val size = if (version == 0) INT_SIZE else LONG_SIZE
        val timeBuf = ByteBuffer.allocate(size)
        if (channel.read(timeBuf) < size) return null
        timeBuf.flip()

        val mp4Seconds = if (version == 0) {
            timeBuf.getInt().toLong() and UINT32_MASK
        } else {
            timeBuf.getLong()
        }
        return mp4Seconds - MP4_EPOCH_OFFSET_SECONDS
    }

    private fun readHeader(channel: FileChannel, offset: Long, limit: Long): BoxHeader? {
        if (offset + BOX_HEADER_SIZE > limit) return null

        channel.position(offset)
        val buffer = ByteBuffer.allocate(BOX_HEADER_SIZE.toInt())
        val valid = channel.read(buffer) >= BOX_HEADER_SIZE
        return if (valid) {
            buffer.flip()
            val rawSize = buffer.getInt().toLong() and UINT32_MASK
            val typeBytes = ByteArray(TYPE_BYTE_COUNT)
            buffer.get(typeBytes)
            val type = String(typeBytes, Charsets.US_ASCII)
            parseBoxHeader(channel, offset, limit, type, rawSize)
        } else {
            null
        }
    }

    private fun parseBoxHeader(
        channel: FileChannel,
        offset: Long,
        limit: Long,
        type: String,
        rawSize: Long,
    ): BoxHeader? = when (rawSize) {
        1L -> readLargeBoxHeader(channel, offset, limit, type)
        0L -> BoxHeader(type, offset, offset + BOX_HEADER_SIZE, limit - offset)
        else -> BoxHeader(type, offset, offset + BOX_HEADER_SIZE, rawSize)
    }

    private fun readLargeBoxHeader(
        channel: FileChannel,
        offset: Long,
        limit: Long,
        type: String,
    ): BoxHeader? {
        if (offset + LARGE_BOX_HEADER_SIZE > limit) return null
        val largeBuffer = ByteBuffer.allocate(LONG_SIZE)
        val readSuccess = channel.read(largeBuffer) >= LONG_SIZE
        return if (readSuccess) {
            largeBuffer.flip()
            val largeSize = largeBuffer.getLong()
            BoxHeader(type, offset, offset + LARGE_BOX_HEADER_SIZE, largeSize)
        } else {
            null
        }
    }
}
