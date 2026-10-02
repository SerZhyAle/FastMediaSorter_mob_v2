package com.sza.fastmediasorter.data.repository

import javax.inject.Inject

/**
 * Parses simple/extended `.m3u` playlists into bare stream entries. A line that begins with `#EXT-X-`
 * means the body is an HLS manifest the player resolves directly, not a playlist to expand, so it returns
 * empty. The same text inside a title, an attribute or a URL does not count (USER-PLAYLIST 0.12 section 9),
 * and an entry URL ending `.m3u8` is an ordinary entry.
 */
class M3uPlaylistParser @Inject constructor() {

    fun parse(text: String): List<ParsedStreamEntry> {
        val lines = text.lineSequence().map { it.trim() }
        if (lines.any { it.startsWith(HLS_TAG_PREFIX, ignoreCase = true) }) return emptyList()

        val entries = mutableListOf<ParsedStreamEntry>()
        var pendingTitle: String? = null
        for (line in lines) {
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:") -> pendingTitle = titleOf(line)
                line.startsWith("#") -> Unit
                else -> {
                    val title = pendingTitle ?: hostOf(line)
                    entries.add(ParsedStreamEntry(url = line, title = title))
                    pendingTitle = null
                }
            }
        }
        return entries
    }

    /**
     * USER-PLAYLIST 0.11 section 8 item 2: the title starts after the first comma outside double quotes,
     * so `group-title="Rock, Pop",Name` is titled `Name`. Null when there is no such comma or it is blank.
     */
    private fun titleOf(extinfLine: String): String? {
        var quoted = false
        extinfLine.forEachIndexed { index, char ->
            if (char == '"') {
                quoted = !quoted
            } else if (char == ',' && !quoted) {
                return extinfLine.substring(index + 1).trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun hostOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.takeIf { it.isNotEmpty() } ?: url
    }

    data class ParsedStreamEntry(val url: String, val title: String)

    private companion object {
        const val HLS_TAG_PREFIX = "#EXT-X-"
    }
}
