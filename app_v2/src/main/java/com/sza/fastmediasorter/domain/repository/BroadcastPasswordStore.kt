package com.sza.fastmediasorter.domain.repository

/**
 * The pair a stream row answers a protected broadcast's challenge with (contract LIVE-BROADCAST item M), kept in
 * the platform secret store beside the row, never in it: the stream table has no column for a secret.
 */
interface BroadcastPasswordStore {

    /** [password] is null while nobody has typed one yet; the user is the descriptor's `auth.user` all the same. */
    data class StoredPair(val user: String, val password: String?) {
        override fun toString(): String = "StoredPair(user=$user, password=${if (password == null) "none" else "***"})"
    }

    suspend fun get(streamId: String): StoredPair?

    /** False when the secret store refused to keep it, so the caller never believes a lost write. */
    suspend fun put(streamId: String, user: String, password: String?): Boolean

    suspend fun remove(streamId: String)
}
