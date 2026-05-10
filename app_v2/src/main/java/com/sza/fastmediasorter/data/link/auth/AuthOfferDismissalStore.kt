package com.sza.fastmediasorter.data.link.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0144: remembers hosts for which the user declined the "add authorization?" offer
 * shown when a social-media link is shared, so the offer is not repeated for the
 * same host. Plain (non-encrypted) preferences — only opaque host strings are stored.
 */
@Singleton
class AuthOfferDismissalStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    fun isDismissed(host: String): Boolean {
        val key = host.trim().lowercase()
        if (key.isEmpty()) return false
        return prefs.getStringSet(KEY_HOSTS, emptySet())?.contains(key) == true
    }

    fun markDismissed(host: String) {
        val key = host.trim().lowercase()
        if (key.isEmpty()) return
        val current = prefs.getStringSet(KEY_HOSTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.add(key)) {
            prefs.edit().putStringSet(KEY_HOSTS, current).apply()
        }
    }

    private companion object {
        const val FILE_NAME = "link_download_auth_offer"
        const val KEY_HOSTS = "dismissed_hosts"
    }
}
