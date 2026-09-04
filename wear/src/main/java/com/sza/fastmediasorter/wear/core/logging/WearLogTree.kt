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
        val line = if (t == null) head else "$head\n${WearSecretMasker.sanitize(Log.getStackTraceString(t))}"
        WearLogBuffer.append(line)

        // S2560: In release builds (where Timber.DebugTree is not planted), output WARN and ERROR
        // to logcat so diagnostics are visible in adb logcat without needing a full debug build.
        if (!BuildConfig.DEBUG && priority >= Log.WARN) {
            val logcatTag = tag ?: "FastMediaSorterWear"
            if (t == null) {
                Log.println(priority, logcatTag, masked)
            } else {
                Log.println(priority, logcatTag, "$masked\n${Log.getStackTraceString(t)}")
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
 * restated here rather than reached for. Kept deliberately narrow: key-value secrets and credentials
 * embedded in a source URI, which are the two shapes this app actually logs.
 */
internal object WearSecretMasker {

    private const val EMPTY = "(empty)"

    private val KEY_VALUE_SECRET = Regex(
        "(password|passwd|pwd|secret|token|apikey|api_key|authorization)\\s*[=:]\\s*(\\S+)",
        RegexOption.IGNORE_CASE
    )

    private val URI_CREDENTIALS = Regex("(://[^:/\\s]+):([^@\\s]+)@")

    fun sanitize(text: String): String {
        val withoutKeyValues = KEY_VALUE_SECRET.replace(text) { match ->
            "${match.groupValues[1]}=${maskFull(match.groupValues[2])}"
        }
        return URI_CREDENTIALS.replace(withoutKeyValues) { match ->
            "${match.groupValues[1]}:${maskFull(match.groupValues[2])}@"
        }
    }

    fun maskFull(value: String?): String {
        if (value.isNullOrEmpty()) {
            return EMPTY
        }
        return "****(${value.length})"
    }
}
