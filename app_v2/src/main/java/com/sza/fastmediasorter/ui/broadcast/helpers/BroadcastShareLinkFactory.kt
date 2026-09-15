package com.sza.fastmediasorter.ui.broadcast.helpers

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BroadcastShareLinkFactory {
    private const val PACKAGE_NAME = "com.sza.fastmediasorter"
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
