package com.sza.fastmediasorter.core.security

import java.util.concurrent.atomic.AtomicReference

/**
 * The live values DIAGNOSTIC-REPORT section 10 (0.13) makes private - the exchange login, server address,
 * device token, pairing code and certificate fingerprints - held so [SecretMasker.sanitize] and the bundle
 * export can replace them by value. A login or a host has no shape a pattern could find in free text such as
 * an exception message, so the code that reads or receives one registers it here.
 *
 * Only a whole token is replaced: a registered `nas` leaves `nasty` alone, which keeps a short login from
 * shredding unrelated log text. The set only grows for the process lifetime; a value that stopped being
 * current is still private in every line written while it was.
 */
object PrivateDiagnosticValues {

    private const val MIN_LENGTH = 3
    private const val TOKEN_CHAR = """[A-Za-z0-9_.\-]"""

    // A dot or hyphen ends a value unless a word character follows it, so `nas.local.` at a sentence end matches.
    private const val VALUE_END = """(?![A-Za-z0-9_]|[.\-][A-Za-z0-9_])"""

    private val state = AtomicReference(Snapshot(emptySet(), null))

    /** True while no value is held; the export pass then packs each file unchanged. */
    val isEmpty: Boolean
        get() = state.get().pattern == null

    /** Adds every non-blank value of at least three characters; shorter ones would match too much. */
    fun register(vararg values: String?) {
        val accepted = values.mapNotNull { it?.trim()?.takeIf { value -> value.length >= MIN_LENGTH } }
        if (accepted.isEmpty()) return
        state.updateAndGet { current ->
            val merged = current.values + accepted
            if (merged.size == current.values.size) current else Snapshot(merged, buildPattern(merged))
        }
    }

    /** Replaces every whole-token occurrence of a held value with [SecretMasker.REDACTED]. */
    fun redact(text: String): String {
        val pattern = state.get().pattern ?: return text
        return pattern.replace(text, SecretMasker.REDACTED)
    }

    /** Test seam: a unit test that registers values must not leak them into the next test's expectations. */
    internal fun clear() {
        state.set(Snapshot(emptySet(), null))
    }

    // Longest first, so a host that contains a shorter registered login is replaced whole.
    private fun buildPattern(values: Set<String>): Regex {
        val alternatives = values.sortedByDescending(String::length).joinToString("|", transform = Regex::escape)
        return Regex("(?<!$TOKEN_CHAR)(?:$alternatives)$VALUE_END", RegexOption.IGNORE_CASE)
    }

    private data class Snapshot(val values: Set<String>, val pattern: Regex?)
}
