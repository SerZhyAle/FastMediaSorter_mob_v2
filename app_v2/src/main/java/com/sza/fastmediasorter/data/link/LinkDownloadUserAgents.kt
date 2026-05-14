package com.sza.fastmediasorter.data.link

/**
 * Shared User-Agent profiles for link-download stacks.
 *
 * S0182: legacy sessions may have cookies but no pinned UA yet, so every fallback path
 * must use the same Android Chrome Mobile profile instead of inventing per-layer defaults.
 */
object LinkDownloadUserAgents {
    const val MOBILE_BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}