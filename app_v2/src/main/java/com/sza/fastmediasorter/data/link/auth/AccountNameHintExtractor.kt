package com.sza.fastmediasorter.data.link.auth

import java.net.HttpCookie

/**
 * Best-effort extraction of a recognizable account label from harvested cookies.
 */
object AccountNameHintExtractor {

    fun extract(cookies: List<HttpCookie>): String? {
        val byName = cookies.associateBy { it.name.lowercase() }
        return listOf("username", "ds_user")
            .firstNotNullOfOrNull { key ->
                byName[key]?.value?.takeIf { it.isNotBlank() }
            }
            ?: byName["twid"]?.value?.removePrefix("u=")?.takeIf { it.isNotBlank() }
    }
}