package com.sza.fastmediasorter.utils

import timber.log.Timber
import java.io.File
import java.nio.charset.Charset

/**
 * Detects character encoding of text files.
 *
 * Strategy:
 * 1. BOM (Byte Order Mark) check — most reliable
 * 2. Heuristic probe of first 4KB — UTF-8 validation, then Cyrillic patterns
 * 3. Fallback to ISO-8859-1 (accepts any byte sequence)
 */
object CharsetDetector {

    private const val PROBE_SIZE = 4096 // 4KB for heuristic detection

    /**
     * Standard charsets available for manual "Re-open with Encoding" dialog.
     * Pair of (display name, Charset).
     */
    val SUPPORTED_CHARSETS: List<Pair<String, Charset>> = listOf(
        "UTF-8" to Charsets.UTF_8,
        "UTF-16 LE" to Charsets.UTF_16LE,
        "UTF-16 BE" to Charsets.UTF_16BE,
        "Windows-1251 (Cyrillic)" to Charset.forName("Windows-1251"),
        "Windows-1252 (Western)" to Charset.forName("Windows-1252"),
        "ISO-8859-1 (Latin)" to Charsets.ISO_8859_1,
        "ISO-8859-5 (Cyrillic)" to Charset.forName("ISO-8859-5"),
        "KOI8-R (Russian)" to Charset.forName("KOI8-R"),
        "Shift_JIS (Japanese)" to Charset.forName("Shift_JIS"),
        "GBK (Chinese)" to Charset.forName("GBK"),
        "EUC-KR (Korean)" to Charset.forName("EUC-KR"),
        "US-ASCII" to Charsets.US_ASCII,
    )

    /**
     * Detect charset of a file.
     * Returns detected charset (never null — falls back to UTF-8).
     */
    fun detect(file: File): Charset {
        val probeSize = minOf(PROBE_SIZE.toLong(), file.length()).toInt()
        if (probeSize <= 0) return Charsets.UTF_8

        val probe = ByteArray(probeSize)
        file.inputStream().use { it.read(probe) }

        // 1. BOM check — most reliable indicator
        detectFromBom(probe)?.let { charset ->
            Timber.d("CharsetDetector: BOM detected → ${charset.name()}")
            return charset
        }

        // 2. Heuristic analysis
        val detected = detectByHeuristic(probe)
        Timber.d("CharsetDetector: heuristic → ${detected.name()}")
        return detected
    }

    /**
     * Check for Byte Order Mark at the beginning of the data.
     */
    fun detectFromBom(bytes: ByteArray): Charset? {
        if (bytes.size < 2) return null

        return when {
            // UTF-8 BOM: EF BB BF
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> Charsets.UTF_8

            // UTF-16 LE BOM: FF FE
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE

            // UTF-16 BE BOM: FE FF
            bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE

            else -> null
        }
    }

    /**
     * Heuristic charset detection by analyzing byte patterns.
     * Checks for valid UTF-8 sequences, then tries Windows-1251 patterns.
     */
    fun detectByHeuristic(bytes: ByteArray): Charset {
        // Check if it's valid UTF-8
        if (isValidUtf8(bytes)) {
            return Charsets.UTF_8
        }

        // Check for Windows-1251 (Cyrillic) patterns
        if (looksLikeWindows1251(bytes)) {
            return Charset.forName("Windows-1251")
        }

        // Fallback to ISO-8859-1 (accepts any byte sequence)
        return Charsets.ISO_8859_1
    }

    /**
     * Check if byte sequence is valid UTF-8.
     * Returns true for pure ASCII as well (valid subset of UTF-8).
     */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> i++ // ASCII
                b in 0xC2..0xDF -> {
                    // 2-byte sequence
                    if (i + 1 >= bytes.size) return false
                    if (bytes[i + 1].toInt() and 0xC0 != 0x80) return false
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    // 3-byte sequence
                    if (i + 2 >= bytes.size) return false
                    if (bytes[i + 1].toInt() and 0xC0 != 0x80) return false
                    if (bytes[i + 2].toInt() and 0xC0 != 0x80) return false
                    i += 3
                }
                b in 0xF0..0xF4 -> {
                    // 4-byte sequence
                    if (i + 3 >= bytes.size) return false
                    if (bytes[i + 1].toInt() and 0xC0 != 0x80) return false
                    if (bytes[i + 2].toInt() and 0xC0 != 0x80) return false
                    if (bytes[i + 3].toInt() and 0xC0 != 0x80) return false
                    i += 4
                }
                else -> return false // Invalid UTF-8 start byte (e.g., 0xC0, 0xC1, 0xF5+)
            }
        }
        return true
    }

    /**
     * Check if byte sequence looks like Windows-1251 (Cyrillic).
     * Windows-1251 uses 0xC0-0xFF for Cyrillic А-я, plus Ё=0xA8, ё=0xB8.
     */
    private fun looksLikeWindows1251(bytes: ByteArray): Boolean {
        var cyrillicCount = 0
        var totalNonAscii = 0

        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            if (unsigned > 0x7F) {
                totalNonAscii++
                if (unsigned in 0xC0..0xFF || unsigned == 0xA8 || unsigned == 0xB8) {
                    cyrillicCount++
                }
            }
        }

        // If >60% of non-ASCII bytes fall in the Cyrillic range → likely Windows-1251
        return totalNonAscii > 0 && cyrillicCount.toFloat() / totalNonAscii > 0.6f
    }
}
