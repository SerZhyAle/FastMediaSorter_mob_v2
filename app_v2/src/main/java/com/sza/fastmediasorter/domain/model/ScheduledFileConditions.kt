package com.sza.fastmediasorter.domain.model

import java.util.concurrent.TimeUnit

/**
 * Optional per-file conditions of a scheduled operation. Every field is independent and a null field is
 * "no condition", so an empty instance selects exactly what the type and time filters already select.
 *
 * Age is measured from [MediaFile.createdDate], the same timestamp the coarse [TimeFilter] uses, so the
 * two never disagree about how old a file is.
 */
data class ScheduledFileConditions(
    /** One or more `*`/`?` globs separated by `;`, matched case-insensitively against the file name. */
    val nameMask: String? = null,
    /** "Older than": the file must be at least this many hours old. */
    val minAgeHours: Int? = null,
    /** "Younger than": the file must be less than this many hours old. */
    val maxAgeHours: Int? = null,
    /** "Larger than": the file must be bigger than this many bytes. */
    val minSizeBytes: Long? = null,
    /** "Smaller than": the file must be smaller than this many bytes. */
    val maxSizeBytes: Long? = null,
) {
    val isEmpty: Boolean
        get() = maskPatterns.isEmpty() && minAgeHours == null && maxAgeHours == null &&
            minSizeBytes == null && maxSizeBytes == null

    /** True when two bounds of the same kind leave no file that could pass both. */
    val hasContradictoryBounds: Boolean
        get() = isEmptyRange(minAgeHours?.toLong(), maxAgeHours?.toLong()) ||
            isEmptyRange(minSizeBytes, maxSizeBytes)

    private val maskPatterns: List<Regex> by lazy { parseMask(nameMask) }

    fun matches(file: MediaFile, now: Long): Boolean =
        matchesName(file.name) && matchesAge(now - file.createdDate) && matchesSize(file.size)

    private fun matchesName(name: String): Boolean =
        maskPatterns.isEmpty() || maskPatterns.any { it.matches(name) }

    private fun matchesAge(ageMs: Long): Boolean {
        val olderThanOk = minAgeHours == null || ageMs >= TimeUnit.HOURS.toMillis(minAgeHours.toLong())
        val youngerThanOk = maxAgeHours == null || ageMs < TimeUnit.HOURS.toMillis(maxAgeHours.toLong())
        return olderThanOk && youngerThanOk
    }

    private fun matchesSize(size: Long): Boolean =
        (minSizeBytes == null || size > minSizeBytes) && (maxSizeBytes == null || size < maxSizeBytes)

    companion object {
        const val MASK_SEPARATOR = ';'

        private fun isEmptyRange(lower: Long?, upper: Long?): Boolean =
            lower != null && upper != null && upper <= lower

        /** Splits on [MASK_SEPARATOR]; blank parts are dropped, so a trailing `;` is harmless. */
        fun parseMask(mask: String?): List<Regex> =
            mask.orEmpty()
                .split(MASK_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { globToRegex(it) }

        private fun globToRegex(glob: String): Regex {
            val pattern = buildString {
                glob.forEach { ch ->
                    when (ch) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        else -> append(Regex.escape(ch.toString()))
                    }
                }
            }
            return Regex(pattern, RegexOption.IGNORE_CASE)
        }
    }
}
