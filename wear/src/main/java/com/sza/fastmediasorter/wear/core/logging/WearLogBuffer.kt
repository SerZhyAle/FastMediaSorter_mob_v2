package com.sza.fastmediasorter.wear.core.logging

/**
 * S1802: bounded in-memory log store for the watch.
 *
 * The watch keeps no log file at all - measured 2026-08-18, the app's data directory holds only the
 * settings store - and this ticket deliberately does not add one: a file would drag in a FileProvider,
 * rotation, cleanup and disk accounting on a device with little space, for content that leaves for the
 * phone immediately anyway. A ring buffer is bounded by construction instead.
 *
 * The ceiling is expressed in bytes rather than in lines because one stack trace is worth hundreds of
 * ordinary lines, so a line count cannot bound the payload that has to fit one Data Layer message.
 */
object WearLogBuffer {

    /**
     * Ceiling for retained log text.
     *
     * 49152 bytes leaves 53248 bytes of headroom under the 102400-byte Data Layer per-message ceiling,
     * for the report's non-log fields (format version, app version, device model, Android release,
     * capture instant), the JSON framing and the UTF-8 expansion of a non-ASCII log body. The headroom
     * is deliberately far larger than those fields need, because the measurement recorded in
     * `temp/S1802/message-limit.txt` showed the per-message ceiling is enforced by the peer, not by the
     * sending watch - an unpaired watch refuses every size for a missing node instead - and an
     * oversized send was observed to hang rather than fail.
     */
    const val MAX_BYTES: Int = 48 * 1024

    private const val NEWLINE_BYTES = 1

    /** Top two bits of a UTF-8 continuation byte, which is never the start of a character. */
    private const val CONTINUATION_MASK = 0xC0

    private const val CONTINUATION_MARKER = 0x80

    private val lines = ArrayDeque<String>()

    private var retainedBytes: Int = 0

    private val lock = Any()

    /** Retained size in bytes, for tests and for the payload builder. */
    val sizeInBytes: Int
        get() = synchronized(lock) { retainedBytes }

    /**
     * Append one formatted line, evicting the oldest lines until the result fits [MAX_BYTES].
     *
     * A single line longer than the ceiling is truncated rather than dropped: losing the fact that a
     * huge record happened is worse for diagnosis than losing its tail.
     */
    fun append(line: String) {
        val bounded = truncateToBytes(line, MAX_BYTES - NEWLINE_BYTES)
        val cost = bounded.toByteArray().size + NEWLINE_BYTES
        synchronized(lock) {
            lines.addLast(bounded)
            retainedBytes += cost
            evictWhileOverCeiling()
        }
    }

    /**
     * Current contents, oldest first, as one string.
     *
     * Returns a snapshot taken under the lock so a concurrent append cannot tear the result.
     */
    fun snapshot(): String = synchronized(lock) { lines.joinToString(separator = "\n") }

    /** Drop everything. Used by tests and by a future explicit "clear" affordance. */
    fun clear() {
        synchronized(lock) {
            lines.clear()
            retainedBytes = 0
        }
    }

    /**
     * Cut [text] so its UTF-8 encoding fits [limit] bytes.
     *
     * Cuts on the byte array rather than on characters because a character count cannot bound an
     * encoded size, and the ceiling this feeds is a byte ceiling. The cut is walked back to a character
     * boundary, so the result never re-encodes larger than [limit].
     */
    private fun truncateToBytes(text: String, limit: Int): String {
        val encoded = text.toByteArray()
        if (encoded.size <= limit) {
            return text
        }
        // Walk the cut back off any UTF-8 continuation byte. A cut inside a multi-byte character
        // decodes to U+FFFD, which re-encodes to three bytes and can land back over the limit - the
        // caller's cost accounting would then evict the very line this branch exists to keep.
        var cut = limit
        while (cut > 0 && (encoded[cut].toInt() and CONTINUATION_MASK) == CONTINUATION_MARKER) {
            cut--
        }
        return String(encoded.copyOf(cut), Charsets.UTF_8)
    }

    private fun evictWhileOverCeiling() {
        while (retainedBytes > MAX_BYTES && lines.isNotEmpty()) {
            val removed = lines.removeFirst()
            retainedBytes -= removed.toByteArray().size + NEWLINE_BYTES
        }
        if (retainedBytes < 0) {
            retainedBytes = 0
        }
    }
}
