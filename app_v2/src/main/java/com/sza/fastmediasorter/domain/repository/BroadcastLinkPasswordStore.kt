package com.sza.fastmediasorter.domain.repository

/**
 * S4405: the custom password of the broadcast's external link, chosen by the person. It lives only in the
 * device's protected store - never in settings, a backup, a log or a diagnostic dump.
 */
interface BroadcastLinkPasswordStore {

    /** The stored custom password, or null when none was set or the store cannot decrypt it. */
    suspend fun get(): String?

    /** False when the password could not be encrypted; nothing is stored then. */
    suspend fun put(password: String): Boolean

    suspend fun clear()
}
