package com.sza.fastmediasorter.domain.model.launcher

import java.net.URLDecoder
import java.net.URLEncoder

/** How a resource shortcut opens its target: file browser, slideshow, or plain player. */
enum class LauncherResourceMode {
    BROWSE,
    SLIDESHOW,

    /** Opens the player without slideshow - covers reading a document and starting a playlist. */
    PLAY,
}

/**
 * S0404: the single parser/serializer for a launcher shortcut cell's stored target. One TEXT column
 * carries every command kind via a namespace prefix, so a new kind never forces a schema migration
 * (same pattern as the app-launch panel's [com.sza.fastmediasorter.domain.model.panel.AppLaunchPanelRouteTarget]):
 *
 * - `app:<packageName>`  - launch an installed application.
 * - `fn:<routeKey>`      - one of our own features (keys from `InternalRouteCatalog`).
 * - `res:<id>:<MODE>`    - open a specific resource in a specific view mode.
 * - `stream:<streamId>`  - play a channel from the stream catalog.
 * - `os:<targetKey>`     - a curated OS system target (keys from `OsShortcutCatalog`).
 * - `op:<id>`            - trigger a saved scheduled operation (S1103).
 */
sealed interface LauncherCellCommand {

    fun encode(): String

    data class App(val packageName: String) : LauncherCellCommand {
        override fun encode(): String = "$PREFIX_APP$packageName"
    }

    data class Feature(val routeKey: String) : LauncherCellCommand {
        override fun encode(): String = "$PREFIX_FEATURE$routeKey"
    }

    data class Resource(
        val resourceId: Long,
        val mode: LauncherResourceMode,
    ) : LauncherCellCommand {
        override fun encode(): String = "$PREFIX_RESOURCE$resourceId$SEPARATOR${mode.name}"
    }

    data class Stream(val streamId: String) : LauncherCellCommand {
        override fun encode(): String = "$PREFIX_STREAM$streamId"
    }

    data class OsShortcut(val targetKey: String) : LauncherCellCommand {
        override fun encode(): String = "$PREFIX_OS$targetKey"
    }

    data class ScheduledOp(val operationId: Long) : LauncherCellCommand {
        override fun encode(): String = "$PREFIX_SCHEDULED_OP$operationId"
    }

    /**
     * S1170: one favourite file, opened in its own resource's player exactly where it sits.
     *
     * [Resource] cannot stand in for this: it opens a resource at its start, while the favourites list -
     * on the Android home screen widget and now on the launcher desktop - opens the row the user tapped.
     * That needs the file path alongside the resource, which no other command carries.
     */
    data class FavoriteFile(val resourceId: Long, val filePath: String) : LauncherCellCommand {
        override fun encode(): String = "$PREFIX_FAVORITE_FILE$resourceId$SEPARATOR$filePath"
    }

    /**
     * S1176: a contact pinned to the desktop, with the action its tap performs.
     *
     * Every field is percent-encoded before being joined, so the display name - user data that may
     * legitimately contain the separator, or a newline, or an emoji - cannot corrupt the record. A
     * fixed-field-count split like [Resource]'s would break on the first contact called "Bob: work".
     */
    data class Contact(val target: LauncherContactTarget) : LauncherCellCommand {
        override fun encode(): String = PREFIX_CONTACT + listOf(
            target.action.name,
            target.lookupKey,
            target.phoneNumber,
            target.messageDataId.toString(),
            target.messagePackage,
            target.displayName,
        ).joinToString(SEPARATOR) { encodeField(it) }
    }

    companion object {
        const val PREFIX_APP = "app:"
        const val PREFIX_FEATURE = "fn:"
        const val PREFIX_RESOURCE = "res:"
        const val PREFIX_STREAM = "stream:"
        const val PREFIX_OS = "os:"
        const val PREFIX_SCHEDULED_OP = "op:"
        const val PREFIX_FAVORITE_FILE = "fav:"
        const val PREFIX_CONTACT = "contact:"

        private const val SEPARATOR = ":"

        // Field layout of a [Contact] target. These positions are a persistence format from the first
        // pinned cell: append, never reorder, and bump the count when you do.
        private const val FIELD_ACTION = 0
        private const val FIELD_LOOKUP_KEY = 1
        private const val FIELD_PHONE_NUMBER = 2
        private const val FIELD_DATA_ID = 3
        private const val FIELD_MESSAGE_PACKAGE = 4
        private const val FIELD_DISPLAY_NAME = 5
        private const val CONTACT_FIELD_COUNT = 6

        /** Tolerant decode: unknown prefix, empty payload, bad id or unknown mode all yield null. */
        fun decode(raw: String?): LauncherCellCommand? {
            val value = raw ?: return null
            return when {
                value.startsWith(PREFIX_APP) ->
                    value.removePrefix(PREFIX_APP).takeIf { it.isNotEmpty() }?.let { App(it) }

                value.startsWith(PREFIX_FEATURE) ->
                    value.removePrefix(PREFIX_FEATURE).takeIf { it.isNotEmpty() }?.let { Feature(it) }

                value.startsWith(PREFIX_RESOURCE) -> decodeResource(value.removePrefix(PREFIX_RESOURCE))

                value.startsWith(PREFIX_STREAM) ->
                    value.removePrefix(PREFIX_STREAM).takeIf { it.isNotEmpty() }?.let { Stream(it) }

                value.startsWith(PREFIX_OS) ->
                    value.removePrefix(PREFIX_OS).takeIf { it.isNotEmpty() }?.let { OsShortcut(it) }

                value.startsWith(PREFIX_SCHEDULED_OP) ->
                    value.removePrefix(PREFIX_SCHEDULED_OP).toLongOrNull()?.let { ScheduledOp(it) }

                value.startsWith(PREFIX_FAVORITE_FILE) ->
                    decodeFavoriteFile(value.removePrefix(PREFIX_FAVORITE_FILE))

                value.startsWith(PREFIX_CONTACT) ->
                    decodeContact(value.removePrefix(PREFIX_CONTACT))

                else -> null
            }
        }

        /**
         * Splits on the FIRST separator only - the id never contains one, but a file path routinely
         * does (`content://` authorities, drive letters, timestamps in a name).
         */
        private fun decodeFavoriteFile(payload: String): FavoriteFile? {
            val separatorIndex = payload.indexOf(SEPARATOR)
            if (separatorIndex <= 0) return null
            val id = payload.substring(0, separatorIndex).toLongOrNull()
            val path = payload.substring(separatorIndex + 1)
            return if (id == null || path.isEmpty()) null else FavoriteFile(id, path)
        }

        /**
         * Tolerant like every other case: a truncated record, an unknown action or a non-numeric data
         * id all yield null rather than throwing, so a cell written by a newer build cannot crash an
         * older one - it just renders as unavailable.
         */
        private fun decodeContact(payload: String): Contact? {
            val fields = payload.split(SEPARATOR)
            if (fields.size != CONTACT_FIELD_COUNT) return null
            val action = LauncherContactAction.entries
                .firstOrNull { it.name == decodeField(fields[FIELD_ACTION]) }
            val dataId = decodeField(fields[FIELD_DATA_ID]).toLongOrNull()
            return if (action == null || dataId == null) {
                null
            } else {
                Contact(
                    LauncherContactTarget(
                        action = action,
                        lookupKey = decodeField(fields[FIELD_LOOKUP_KEY]),
                        phoneNumber = decodeField(fields[FIELD_PHONE_NUMBER]),
                        messageDataId = dataId,
                        messagePackage = decodeField(fields[FIELD_MESSAGE_PACKAGE]),
                        displayName = decodeField(fields[FIELD_DISPLAY_NAME]),
                    )
                )
            }
        }

        /**
         * Percent-encoding, not a delimiter-escape scheme: it removes the separator from the alphabet
         * entirely rather than trying to spot escaped ones while splitting. `URLEncoder` escapes `:`,
         * so a joined record can be split naively and every field survives verbatim.
         */
        private fun encodeField(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun decodeField(value: String): String =
            runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault("")

        private fun decodeResource(payload: String): Resource? {
            val separatorIndex = payload.indexOf(SEPARATOR)
            if (separatorIndex <= 0) return null
            val id = payload.substring(0, separatorIndex).toLongOrNull()
            val modeName = payload.substring(separatorIndex + 1)
            val mode = LauncherResourceMode.entries.firstOrNull { it.name == modeName }
            return if (id == null || mode == null) null else Resource(id, mode)
        }
    }
}
