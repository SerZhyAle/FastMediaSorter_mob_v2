package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Minimal ISO-BMFF reader for Google Spatial Media boxes used by 360° MP4 files.
 *
 * Phase 3 only needs a narrow subset of MP4 parsing, so a local reader keeps the feature
 * available to all flavors without introducing a new parser dependency.
 */
class Mp4SpatialMetadataReader {

    companion object {
        private const val TAG = "Mp4SpatialMetadataReader"
        private const val BOX_HEADER_SIZE = 8L
        private const val LARGE_BOX_HEADER_SIZE = 16L
        private const val FULL_BOX_HEADER_SIZE = 4L
        private const val STSD_HEADER_PAYLOAD_SIZE = 8L
        private const val VISUAL_SAMPLE_ENTRY_CHILD_OFFSET = 78L

        private val STANDARD_CONTAINERS = setOf(
            "moov", "trak", "mdia", "minf", "stbl", "udta", "sv3d", "proj", "mshp"
        )
        private val FULL_BOX_CONTAINERS = setOf("meta")
        private val VISUAL_SAMPLE_ENTRIES = setOf("avc1", "avc3", "hev1", "hvc1", "encv", "mp4v")
    }

    fun detectStereoMode(path: String): StereoMode {
        val channel = openLocalChannel(path) ?: return StereoMode.UNKNOWN
        channel.use {
            val state = ParserState()
            return try {
                parseBoxes(it, 0L, it.size(), state)
                state.toStereoMode()
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to parse spatial metadata from $path")
                StereoMode.UNKNOWN
            }
        }
    }

    private fun openLocalChannel(path: String): FileChannel? {
        if (isRemotePath(path)) {
            Timber.d("$TAG: Remote path does not support direct MP4 box reads, fallback to filename/AR")
            return null
        }

        val resolvedPath = if (path.startsWith("file://")) path.removePrefix("file://") else path
        if (resolvedPath.isBlank()) return null

        val file = File(resolvedPath)
        if (!file.exists() || !file.isFile) {
            Timber.d("$TAG: Local file missing, skipping spatial metadata parse: $path")
            return null
        }

        return RandomAccessFile(file, "r").channel
    }

    private fun isRemotePath(path: String): Boolean = path.startsWith("smb://") ||
        path.startsWith("sftp://") ||
        path.startsWith("ftp://") ||
        path.startsWith("ftps://") ||
        path.startsWith("http://") ||
        path.startsWith("https://") ||
        path.startsWith("gdrive://") ||
        path.startsWith("onedrive://") ||
        path.startsWith("dropbox://") ||
        path.startsWith("cloud://") ||
        path.startsWith("content://")

    private fun parseBoxes(channel: FileChannel, start: Long, end: Long, state: ParserState) {
        var offset = start
        while (offset + BOX_HEADER_SIZE <= end && !state.isComplete()) {
            val header = readHeader(channel, offset, end) ?: break
            if (header.size <= 0L || header.end > end) break

            when (header.type) {
                "st3d" -> state.stereoLayout = readSt3dStereoLayout(channel, header)
                "equi" -> state.projection = ProjectionType.EQUIRECT
                "cbmp" -> state.projection = ProjectionType.CUBEMAP
                "stsd" -> parseStsd(channel, header, state)
                in STANDARD_CONTAINERS -> parseBoxes(channel, header.contentOffset, header.end, state)
                in FULL_BOX_CONTAINERS -> parseBoxes(
                    channel,
                    header.contentOffset + FULL_BOX_HEADER_SIZE,
                    header.end,
                    state
                )
                in VISUAL_SAMPLE_ENTRIES -> {
                    val childStart = header.contentOffset + VISUAL_SAMPLE_ENTRY_CHILD_OFFSET
                    if (childStart < header.end) {
                        parseBoxes(channel, childStart, header.end, state)
                    }
                }
            }

            offset = header.end
        }
    }

    private fun parseStsd(channel: FileChannel, header: BoxHeader, state: ParserState) {
        val childStart = header.contentOffset + STSD_HEADER_PAYLOAD_SIZE
        if (childStart < header.end) {
            parseBoxes(channel, childStart, header.end, state)
        }
    }

    private fun readSt3dStereoLayout(channel: FileChannel, header: BoxHeader): Int? {
        if (header.payloadSize < 5L) return null
        val buffer = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN)
        channel.position(header.contentOffset)
        if (!readFully(channel, buffer)) return null
        buffer.flip()
        buffer.getInt() // version + flags
        return buffer.get().toInt() and 0xFF
    }

    private fun readHeader(channel: FileChannel, offset: Long, parentEnd: Long): BoxHeader? {
        val headerBuffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        channel.position(offset)
        if (!readFully(channel, headerBuffer)) return null

        headerBuffer.flip()
        val rawSize = headerBuffer.int.toLong() and 0xFFFFFFFFL
        val typeBytes = ByteArray(4)
        headerBuffer.get(typeBytes)
        val type = typeBytes.toString(Charsets.US_ASCII)

        val size: Long
        val headerSize: Long
        when (rawSize) {
            0L -> {
                size = parentEnd - offset
                headerSize = BOX_HEADER_SIZE
            }
            1L -> {
                val largeSizeBuffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                if (!readFully(channel, largeSizeBuffer)) return null
                largeSizeBuffer.flip()
                size = largeSizeBuffer.long
                headerSize = LARGE_BOX_HEADER_SIZE
            }
            else -> {
                size = rawSize
                headerSize = BOX_HEADER_SIZE
            }
        }

        if (size < headerSize) return null
        return BoxHeader(
            type = type,
            size = size,
            start = offset,
            headerSize = headerSize,
            contentOffset = offset + headerSize,
        )
    }

    private fun readFully(channel: FileChannel, buffer: ByteBuffer): Boolean {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) return false
        }
        return true
    }

    private data class BoxHeader(
        val type: String,
        val size: Long,
        val start: Long,
        val headerSize: Long,
        val contentOffset: Long,
    ) {
        val end: Long get() = start + size
        val payloadSize: Long get() = size - headerSize
    }

    private enum class ProjectionType {
        UNKNOWN,
        EQUIRECT,
        CUBEMAP,
    }

    private data class ParserState(
        var stereoLayout: Int? = null,
        var projection: ProjectionType = ProjectionType.UNKNOWN,
    ) {
        fun isComplete(): Boolean = stereoLayout != null && projection != ProjectionType.UNKNOWN

        fun toStereoMode(): StereoMode {
            if (projection == ProjectionType.CUBEMAP) {
                Timber.w("$TAG: Cubemap projection found in MP4 spatial metadata, unsupported in current VR roadmap")
                return StereoMode.UNKNOWN
            }
            if (projection != ProjectionType.EQUIRECT) return StereoMode.UNKNOWN

            return when (stereoLayout) {
                0 -> StereoMode.EQUIRECT_360_MONO
                1 -> StereoMode.EQUIRECT_360_OU
                2 -> StereoMode.EQUIRECT_360_SBS
                // mode 3 = STEREO_CUSTOM - used by VR180 cameras (Google VR180 spec)
                3 -> StereoMode.VR180_FISHEYE_SBS
                // mode 4 = right-left reversed SBS - render same as left-right SBS
                4 -> StereoMode.EQUIRECT_360_SBS
                else -> StereoMode.UNKNOWN
            }
        }
    }
}