package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.BuildConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BroadcastShareLinkFactory {
    // The intent hint names the SENDING build's own package, not the store id: a hardcoded store id
    // makes a debug build emit a link only the store build can open, which left the link path
    // unverifiable on any test device (S3053, device run 2026-09-13).
    private val PACKAGE_NAME: String = BuildConfig.APPLICATION_ID
    private const val FALLBACK_PAGE =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/broadcast-import.html"

    fun create(payload: String): String {
        val encodedPayload = encode(payload)
        val fallback = "$FALLBACK_PAGE#payload=$encodedPayload"
        return "intent://import?payload=$encodedPayload#Intent;scheme=fmsbcast;package=$PACKAGE_NAME;" +
            "S.browser_fallback_url=${encode(fallback)};end"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
