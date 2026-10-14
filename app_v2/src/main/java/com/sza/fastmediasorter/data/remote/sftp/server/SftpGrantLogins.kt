package com.sza.fastmediasorter.data.remote.sftp.server

import com.sza.fastmediasorter.domain.model.ExchangeGrant
import java.security.MessageDigest

/**
 * Who may log in to the embedded server besides the install's own login, and what each grant login sees
 * (contract `ANYWHERE-ACCESS` item L, `DEVICE-EXCHANGE` items BD and BE): a grant login opens exactly the one
 * served root its grant names, an unknown login opens nothing. The snapshot is replaced whole, so a session never
 * reads half of an update.
 */
class SftpGrantLogins {

    /** One live grant login: its grant and its password. */
    data class Live(val grant: ExchangeGrant, val password: String) {
        override fun toString(): String = "Live(${grant.login})"
    }

    @Volatile
    private var byLogin: Map<String, Live> = emptyMap()

    fun replace(live: List<Live>) {
        byLogin = live.associateBy { it.grant.login }
    }

    fun isGrantLogin(user: String?): Boolean = user != null && byLogin.containsKey(user)

    /** Constant time on the password: the comparison must not tell an outsider how many characters matched. */
    fun accepts(user: String?, password: String?): Boolean {
        val live = user?.let { byLogin[it] } ?: return false
        return MessageDigest.isEqual(
            live.password.toByteArray(Charsets.UTF_8),
            password.orEmpty().toByteArray(Charsets.UTF_8),
        )
    }

    /**
     * The roots [user] may see: every root for [installLogin], the granted one for a grant login, none otherwise.
     * [treeUriOf] reads a root's tree URI, which is what a grant names.
     */
    fun <T> rootsFor(user: String?, installLogin: String, allRoots: List<T>, treeUriOf: (T) -> String): List<T> {
        val grant = user?.let { byLogin[it] }?.grant
        return when {
            user == installLogin -> allRoots
            grant != null -> allRoots.filter { treeUriOf(it) == grant.rootUri }
            else -> emptyList()
        }
    }
}
