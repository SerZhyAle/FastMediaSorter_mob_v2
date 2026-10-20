package com.sza.fastmediasorter.domain.model

import java.util.Locale

/**
 * A connection string `FMSEXCH1:` (contract DEVICE-EXCHANGE 5.5): one exchange server and the pin of its
 * certificate. It is not a credential - no password, token or pairing code - and is handled like an address.
 *
 * Only a value the codec can write and read back exists: every field is held in its canonical form and the
 * written text fits the 512-character limit, so `decode(encode(value))` is the same value.
 *
 * @property fingerprint `SHA256:` and 43 characters of standard base64 over the DER leaf; null for a
 * deployment whose leaf validates against a platform trust store.
 * @property name a display hint, never an identity.
 * @property loginHint a login in the form of contract 5.1, lower case; never logged.
 * @property invitation a one-time invitation of 19 item AR, 22 base64url characters. Unlike the rest of the
 * string it is a secret until used: never logged, and never written by a client passing on an entry it holds.
 */
data class ExchangeConnectionString(
    val endpoint: ExchangeEndpoint,
    val fingerprint: String? = null,
    val name: String? = null,
    val loginHint: String? = null,
    val invitation: String? = null,
) {

    init {
        require(ExchangeEndpoint.parse(endpoint.toString()) == endpoint) { "endpoint is not in canonical form" }
        require(fingerprint == null || ExchangeConnectionStringCodec.fingerprintBytes(fingerprint) != null) {
            "fingerprint is not SHA256: and 43 base64 characters"
        }
        require(name == null || ExchangeConnectionStringCodec.isName(name)) { "name breaks the contract rule" }
        require(loginHint == null || isCanonicalLogin(loginHint)) { "login hint breaks the contract rule" }
        require(invitation == null || ExchangeConnectionStringCodec.isInvitation(invitation)) {
            "invitation is not 22 base64url characters of 16 bytes"
        }
        require(ExchangeConnectionStringCodec.encode(this).length <= ExchangeConnectionStringCodec.MAX_TEXT) {
            "the written string exceeds ${ExchangeConnectionStringCodec.MAX_TEXT} characters"
        }
    }

    /** The 32 bytes of the pinned SHA-256, or null when no pin travels with the address. */
    fun fingerprintBytes(): ByteArray? = fingerprint?.let(ExchangeConnectionStringCodec::fingerprintBytes)

    fun encode(): String = ExchangeConnectionStringCodec.encode(this)

    /** A string carrying `i` registers a new account instead of signing in to one (19 item AR). */
    val isInvitation: Boolean get() = invitation != null

    // The login hint and the invitation stay out of every log line; the endpoint names the server.
    override fun toString(): String =
        "ExchangeConnectionString(endpoint=$endpoint, pinned=${fingerprint != null}, invitation=$isInvitation)"

    private fun isCanonicalLogin(login: String): Boolean =
        ExchangeConnectionStringCodec.isLogin(login) && login == login.lowercase(Locale.ROOT)
}

/** The outcome of reading a connection string: a whole value or nothing, never a partial read. */
sealed interface ExchangeConnectionStringResult {

    data class Read(val value: ExchangeConnectionString) : ExchangeConnectionStringResult

    /** [looks] names what a text that is not a connection string resembles, so the message can say so. */
    data class Refused(
        val reason: ExchangeConnectionStringRefusal,
        val looks: ExchangeConnectionStringLookalike? = null,
    ) : ExchangeConnectionStringResult

    /** `FMSEXCH2:` and up: a later format the user needs a newer app for; nothing was read. */
    data class NewerMajor(val major: Int) : ExchangeConnectionStringResult
}

/** The refusal vocabulary of `vectors/connection-string-v1.json`; [code] is the artifact's spelling. */
enum class ExchangeConnectionStringRefusal(val code: String) {
    NOT_A_STRING("not-a-string"),
    TOO_LONG("too-long"),
    WHITESPACE("whitespace"),
    NOT_ASCII("not-ascii"),
    BAD_ESCAPE("bad-escape"),
    BAD_UTF8("bad-utf8"),
    MALFORMED_FIELD("malformed-field"),
    REPEATED_KEY("repeated-key"),
    MISSING_X("missing-x"),
    BAD_X("bad-x"),
    BAD_FP("bad-fp"),
    BAD_N("bad-n"),
    BAD_U("bad-u"),
    BAD_I("bad-i"),
    FILE_TOO_LONG("file-too-long"),
    FILE_LINES("file-lines"),
}

enum class ExchangeConnectionStringLookalike(val code: String) {
    SHARE_CODE("share-code"),
    URL("url"),
}
