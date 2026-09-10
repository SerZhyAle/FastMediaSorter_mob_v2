package com.sza.fastmediasorter.domain.model.youtube

/**
 * S2032: the channel a launcher window cell is bound to.
 *
 * Strategic ADR-2: the cell stores the channel ID, never its name and never a page address - a name
 * changes and one channel answers to several addresses, so both alternatives stop pointing at what the
 * user picked. The title rides alongside it only so a cold desktop with no network still says which
 * channel is placed (strategic Столп 2).
 */
data class YouTubeChannel(
    val channelId: String,
    val title: String,
    val avatarUrl: String? = null,
) {

    /** `channelId|title` - the title goes last because it is the only part allowed to contain the separator. */
    fun encode(): String = "$channelId$SEPARATOR$title"

    companion object {

        private const val SEPARATOR = '|'

        /**
         * A value with no separator is a bare channel ID, which is what an older cell or a hand-written
         * target holds: it decodes to a titleless channel rather than to nothing, so such a cell still
         * plays once its feed answers.
         */
        fun decode(raw: String?): YouTubeChannel? {
            val value = raw?.trim().orEmpty()
            val separator = value.indexOf(SEPARATOR)
            val channelId = if (separator < 0) value else value.substring(0, separator).trim()
            if (channelId.isEmpty()) return null
            val title = if (separator < 0) "" else value.substring(separator + 1).trim()
            return YouTubeChannel(channelId = channelId, title = title)
        }
    }
}
