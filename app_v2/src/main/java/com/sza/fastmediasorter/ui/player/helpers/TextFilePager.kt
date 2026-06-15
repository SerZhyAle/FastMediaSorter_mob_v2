package com.sza.fastmediasorter.ui.player.helpers

import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Paged text file reader using RandomAccessFile.
 * Reads text in configurable chunks (default 50KB) with page navigation.
 * Handles multi-byte character boundary correction at chunk edges.
 *
 * Page boundaries are aligned to newline characters where possible,
 * ensuring clean line breaks between pages.
 */
class TextFilePager(
    val file: File,
    var charset: Charset = Charsets.UTF_8,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : AutoCloseable {

    companion object {
        const val DEFAULT_CHUNK_SIZE = 50 * 1024 // 50KB
        const val MAX_FILE_SIZE = 100L * 1024 * 1024 // 100MB
        private const val NEWLINE_SEARCH_RANGE = 1024 // Search back up to 1KB for newline boundary
        private const val MAX_UTF8_CHAR_BYTES = 6 // Max backtrack for UTF-8 boundary
    }

    private var raf: RandomAccessFile? = null
    private val rafLock = Any() // Shared lock for read/close synchronization
    val fileSize: Long = file.length()

    // Lazily built index of byte offsets for each page start
    private val pageStartOffsets = mutableListOf<Long>()
    private var fullyIndexed = false

    // Line count per page (populated as pages are read)
    private val pageLineCounts = mutableListOf<Int>()

    var currentPage: Int = 0
        private set

    init {
        pageStartOffsets.add(0L)
    }

    fun open() {
        raf = RandomAccessFile(file, "r")
        Timber.d("TextFilePager: opened ${file.name}, size=${fileSize}, charset=$charset, chunkSize=$chunkSize")
    }

    override fun close() {
        synchronized(rafLock) {
            try {
                raf?.close()
            } catch (e: IOException) {
                // EIO/other close failure means the backing fd or volume is already gone
                // (SD/USB unmount, dead content-provider fd). The descriptor is abandoned during
                // teardown either way - swallow so onDestroy is not crashed by a doomed close().
                Timber.w(e, "TextFilePager: close failed, fd already lost; abandoning")
            } finally {
                raf = null
            }
        }
        Timber.d("TextFilePager: closed")
    }

    /**
     * Read the page at the given index.
     * Pages are lazily indexed - reading page N will index all pages up to N.
     * Returns page text as String (may be empty for pages beyond EOF).
     */
    fun readPage(pageIndex: Int): String {
        raf ?: throw IllegalStateException("TextFilePager not opened")

        // Ensure index covers this page
        ensureIndexedUpTo(pageIndex)

        if (pageIndex >= pageStartOffsets.size) {
            return "" // Beyond end of file
        }

        currentPage = pageIndex

        val startOffset = pageStartOffsets[pageIndex]

        // Determine end offset
        val endOffset: Long = when {
            pageIndex + 1 < pageStartOffsets.size -> pageStartOffsets[pageIndex + 1]
            fullyIndexed -> fileSize
            else -> {
                val end = findPageEnd(startOffset)
                if (end >= fileSize) {
                    fullyIndexed = true
                    fileSize
                } else {
                    pageStartOffsets.add(end)
                    end
                }
            }
        }

        val length = (endOffset - startOffset).toInt()
        if (length <= 0) return ""

        val buffer = ByteArray(length)
        synchronized(rafLock) {
            val r = raf ?: throw IllegalStateException("TextFilePager closed during read")
            r.seek(startOffset)
            r.readFully(buffer)
        }

        val text = String(buffer, charset)

        // Track line count for this page
        while (pageLineCounts.size <= pageIndex) {
            pageLineCounts.add(0)
        }
        pageLineCounts[pageIndex] = text.count { it == '\n' } + if (text.isNotEmpty() && !text.endsWith('\n')) 1 else 0

        Timber.d("TextFilePager: readPage($pageIndex) offset=$startOffset..$endOffset, ${text.length} chars, ${pageLineCounts[pageIndex]} lines")
        return text
    }

    /**
     * Get estimated total page count.
     * Accurate once [fullyIndexed], otherwise estimated from file size.
     */
    fun getEstimatedPageCount(): Int {
        return if (fullyIndexed) {
            pageStartOffsets.size
        } else {
            maxOf(pageStartOffsets.size, ((fileSize + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1))
        }
    }

    fun isFullyIndexed(): Boolean = fullyIndexed

    /**
     * Whether the file fits entirely in a single page (no paging needed).
     */
    fun isSinglePage(): Boolean = fileSize <= chunkSize

    fun hasNextPage(): Boolean {
        if (!fullyIndexed && currentPage + 1 >= pageStartOffsets.size) {
            ensureIndexedUpTo(currentPage + 1)
        }
        return if (fullyIndexed) currentPage + 1 < pageStartOffsets.size else true
    }

    fun hasPreviousPage(): Boolean = currentPage > 0

    /**
     * Get the starting line number (1-based) for a given page.
     * Requires all previous pages to have been read.
     */
    fun getStartLineNumber(pageIndex: Int): Int {
        var lineNum = 1
        for (i in 0 until pageIndex) {
            if (i < pageLineCounts.size) {
                lineNum += pageLineCounts[i]
            }
        }
        return lineNum
    }

    // --- Private helpers ---

    /**
     * Lazily index pages up to the target page index.
     */
    private fun ensureIndexedUpTo(targetPageIndex: Int) {
        if (fullyIndexed) return

        while (pageStartOffsets.size <= targetPageIndex && !fullyIndexed) {
            val lastOffset = pageStartOffsets.last()
            val nextEnd = findPageEnd(lastOffset)
            if (nextEnd >= fileSize) {
                fullyIndexed = true
            } else {
                pageStartOffsets.add(nextEnd)
            }
        }
    }

    /**
     * Find the end offset for a page starting at [startOffset].
     * Tries to end at a newline boundary within [chunkSize] bytes.
     */
    private fun findPageEnd(startOffset: Long): Long {
        raf ?: throw IllegalStateException("TextFilePager not opened")

        val remaining = fileSize - startOffset
        if (remaining <= chunkSize) {
            return fileSize // Last page covers the rest
        }

        val tentativeEnd = startOffset + chunkSize

        // Search backward from tentativeEnd to find last newline
        val searchSize = minOf(NEWLINE_SEARCH_RANGE.toLong(), (tentativeEnd - startOffset)).toInt()
        val searchStart = tentativeEnd - searchSize
        val searchBuffer = ByteArray(searchSize)

        synchronized(rafLock) {
            val r = raf ?: return tentativeEnd
            r.seek(searchStart)
            r.readFully(searchBuffer)
        }

        // Find last newline in search buffer
        for (i in searchBuffer.size - 1 downTo 0) {
            if (searchBuffer[i] == '\n'.code.toByte()) {
                val newlineOffset = searchStart + i + 1 // Next page starts after newline
                if (newlineOffset > startOffset) {
                    return newlineOffset
                }
            }
        }

        // No newline found - use charset boundary correction
        return adjustForCharsetBoundary(tentativeEnd)
    }

    /**
     * Adjust offset to avoid splitting multi-byte characters.
     * For UTF-8: back up to find a non-continuation byte (not 10xxxxxx).
     * For single-byte charsets (ISO-8859-1, Windows-*): no adjustment needed.
     */
    private fun adjustForCharsetBoundary(offset: Long): Long {
        val charsetName = charset.name().lowercase()
        if (charsetName.startsWith("iso-8859") || charsetName.startsWith("windows-") ||
            charsetName == "us-ascii" || charsetName.startsWith("koi8")
        ) {
            return offset // Single-byte charset - safe at any boundary
        }

        // UTF-8 / multi-byte boundary correction
        raf ?: return offset
        var pos = offset

        synchronized(rafLock) {
            val r = raf ?: return offset
            while (pos > 0 && offset - pos < MAX_UTF8_CHAR_BYTES) {
                r.seek(pos)
                val b = r.read()
                if (b and 0xC0 != 0x80) {
                    // Not a continuation byte - safe boundary
                    return pos
                }
                pos--
            }
        }

        return offset
    }
}
