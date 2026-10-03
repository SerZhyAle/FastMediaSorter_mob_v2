package com.sza.fastmediasorter.wear.core.logging

import android.util.Log
import com.sza.fastmediasorter.wear.BuildConfig
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * S1802: Timber tree that feeds [WearLogBuffer].
 *
 * Masking runs here, on the write path, not before sending. A buffer that can physically hold an
 * unmasked credential is one read away from leaking it, and the report is not the only thing that may
 * ever read the buffer.
 */
class WearLogTree(private val minPriority: Int) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= minPriority

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val stamp = TIMESTAMP_FORMAT.format(Instant.now())
        val level = levelLabel(priority)
        val masked = WearSecretMasker.sanitize(message)
        val head = "$stamp $level ${tag ?: NO_TAG} $masked"
        val maskedTrace = t?.let { WearSecretMasker.sanitize(Log.getStackTraceString(it)) }
        val line = if (maskedTrace == null) head else "$head\n$maskedTrace"
        WearLogBuffer.append(line)

        // S2560: In release builds (where Timber.DebugTree is not planted), output WARN and ERROR
        // to logcat so diagnostics are visible in adb logcat without needing a full debug build.
        if (!BuildConfig.DEBUG && priority >= Log.WARN) {
            val logcatTag = tag ?: "FastMediaSorterWear"
            // S3851: the mirror carries the same masked trace as the buffer - logcat is readable by
            // anyone with adb, so an exception message naming a credential must not reach it in clear.
            if (maskedTrace == null) {
                Log.println(priority, logcatTag, masked)
            } else {
                Log.println(priority, logcatTag, "$masked\n$maskedTrace")
            }
        }
    }

    private fun levelLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }

    companion object {
        private const val NO_TAG = "-"

        // DateTimeFormatter, not SimpleDateFormat: log() is called from whatever thread emitted the
        // record, and SimpleDateFormat is mutable internally - concurrent format() calls on one
        // instance garble the output or throw.
        private val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US).withZone(ZoneId.systemDefault())
    }
}

/**
 * S1802: watch-side credential masking.
 *
 * The phone has its own masker in `core/security`, but the two modules share no code, so the rules are
 * restated here rather than reached for: DIAGNOSTIC-REPORT rule 3 as section 7 (0.11) and section 8 C (0.12)
 * spell it, matched by the shape of the text rather than by a bare key name. A change here belongs in the
 * phone's `SecretMasker` too.
 */
internal object WearSecretMasker {

    const val REDACTED = "[REDACTED]"
    const val APP_DATA = "<APP_DATA>"
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

    fun sanitize(text: String): String {
        val withoutUserinfo = URI_USERINFO.replace(text) { match -> "${match.groupValues[1]}$REDACTED@" }
        val withoutValues = SECRET_VALUE.replace(withoutUserinfo) { match ->
            val head = match.groupValues[1] + match.groupValues[2]
            if (match.groups[GROUP_QUOTED_VALUE] != null) {
                "$head\"$REDACTED\""
            } else {
                "$head${match.groupValues[GROUP_AUTH_SCHEME]}$REDACTED"
            }
        }
        val withoutPathAccounts = PATH_CREDENTIALS.replace(withoutValues) { match ->
            "${match.groupValues[1]}$REDACTED/$REDACTED"
        }
        return withoutPathAccounts.replace(APP_DATA_PATH, APP_DATA)
    }
}
