package com.sza.fastmediasorter.data.glide

private const val DEFAULT_MAX_ENTRIES = 512
private const val MAP_INITIAL_CAPACITY = 16
private const val MAP_LOAD_FACTOR = 0.75f

/**
 * Process-scoped record of PDF files that the system renderer refused to open.
 *
 * Glide re-binds a list cell on every scroll, so without a record the same refusal is retried and
 * logged each time. The record stays in memory because a refusal is a property of the renderer
 * build, not of the file's history: persisting it would keep a file blocked after a system update.
 * The key carries length and last-modified time, so a replaced or repaired file is a different key
 * and gets a fresh attempt. Bounded by least-recently-used eviction so a huge folder cannot grow it.
 */
internal class PdfNonDecodableCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    private val keys = object : LinkedHashMap<String, Unit>(MAP_INITIAL_CAPACITY, MAP_LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun isKnownNonDecodable(key: String): Boolean = keys[key] != null

    @Synchronized
    fun markNonDecodable(key: String) {
        keys[key] = Unit
    }

    companion object {
        fun keyFor(path: String, length: Long, lastModified: Long): String = "$path|$length|$lastModified"
    }
}
