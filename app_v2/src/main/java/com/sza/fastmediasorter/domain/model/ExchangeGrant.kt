package com.sza.fastmediasorter.domain.model

import java.security.SecureRandom

/**
 * One shared folder given outside the account (contract DEVICE-EXCHANGE items BD and BE): to a `guest` account of
 * the same server, or as a public link with a password. Each grant owns an SFTP [login] of its own, confined to
 * [rootUri], so the install's shared login never leaves the account (`ANYWHERE-ACCESS` item L). The password is
 * not part of the grant: it lives in the platform secret store.
 */
data class ExchangeGrant(
    val grantId: String,
    val kind: Kind,
    /** The tree URI of the one served root the grant opens. */
    val rootUri: String,
    val login: String,
    /** The guest account's login; null for a link. */
    val guest: String? = null,
    /** The link's own outsider capability (item BE); null for a guest grant. */
    val linkShareId: String? = null,
    /** The folder's display name: the one mount a grant login sees, so the guest's `access` opens it. */
    val rootName: String? = null,
) {

    enum class Kind { GUEST, LINK }

    // The grant id and the link's share id are capabilities, so neither reaches a log line.
    override fun toString(): String = "ExchangeGrant(kind=$kind, login=$login)"
}

/** Builds new grants and their secrets from a CSPRNG. */
object ExchangeGrants {

    /** Item BD: the generated password of a grant's login. */
    const val GENERATED_PASSWORD_LENGTH = 24

    /** Item BE: the shortest password the owner may type for a link. */
    const val MIN_LINK_PASSWORD_LENGTH = 8

    private const val LOGIN_PREFIX = "g-"
    private const val LOGIN_ID_CHARS = 8
    private const val PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newGuest(rootUri: String, rootName: String?, guest: String, random: SecureRandom = SecureRandom()) =
        newGrant(ExchangeGrant.Kind.GUEST, rootUri, random).copy(guest = guest.trim(), rootName = rootName)

    fun newLink(rootUri: String, rootName: String?, random: SecureRandom = SecureRandom()) =
        newGrant(ExchangeGrant.Kind.LINK, rootUri, random)
            .copy(linkShareId = SftpShareId.generate(random), rootName = rootName)

    fun generatePassword(random: SecureRandom = SecureRandom()): String =
        String(CharArray(GENERATED_PASSWORD_LENGTH) { PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)] })

    fun isAcceptableLinkPassword(password: String): Boolean = password.length >= MIN_LINK_PASSWORD_LENGTH

    private fun newGrant(kind: ExchangeGrant.Kind, rootUri: String, random: SecureRandom): ExchangeGrant {
        val grantId = SftpShareId.generate(random)
        return ExchangeGrant(
            grantId = grantId,
            kind = kind,
            rootUri = rootUri,
            login = LOGIN_PREFIX + grantId.take(LOGIN_ID_CHARS),
        )
    }
}

/** A link just made: the code to pass on and the password to tell separately (item BE). */
data class ExchangeLinkCreated(val grant: ExchangeGrant, val code: String, val password: String) {
    override fun toString(): String = "ExchangeLinkCreated(${grant.login})"
}
