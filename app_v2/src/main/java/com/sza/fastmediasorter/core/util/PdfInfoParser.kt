package com.sza.fastmediasorter.core.util

import timber.log.Timber
import java.io.File

/**
 * Zero-dependency parser for the PDF /Info dictionary.
 *
 * Covers the common case where /Info points to a standalone indirect object. Compressed
 * object streams (PDF 1.5+ ObjStm) are not decoded — implementing FlateDecode purely for
 * metadata would cost far more than it returns. In practice, the vast majority of PDF
 * producers emit /Info as a standalone object because it is written before cross-ref build.
 */
internal object PdfInfoParser {

    data class PdfInfo(
        val version: String? = null,
        val title: String? = null,
        val author: String? = null,
        val subject: String? = null,
        val keywords: String? = null,
        val creator: String? = null,
        val producer: String? = null,
        val creationDate: String? = null,
        val modificationDate: String? = null
    )

    // Cap on files we slurp into RAM. Larger PDFs skip deep extraction but still return version.
    private const val MAX_BYTES_FOR_DEEP_PARSE = 50L * 1024 * 1024

    // Window scanned for the last /Info reference near the trailer / xref-stream dict.
    private const val TRAILER_SEARCH_WINDOW = 128 * 1024

    // Safety cap on dict length when searching for matching ">>". Real Info dicts are <1 KB.
    private const val MAX_DICT_LEN = 64 * 1024

    fun parse(file: File): PdfInfo {
        return try {
            val length = file.length()
            if (length <= 0L) return PdfInfo()

            if (length > MAX_BYTES_FOR_DEEP_PARSE) {
                return PdfInfo(version = readVersionOnly(file))
            }

            parseBytes(file.readBytes())
        } catch (e: Exception) {
            Timber.w(e, "PdfInfoParser: failed to parse ${file.path}")
            PdfInfo()
        }
    }

    /**
     * Parse from an input stream. The stream is read in full (up to MAX_BYTES_FOR_DEEP_PARSE);
     * larger payloads return only the header version so we do not blow up RAM for SAF sources.
     */
    fun parse(stream: java.io.InputStream): PdfInfo {
        return try {
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(chunk)
                if (read <= 0) break
                total += read
                if (total > MAX_BYTES_FOR_DEEP_PARSE) {
                    // Return whatever version the already-read prefix gave us — we bail on deep parse.
                    val prefix = buffer.toByteArray()
                    return PdfInfo(version = parseHeader(prefix))
                }
                buffer.write(chunk, 0, read)
            }
            parseBytes(buffer.toByteArray())
        } catch (e: Exception) {
            Timber.w(e, "PdfInfoParser: failed to parse input stream")
            PdfInfo()
        }
    }

    private fun parseBytes(bytes: ByteArray): PdfInfo {
        val version = parseHeader(bytes)
        val ref = findInfoReference(bytes) ?: return PdfInfo(version = version)
        val dictBytes = findObjectDict(bytes, ref) ?: return PdfInfo(version = version)
        return parseDict(dictBytes, version)
    }

    private fun readVersionOnly(file: File): String? {
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(32)
                val read = stream.read(header)
                if (read <= 0) null else parseHeader(header.copyOf(read))
            }
        } catch (e: Exception) {
            Timber.w(e, "PdfInfoParser: failed to read header for ${file.path}")
            null
        }
    }

    private fun parseHeader(bytes: ByteArray): String? {
        val limit = minOf(bytes.size, 32)
        val head = String(bytes, 0, limit, Charsets.ISO_8859_1)
        val m = Regex("%PDF-(\\d+\\.\\d+)").find(head) ?: return null
        return m.groupValues[1]
    }

    private fun findInfoReference(bytes: ByteArray): Pair<Int, Int>? {
        val from = maxOf(0, bytes.size - TRAILER_SEARCH_WINDOW)
        val tail = String(bytes, from, bytes.size - from, Charsets.ISO_8859_1)
        val last = Regex("/Info\\s+(\\d+)\\s+(\\d+)\\s+R").findAll(tail).lastOrNull() ?: return null
        return last.groupValues[1].toInt() to last.groupValues[2].toInt()
    }

    private fun findObjectDict(bytes: ByteArray, ref: Pair<Int, Int>): String? {
        // Locate "N G obj" byte-wise to avoid converting the whole file to a String.
        val needle = "${ref.first} ${ref.second} obj".toByteArray(Charsets.ISO_8859_1)
        val objStart = indexOfBytes(bytes, needle, 0)
        if (objStart < 0) return null

        val dictOpen = byteArrayOf('<'.code.toByte(), '<'.code.toByte())
        val dictStart = indexOfBytes(bytes, dictOpen, objStart + needle.size)
        if (dictStart < 0) return null

        // Convert a bounded slice around the dict to a String for structural parsing.
        val sliceEnd = minOf(bytes.size, dictStart + MAX_DICT_LEN)
        val slice = String(bytes, dictStart, sliceEnd - dictStart, Charsets.ISO_8859_1)
        val dictEnd = findMatchingDictEnd(slice, 0) ?: return null
        return slice.substring(0, dictEnd + 2)
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty() || needle.size > haystack.size - from) return -1
        val first = needle[0]
        var i = from
        val max = haystack.size - needle.size
        while (i <= max) {
            if (haystack[i] == first) {
                var j = 1
                while (j < needle.size && haystack[i + j] == needle[j]) j++
                if (j == needle.size) return i
            }
            i++
        }
        return -1
    }

    /** Scan forward from index of "<<" to find the matching ">>", respecting strings/comments/nesting. */
    private fun findMatchingDictEnd(s: String, start: Int): Int? {
        var i = start + 2
        var depth = 1
        while (i < s.length) {
            val c = s[i]
            when {
                c == '<' && i + 1 < s.length && s[i + 1] == '<' -> { depth++; i += 2 }
                c == '>' && i + 1 < s.length && s[i + 1] == '>' -> {
                    depth--
                    if (depth == 0) return i
                    i += 2
                }
                c == '(' -> i = skipLiteralString(s, i)
                c == '<' -> {
                    val end = s.indexOf('>', i + 1)
                    if (end < 0) return null
                    i = end + 1
                }
                c == '%' -> {
                    var j = i + 1
                    while (j < s.length && s[j] != '\n' && s[j] != '\r') j++
                    i = j
                }
                else -> i++
            }
        }
        return null
    }

    /** Returns index just past the closing ')'. Handles nested parens and backslash escapes. */
    private fun skipLiteralString(s: String, start: Int): Int {
        var i = start + 1
        var depth = 1
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                '(' -> { depth++; i++ }
                ')' -> {
                    depth--
                    if (depth == 0) return i + 1
                    i++
                }
                else -> i++
            }
        }
        return i
    }

    private fun parseDict(dict: String, version: String?): PdfInfo {
        val entries = tokenizeDict(dict)
        return PdfInfo(
            version = version,
            title = decodeString(entries["Title"]),
            author = decodeString(entries["Author"]),
            subject = decodeString(entries["Subject"]),
            keywords = decodeString(entries["Keywords"]),
            creator = decodeString(entries["Creator"]),
            producer = decodeString(entries["Producer"]),
            creationDate = decodeString(entries["CreationDate"])?.let(::formatPdfDate),
            modificationDate = decodeString(entries["ModDate"])?.let(::formatPdfDate)
        )
    }

    private fun tokenizeDict(dict: String): Map<String, String> {
        // Strip surrounding "<<" ">>"
        if (dict.length < 4) return emptyMap()
        val inner = dict.substring(2, dict.length - 2)
        val result = mutableMapOf<String, String>()
        var i = 0
        while (i < inner.length) {
            while (i < inner.length && inner[i].isWhitespace()) i++
            if (i >= inner.length || inner[i] != '/') { i++; continue }
            val nameStart = i + 1
            var nameEnd = nameStart
            while (nameEnd < inner.length && !isNameTerminator(inner[nameEnd])) nameEnd++
            if (nameEnd == nameStart) { i = nameEnd + 1; continue }
            val name = inner.substring(nameStart, nameEnd)
            i = nameEnd
            while (i < inner.length && inner[i].isWhitespace()) i++
            val valueStart = i
            val valueEnd = readValueEnd(inner, i)
            if (valueEnd <= valueStart) { i = valueStart + 1; continue }
            result[name] = inner.substring(valueStart, valueEnd).trim()
            i = valueEnd
        }
        return result
    }

    private fun isNameTerminator(c: Char): Boolean =
        c.isWhitespace() || c == '/' || c == '<' || c == '>' || c == '(' || c == ')' || c == '[' || c == ']'

    private fun readValueEnd(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            val c = s[i]
            when {
                c == '(' -> i = skipLiteralString(s, i)
                c == '<' && i + 1 < s.length && s[i + 1] == '<' -> {
                    val end = findMatchingDictEnd(s, i) ?: return s.length
                    i = end + 2
                }
                c == '<' -> {
                    val end = s.indexOf('>', i + 1)
                    i = if (end < 0) s.length else end + 1
                }
                c == '[' -> {
                    var depth = 1
                    i++
                    while (i < s.length && depth > 0) {
                        when (s[i]) {
                            '[' -> { depth++; i++ }
                            ']' -> { depth--; i++ }
                            '(' -> i = skipLiteralString(s, i)
                            else -> i++
                        }
                    }
                }
                c == '/' -> return i
                else -> i++
            }
        }
        return i
    }

    private fun decodeString(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val decoded = when {
            raw.startsWith("(") && raw.endsWith(")") ->
                decodeLiteral(raw.substring(1, raw.length - 1))
            raw.startsWith("<") && !raw.startsWith("<<") && raw.endsWith(">") ->
                decodeHex(raw.substring(1, raw.length - 1))
            else -> null
        } ?: return null
        return decoded.trim().takeIf { it.isNotBlank() }
    }

    private fun decodeLiteral(s: String): String {
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val esc = s[i + 1]) {
                    'n' -> { bytes.add(0x0A); i += 2 }
                    'r' -> { bytes.add(0x0D); i += 2 }
                    't' -> { bytes.add(0x09); i += 2 }
                    'b' -> { bytes.add(0x08); i += 2 }
                    'f' -> { bytes.add(0x0C); i += 2 }
                    '\\' -> { bytes.add(0x5C); i += 2 }
                    '(' -> { bytes.add(0x28); i += 2 }
                    ')' -> { bytes.add(0x29); i += 2 }
                    '\n' -> i += 2
                    '\r' -> { i += 2; if (i < s.length && s[i] == '\n') i++ }
                    in '0'..'7' -> {
                        var j = i + 1
                        var value = 0
                        var count = 0
                        while (j < s.length && count < 3 && s[j] in '0'..'7') {
                            value = value * 8 + (s[j] - '0')
                            j++; count++
                        }
                        bytes.add((value and 0xFF).toByte())
                        i = j
                    }
                    else -> { bytes.add(esc.code.toByte()); i += 2 }
                }
            } else {
                bytes.add((c.code and 0xFF).toByte())
                i++
            }
        }
        return decodeBytes(bytes.toByteArray())
    }

    private fun decodeHex(hex: String): String {
        val clean = hex.filter { !it.isWhitespace() }
        val padded = if (clean.length % 2 == 1) clean + "0" else clean
        val bytes = ByteArray(padded.length / 2)
        var i = 0
        while (i < padded.length - 1) {
            val hi = Character.digit(padded[i], 16)
            val lo = Character.digit(padded[i + 1], 16)
            if (hi < 0 || lo < 0) return ""
            bytes[i / 2] = ((hi shl 4) or lo).toByte()
            i += 2
        }
        return decodeBytes(bytes)
    }

    private fun decodeBytes(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        // PDFDocEncoding is a near-superset of Latin-1 for printable characters; decoding as
        // ISO-8859-1 is good enough for the Info-dict fields that end up in the UI.
        return String(bytes, Charsets.ISO_8859_1)
    }

    // PDF date format: "D:YYYYMMDDHHmmSSOHH'mm'" (PDF 1.7 spec 7.9.4). Returns the original
    // if it does not look like a PDF date, so non-standard values still reach the UI.
    private fun formatPdfDate(raw: String): String {
        val trimmed = raw.removePrefix("D:").trim()
        if (trimmed.length < 4 || !trimmed.take(4).all { it.isDigit() }) return raw
        return try {
            val year = trimmed.substring(0, 4)
            val month = trimmed.safeSlice(4, 6) ?: "01"
            val day = trimmed.safeSlice(6, 8) ?: "01"
            val hour = trimmed.safeSlice(8, 10) ?: "00"
            val min = trimmed.safeSlice(10, 12) ?: "00"
            val sec = trimmed.safeSlice(12, 14) ?: "00"
            val tz = if (trimmed.length > 14) {
                when (val tzChar = trimmed[14]) {
                    'Z' -> " UTC"
                    '+', '-' -> {
                        val tzH = trimmed.safeSlice(15, 17) ?: "00"
                        val mStart = if (trimmed.length > 17 && trimmed[17] == '\'') 18 else 17
                        val tzM = trimmed.safeSlice(mStart, mStart + 2) ?: "00"
                        " UTC$tzChar$tzH:$tzM"
                    }
                    else -> ""
                }
            } else ""
            "$year-$month-$day $hour:$min:$sec$tz"
        } catch (e: Exception) {
            raw
        }
    }

    private fun String.safeSlice(from: Int, to: Int): String? =
        if (from >= 0 && to <= length && from < to) substring(from, to) else null
}
