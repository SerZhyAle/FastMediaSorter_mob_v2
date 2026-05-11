package com.sza.fastmediasorter.data.link.auth

import java.net.HttpCookie

/**
 * S0155: best-effort extraction of a human-readable account name hint from
 * WebView session cookies. Used to pre-fill the "Name this account" dialog
 * after login, so users get a recognisable default without manual typing.
 *
 * Resolution per §6.1: the hint is not guaranteed to be the canonical username;
 * the user always confirms or edits it before saving. The stable `accountId`
 * is a UUID generated separately — this is only a display label hint.
 */
object AccountNameHintExtractor {

    /**
     * Returns the first recognisable username-like value found in [cookies],
     * or null if none is found.
     *
     * Checked in order:
     * 1. `username` — used by several platforms as a plain-text username cookie.
     * 2. `ds_user` — Instagram/Threads plain-text username cookie.
     * 3. `twid` — X (Twitter) user id cookie; value is `u=<id>`, stripped to the id part.
     */
    fun extract(cookies: List<HttpCookie>): String? {
        val byName = cookies.associateBy { it.name.lowercase() }
        return listOf("username", "ds_user")
            .firstNotNullOfOrNull { key ->
                byName[key]?.value?.takeIf { it.isNotBlank() }
            }
            ?: byName["twid"]?.value?.removePrefix("u=")?.takeIf { it.isNotBlank() }
    }
}
