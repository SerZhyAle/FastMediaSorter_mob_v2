package com.sza.fastmediasorter.core.security

/**
 * Utility for masking sensitive data before logging.
 *
 * Usage:
 * ```
 * Timber.d("Connecting: user=${SecretMasker.mask(username)}, pwd=${SecretMasker.maskFull(password)}")
 * Timber.d("Path: ${SecretMasker.maskPath("smb://user:pass@host/share")}")
 * ```
 */
object SecretMasker {

    private const val SENSITIVE_KEYS = "password|passwd|pwd|secret|token|apikey|api_key|authorization"
    private const val AUTH_SCHEMES = "bearer|basic|digest|negotiate"

    // The scheme word is not the secret: without this group `Authorization: Bearer <t>` masked "Bearer" and logged <t>.
    private val SENSITIVE_VALUE = Regex(
        """($SENSITIVE_KEYS)\s*[=:]\s*(?:($AUTH_SCHEMES)\s+)?(\S+)""",
        RegexOption.IGNORE_CASE,
    )

    // Greedy password up to the LAST `@` of the authority, so `user:p@ss@host` masks `p@ss`, not `p`.
    private val URI_CREDENTIALS = Regex("""(://[^:/@\s]+):([^/\s]+)@""")

    /**
     * Fully mask a value, showing only its presence and length.
     * `"mySecret123"` → `"****(11)"`
     */
    fun maskFull(value: String?): String {
        if (value.isNullOrEmpty()) return "(empty)"
        return "****(${value.length})"
    }

    /**
     * Partial mask: show first 2 and last 1 characters when length > 4.
     * `"admin"` → `"ad**n"`, `"ab"` → `"****(2)"`
     */
    fun mask(value: String?): String {
        if (value.isNullOrEmpty()) return "(empty)"
        if (value.length <= 4) return "****(${value.length})"
        return "${value.take(2)}${"*".repeat(value.length - 3)}${value.last()}"
    }

    /**
     * Mask the password embedded in URIs; the user name stays readable.
     * `"smb://user:pass@host/share"` → `"smb://user:****(4)@host/share"`
     */
    fun maskPath(path: String?): String {
        if (path.isNullOrEmpty()) return "(empty)"
        return path.replace(URI_CREDENTIALS) { match ->
            "${match.groupValues[1]}:${maskFull(match.groupValues[2])}@"
        }
    }

    /**
     * Sanitize any string that might contain sensitive key-value pairs.
     * Masks values for keys matching common sensitive patterns; an HTTP auth scheme stays visible.
     */
    fun sanitize(text: String): String {
        return text.replace(SENSITIVE_VALUE) { match ->
            val key = match.groupValues[1]
            val scheme = match.groupValues[2]
            val masked = maskFull(match.groupValues[3])
            if (scheme.isEmpty()) "$key=$masked" else "$key=$scheme $masked"
        }
    }
}
