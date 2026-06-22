package com.sza.fastmediasorter.data.repository

import javax.inject.Inject

/**
 * Parses simple/extended `.m3u` playlists into bare stream entries. A `#EXT-X-` tag means the body
 * is an HLS manifest the player resolves directly, not a playlist to expand, so it returns empty.
 */
class M3uPlaylistParser @Inject constructor() {

    fun parse(text: String): List<ParsedStreamEntry> {
        if (text.contains("#EXT-X-")) return emptyList()

        val entries = mutableListOf<ParsedStreamEntry>()
        var pendingTitle: String? = null
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:") -> pendingTitle = line.substringAfter(',', "").trim()
                    .takeIf { it.isNotEmpty() }
                line.startsWith("#") -> Unit
                else -> {
                    val title = pendingTitle?.takeIf { it.isNotEmpty() } ?: hostOf(line)
                    entries.add(ParsedStreamEntry(url = line, title = title))
                    pendingTitle = null
                }
            }
        }
        return entries
    }

    private fun hostOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.takeIf { it.isNotEmpty() } ?: url
    }

    data class ParsedStreamEntry(val url: String, val title: String)
}
