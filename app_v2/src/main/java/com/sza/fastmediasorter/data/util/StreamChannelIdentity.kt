package com.sza.fastmediasorter.data.util

import java.util.Locale

/**
 * S1832: turns a channel address into the key every kind of user-authored data is filed under - the pin
 * and its position, the play outcome, the desktop cell that launches the channel.
 *
 * Built on [StreamUrlNormalizer], which already folds the four differences a server treats as equivalent:
 * scheme case, host case, a trailing slash and a default port. This adds the one rule the published bank
 * actually needs - `http` and `https` name the same channel - by replacing either scheme with a single
 * token. Every other scheme, `rtsp` included, is left exactly as the normalizer returned it, because two
 * genuinely different protocols on one host and path are two different channels.
 *
 * Measured on the bank of 17 628 rows published 2026-08-19: the fold collapses 53 groups of rows that are
 * the same broadcaster entered twice, where the other four rules together collapse 5. A further rule must
 * be measured on a live bank before it is added - one that merges two genuinely different channels costs
 * the user exactly the data this key exists to protect.
 *
 * [StreamUrlNormalizer] itself must not acquire the fold: its output is the key `stream_quality_memory`
 * rows are filed under (S1511), and widening it would silently reset every learned rung on every device.
 */
object StreamChannelIdentity {

    fun of(url: String): String {
        val normalized = StreamUrlNormalizer.normalize(url)
        val separator = normalized.indexOf(SCHEME_SEPARATOR)
        val scheme = if (separator > 0) {
            normalized.substring(0, separator).lowercase(Locale.ROOT)
        } else {
            ""
        }
        return if (scheme in WEB_SCHEMES) {
            WEB_SCHEME + normalized.substring(separator)
        } else {
            normalized
        }
    }

    private const val SCHEME_SEPARATOR = "://"
    private const val WEB_SCHEME = "web"
    private val WEB_SCHEMES = setOf("http", "https")
}
