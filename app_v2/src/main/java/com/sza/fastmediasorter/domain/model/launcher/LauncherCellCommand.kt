package com.sza.fastmediasorter.domain.model.launcher

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

    companion object {
        const val PREFIX_APP = "app:"
        const val PREFIX_FEATURE = "fn:"
        const val PREFIX_RESOURCE = "res:"
        const val PREFIX_STREAM = "stream:"
        const val PREFIX_OS = "os:"
        const val PREFIX_SCHEDULED_OP = "op:"

        private const val SEPARATOR = ":"

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

                else -> null
            }
        }

        private fun decodeResource(payload: String): Resource? {
            val separatorIndex = payload.indexOf(SEPARATOR)
            if (separatorIndex <= 0) return null
            val id = payload.substring(0, separatorIndex).toLongOrNull() ?: return null
            val modeName = payload.substring(separatorIndex + 1)
            val mode = LauncherResourceMode.entries.firstOrNull { it.name == modeName } ?: return null
            return Resource(id, mode)
        }
    }
}
