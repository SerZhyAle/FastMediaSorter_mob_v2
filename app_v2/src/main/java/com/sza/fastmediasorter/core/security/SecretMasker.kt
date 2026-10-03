package com.sza.fastmediasorter.core.security

/**
 * Utility for masking sensitive data before logging.
 *
 * Usage:
 * ```
 * Timber.d("Connecting: user=${SecretMasker.mask(username)}, pwd=${SecretMasker.maskFull(password)}")
 * Timber.d("Path: ${SecretMasker.maskPath("smb://user:pass@host/share")}")
 * ```
 *
 * [sanitize] follows DIAGNOSTIC-REPORT rule 3 as section 7 (0.11) and section 8 C (0.12) spell it: a secret is
 * recognised by the shape of the text around it, not only by a bare key name, so a JSON blob, a query string or a
 * connection string under a harmless-looking key is still redacted field by field. The wear module restates the
 * same rules in `WearSecretMasker`; a change here belongs there too.
 */
object SecretMasker {

    const val REDACTED = "[REDACTED]"
    const val APP_DATA = "<APP_DATA>"
    private const val EMPTY = "(empty)"
    private const val PARTIAL_MIN_LENGTH = 5
    private const val PARTIAL_HEAD = 2
    private const val GROUP_QUOTED_VALUE = 3
    private const val GROUP_AUTH_SCHEME = 4

    // Matched as a key-name SUFFIX, so `accessPin`, `X-Amz-Signature`, `client_secret` and `refresh_token` count.
    private const val SECRET_SUFFIXES = "password|passwd|pwd|secret|token|apikey|api_key|authorization" +
        "|signature|credential|pin|wmsauthsign|hdnts|hdnea"

    // Too generic for a suffix (`bypass`, `cachePolicy`): only a key that is exactly one of these counts.
    private const val SECRET_EXACT = "pass|auth|policy|key-pair-id"
    private const val AUTH_SCHEMES = "bearer|basic|digest|negotiate"
    private const val KEY_CHAR = """[A-Za-z0-9_.\-]"""

    // The lookbehind anchors every attempt at the start of a key, which keeps a long token-like run linear.
    private const val SECRET_KEY =
        """(?<!$KEY_CHAR)((?:$KEY_CHAR*?(?:$SECRET_SUFFIXES))|(?:$SECRET_EXACT))(?!$KEY_CHAR)"""

    // An optional quote before the separator is the JSON shape `"password":"x"`.
    private const val SECRET_SEPARATOR = """("?\s*[=:]\s*)"""

    // A bare value stops at the next query, connection-string or JSON delimiter instead of swallowing the URL tail.
    private const val SECRET_VALUE_BODY =
        """(?:"((?:\\.|[^"\\])*)"|((?:$AUTH_SCHEMES)\s+)?([^\s&;,"'<>(){}\[\]]+))"""

    private val SECRET_VALUE = Regex(SECRET_KEY + SECRET_SEPARATOR + SECRET_VALUE_BODY, RegexOption.IGNORE_CASE)

    // The password may hold `/`, `?`, `#` or `@`, so the userinfo runs to the LAST `@` of the address; an
    // over-wide match redacts too much, never too little.
    private val URI_USERINFO =
        Regex("""(?<![A-Za-z0-9+.\-])([A-Za-z][A-Za-z0-9+.\-]*://)[^\s/@:"'<>]+:[^\s"'<>]*@""")

    // Xtream-style panels put the account in the path: `/live|movie|series/<user>/<pass>/<id>`.
    private val PATH_CREDENTIALS =
        Regex("""(://[^/\s]+(?:/[^/\s?#]+)*?/(?:live|movie|series)/)[^/\s?#]+/[^/\s?#]+(?=/)""")

    // The package directory names the app; contract rule 3 substitutes the personal directory of a path.
    private val APP_DATA_PATH = Regex("""/data/(?:user(?:_de)?/\d+|data)/[A-Za-z0-9_.]+""")

    /** Replace a value whole: `"mySecret123"` -> `"[REDACTED]"`. */
    fun maskFull(value: String?): String {
        if (value.isNullOrEmpty()) return EMPTY
        return REDACTED
    }

    /**
     * Partial mask for a non-secret identifier such as a user name: first 2 and last 1 characters stay.
     * `"admin"` -> `"ad**n"`, `"ab"` -> `"[REDACTED]"`
     */
    fun mask(value: String?): String {
        if (value.isNullOrEmpty()) return EMPTY
        if (value.length < PARTIAL_MIN_LENGTH) return REDACTED
        return "${value.take(PARTIAL_HEAD)}${"*".repeat(value.length - PARTIAL_HEAD - 1)}${value.last()}"
    }

    /**
     * Mask the credentials a path or URI can carry: userinfo, Xtream path segments and the app data directory.
     * `"smb://user:pass@host/share"` -> `"smb://[REDACTED]@host/share"`
     */
    fun maskPath(path: String?): String {
        if (path.isNullOrEmpty()) return EMPTY
        return maskAppData(maskPathCredentials(maskUserinfo(path)))
    }

    /** Redact every secret shape in free text; a key name and an HTTP auth scheme stay visible. */
    fun sanitize(text: String): String =
        maskAppData(maskPathCredentials(maskSecretValues(maskUserinfo(text))))

    private fun maskUserinfo(text: String): String =
        text.replace(URI_USERINFO) { match -> "${match.groupValues[1]}$REDACTED@" }

    private fun maskSecretValues(text: String): String =
        text.replace(SECRET_VALUE) { match ->
            val head = match.groupValues[1] + match.groupValues[2]
            if (match.groups[GROUP_QUOTED_VALUE] != null) {
                "$head\"$REDACTED\""
            } else {
                "$head${match.groupValues[GROUP_AUTH_SCHEME]}$REDACTED"
            }
        }

    private fun maskPathCredentials(text: String): String =
        text.replace(PATH_CREDENTIALS) { match -> "${match.groupValues[1]}$REDACTED/$REDACTED" }

    private fun maskAppData(text: String): String = text.replace(APP_DATA_PATH, APP_DATA)
}
