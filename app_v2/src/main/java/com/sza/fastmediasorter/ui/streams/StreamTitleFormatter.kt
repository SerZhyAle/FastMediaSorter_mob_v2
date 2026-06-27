package com.sza.fastmediasorter.ui.streams

/**
 * S0691: stream catalog titles sometimes arrive duplicated as `Name (Name)` (the producer repeats the
 * channel name inside a parenthetical that elsewhere carries a distinguishing variant). When the
 * parenthetical is identical to the head it is pure noise, so the suffix is dropped for display; when it
 * differs (e.g. a region/quality tag) it is kept. Pure + UI-only: the stored [com.sza.fastmediasorter
 * .data.local.db.StreamSourceEntity.title] is never mutated, only what the row/tile renders.
 */
object StreamTitleFormatter {

    /**
     * Returns the title to display: if [title] is exactly `head (paren)` and `paren` equals `head`
     * (trimmed, case-insensitive), returns the trimmed head; otherwise returns [title] unchanged. Only the
     * trailing parenthetical is considered, and only a whole-head duplicate is suppressed, so a genuine
     * distinguishing suffix like `Euronews (FR)` is always preserved.
     */
    fun display(title: String): String {
        val trimmed = title.trim()
        if (!trimmed.endsWith(')')) return title
        val open = trimmed.lastIndexOf(" (")
        if (open <= 0) return title
        val head = trimmed.substring(0, open).trim()
        val paren = trimmed.substring(open + 2, trimmed.length - 1).trim()
        return if (head.isNotEmpty() && head.equals(paren, ignoreCase = true)) head else title
    }
}
