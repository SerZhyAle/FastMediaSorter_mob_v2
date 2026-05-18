package com.sza.fastmediasorter.data.link

/**
 * S0190 - Return type of [LinkUrlCanonicalizer.canonicalize].
 *
 * Carries the (possibly rewritten) URL plus a media-hint flag. [audioOnly] is set to
 * `true` only when the original URL signalled an audio-only platform - currently the
 * sole case is `music.youtube.com`, which is canonicalised to `www.youtube.com` and
 * therefore loses the host-level audio hint. All other rewrites (`m.youtube.com`,
 * `youtube.com/shorts/<id>`) leave [audioOnly] as `false`.
 */
data class CanonicalizedUrl(val url: String, val audioOnly: Boolean = false)
