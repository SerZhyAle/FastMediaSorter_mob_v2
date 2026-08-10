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

/** What a geographic launcher cell asks the external map application to do. */
enum class LauncherGeographicAction {
    DIRECTIONS,
    NAVIGATION,
    SHOW_PLACE,
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
 * - `act:<actionKey>`    - an action on the launcher itself (keys from `LauncherActionCatalog`, S1402).
 * - `pin:<pkg>:<id>:<label>` - a shortcut another app asked us to pin (S1205).
 * - `geo:<action>:<query>:<label>` - a shared place for directions, navigation, or display (S1175).
 * - `sec:<sectionKey>`   - a titled section header grouping the cells below it (S1428).
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
     * S1402: an action on the launcher itself - open its settings, edit the desktop, leave the mode.
     * Like [ScheduledOp] it cannot be started as an Intent, so the launcher host executes it; the
     * shared executor refuses it on purpose.
     */
    data class LauncherAction(val actionKey: String) : LauncherCellCommand {
        override fun encode(): String = "$PREFIX_LAUNCHER_ACTION$actionKey"
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

    /**
     * S1205: a shortcut a third-party app asked the launcher to pin, started later by its identifier.
     *
     * [label] is a snapshot rather than a lookup, for the same reason [Contact] snapshots a person:
     * the caption has to survive the shortcut itself going away, which is the whole point of keeping
     * the cell when its target disappears. Every field is percent-encoded before being joined, because
     * a publisher's own label may legitimately contain the separator or a newline.
     */
    data class PinnedShortcut(
        val packageName: String,
        val shortcutId: String,
        val label: String,
    ) : LauncherCellCommand {
        override fun encode(): String = PREFIX_PIN + listOf(packageName, shortcutId, label)
            .joinToString(SEPARATOR) { encodeField(it) }
    }

    /**
     * S1175: a place received from another map application.
     *
     * [query] is either coordinates, the received place text, or a URL. It is deliberately not parsed
     * into a coordinate type: Maps can share all three forms and retaining the original query leaves
     * the external map app free to resolve the form it understands. [label] is the cell caption, not
     * an identifier, so an empty value is valid and the visual resolver supplies a fallback.
     */
    data class Geographic(
        val action: LauncherGeographicAction,
        val query: String,
        val label: String,
    ) : LauncherCellCommand {
        override fun encode(): String = PREFIX_GEOGRAPHIC + listOf(action.name, query, label)
            .joinToString(SEPARATOR) { encodeField(it) }
    }

    /**
     * S1428: a titled header owning every cell below it down to the next header.
     *
     * Carries [sectionKey] rather than the title text: strategic §5.3 requires a future user-created
     * group to be a second instance of this command rather than a code change, which holds only while
     * the name is data the resolver looks up.
     */
    data class Section(val sectionKey: String) : LauncherCellCommand {
        override fun encode(): String = "$PREFIX_SECTION$sectionKey"
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
        const val PREFIX_LAUNCHER_ACTION = "act:"
        const val PREFIX_PIN = "pin:"
        const val PREFIX_GEOGRAPHIC = "geo:"
        const val PREFIX_SECTION = "sec:"

        /** The preset section a fresh desktop opens with; §6.2 defers user-created ones to their own ticket. */
        const val SECTION_APP_FUNCTIONS = "app_functions"

        /**
         * What ends [SECTION_APP_FUNCTIONS] (strategic §6.12). Membership is positional and the last
         * section on the desktop has no lower bound, so without a second header the first one would own
         * every cell below it - collapsing the whole desktop instead of the four functions.
         *
         * Named for its position rather than its contents on purpose: the user may drag anything under
         * it, which would make a descriptive title false the moment they did.
         */
        const val SECTION_EVERYTHING_ELSE = "everything_else"

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

        // Field layout of a [PinnedShortcut] target, same append-never-reorder rule as [Contact].
        private const val FIELD_PIN_PACKAGE = 0
        private const val FIELD_PIN_SHORTCUT_ID = 1
        private const val FIELD_PIN_LABEL = 2
        private const val PIN_FIELD_COUNT = 3

        // Field layout of a [Geographic] target. The three fields are a persistence format: append,
        // never reorder, and increase the count when a later version adds a field.
        private const val FIELD_GEOGRAPHIC_ACTION = 0
        private const val FIELD_GEOGRAPHIC_QUERY = 1
        private const val FIELD_GEOGRAPHIC_LABEL = 2
        private const val GEOGRAPHIC_FIELD_COUNT = 3

        /** Tolerant decode: unknown prefix, empty payload, bad id or unknown mode all yield null. */
        fun decode(raw: String?): LauncherCellCommand? {
            val value = raw ?: return null
            return decodeSinglePayload(value) ?: decodeMultiField(value)
        }

        /**
         * The prefixes whose whole payload is one non-empty string. Split from [decodeMultiField]
         * because one `when` over all eleven prefixes exceeds the cyclomatic ceiling; no prefix is a
         * prefix of another, so the two halves can be tried in either order without ambiguity.
         */
        private fun decodeSinglePayload(value: String): LauncherCellCommand? = when {
            value.startsWith(PREFIX_APP) ->
                value.removePrefix(PREFIX_APP).takeIf { it.isNotEmpty() }?.let { App(it) }

            value.startsWith(PREFIX_FEATURE) ->
                value.removePrefix(PREFIX_FEATURE).takeIf { it.isNotEmpty() }?.let { Feature(it) }

            value.startsWith(PREFIX_STREAM) ->
                value.removePrefix(PREFIX_STREAM).takeIf { it.isNotEmpty() }?.let { Stream(it) }

            value.startsWith(PREFIX_OS) ->
                value.removePrefix(PREFIX_OS).takeIf { it.isNotEmpty() }?.let { OsShortcut(it) }

            value.startsWith(PREFIX_LAUNCHER_ACTION) ->
                value.removePrefix(PREFIX_LAUNCHER_ACTION).takeIf { it.isNotEmpty() }
                    ?.let { LauncherAction(it) }

            value.startsWith(PREFIX_SECTION) ->
                value.removePrefix(PREFIX_SECTION).takeIf { it.isNotEmpty() }?.let { Section(it) }

            else -> null
        }

        /** The prefixes whose payload carries a typed id or several joined fields. */
        private fun decodeMultiField(value: String): LauncherCellCommand? = when {
            value.startsWith(PREFIX_RESOURCE) -> decodeResource(value.removePrefix(PREFIX_RESOURCE))

            value.startsWith(PREFIX_SCHEDULED_OP) ->
                value.removePrefix(PREFIX_SCHEDULED_OP).toLongOrNull()?.let { ScheduledOp(it) }

            value.startsWith(PREFIX_FAVORITE_FILE) ->
                decodeFavoriteFile(value.removePrefix(PREFIX_FAVORITE_FILE))

            value.startsWith(PREFIX_CONTACT) ->
                decodeContact(value.removePrefix(PREFIX_CONTACT))

            value.startsWith(PREFIX_PIN) ->
                decodePinnedShortcut(value.removePrefix(PREFIX_PIN))

            value.startsWith(PREFIX_GEOGRAPHIC) ->
                decodeGeographic(value.removePrefix(PREFIX_GEOGRAPHIC))

            else -> null
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
         * Tolerant like [decodeContact]. An empty package or shortcut id yields null because neither
         * can address anything, while an empty label is a real state - a publisher may ship a shortcut
         * with no caption at all, and the cell still has to render.
         */
        private fun decodePinnedShortcut(payload: String): PinnedShortcut? {
            val fields = payload.split(SEPARATOR)
            if (fields.size != PIN_FIELD_COUNT) return null
            val packageName = decodeField(fields[FIELD_PIN_PACKAGE])
            val shortcutId = decodeField(fields[FIELD_PIN_SHORTCUT_ID])
            return if (packageName.isEmpty() || shortcutId.isEmpty()) {
                null
            } else {
                PinnedShortcut(packageName, shortcutId, decodeField(fields[FIELD_PIN_LABEL]))
            }
        }

        /** A bad action or a missing query makes the stored target unusable, so degrade to null. */
        private fun decodeGeographic(payload: String): Geographic? {
            val fields = payload.split(SEPARATOR)
            if (fields.size != GEOGRAPHIC_FIELD_COUNT) return null
            val action = LauncherGeographicAction.entries.firstOrNull {
                it.name == decodeField(fields[FIELD_GEOGRAPHIC_ACTION])
            }
            val query = decodeField(fields[FIELD_GEOGRAPHIC_QUERY])
            return if (action == null || query.isBlank()) {
                null
            } else {
                Geographic(action, query, decodeField(fields[FIELD_GEOGRAPHIC_LABEL]))
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
