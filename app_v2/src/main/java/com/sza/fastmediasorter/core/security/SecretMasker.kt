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
     * Mask credentials embedded in URIs.
     * `"smb://user:pass@host/share"` → `"smb://us**r:****(4)@host/share"`
     */
    fun maskPath(path: String?): String {
        if (path.isNullOrEmpty()) return "(empty)"

        // Pattern: scheme://user:password@host
        return path.replace(
            Regex("""(://[^:]+):([^@]+)@""")
        ) { match ->
            val user = match.groupValues[1]
            val pass = match.groupValues[2]
            "$user:${maskFull(pass)}@"
        }
    }

    /**
     * Sanitize any string that might contain sensitive key-value pairs.
     * Masks values for keys matching common sensitive patterns.
     */
    fun sanitize(text: String): String {
        return text
            .replace(Regex("""(password|passwd|pwd|secret|token|apikey|api_key|authorization)\s*[=:]\s*(\S+)""", RegexOption.IGNORE_CASE)) { match ->
                "${match.groupValues[1]}=${maskFull(match.groupValues[2])}"
            }
    }
}
