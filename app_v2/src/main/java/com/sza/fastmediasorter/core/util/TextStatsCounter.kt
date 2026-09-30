package com.sza.fastmediasorter.core.util

import java.io.Reader

/**
 * One streaming pass over a text source: a multi-MB log never becomes one String plus a list of
 * every word. Counts match the former `text.lines().size`, `split("\\s+")` non-blank tokens and
 * `text.length`.
 */
internal object TextStatsCounter {

    data class TextStats(val lines: Int, val words: Int, val chars: Int)

    private const val BUFFER_CHARS = 8 * 1024

    fun count(reader: Reader): TextStats {
        val buffer = CharArray(BUFFER_CHARS)
        var chars = 0L
        var lineBreaks = 0
        var words = 0
        var inWord = false
        var previousWasCr = false
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            chars += read
            for (i in 0 until read) {
                val c = buffer[i]
                // CRLF is one break, as in String.lines().
                if (c == '\r' || (c == '\n' && !previousWasCr)) lineBreaks++
                previousWasCr = c == '\r'
                val whitespace = isRegexWhitespace(c)
                if (!whitespace && !inWord) words++
                inWord = !whitespace
            }
        }
        return TextStats(
            lines = lineBreaks + 1,
            words = words,
            chars = chars.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
    }

    // The set matched by `\s` in java.util.regex without UNICODE_CHARACTER_CLASS.
    private fun isRegexWhitespace(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'
}
