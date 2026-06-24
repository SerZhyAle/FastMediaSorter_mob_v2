package com.sza.fastmediasorter.domain.model.panel

/**
 * The single parser/serializer for an [com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType.INTERNAL_ROUTE]
 * tile's `targetId`. One stored TEXT column carries all three new tile kinds via a namespace prefix
 * (strategic S0663, tactical INDEX "Storage decision"), so no schema migration is needed:
 *
 * - `fn:<routeKey>`   - one of our own features (calculator, game, ocr, streams, favorites).
 * - `resource:<id>`   - a specific resource opened directly (several such tiles allowed).
 * - `os:<targetKey>`  - a curated OS system target (settings, wifi, ..).
 */
sealed interface AppLaunchPanelRouteTarget {

    fun encode(): String

    data class Feature(val routeKey: String) : AppLaunchPanelRouteTarget {
        override fun encode(): String = "$PREFIX_FEATURE$routeKey"
    }

    data class Resource(val resourceId: Long) : AppLaunchPanelRouteTarget {
        override fun encode(): String = "$PREFIX_RESOURCE$resourceId"
    }

    data class OsShortcut(val targetKey: String) : AppLaunchPanelRouteTarget {
        override fun encode(): String = "$PREFIX_OS$targetKey"
    }

    companion object {
        const val PREFIX_FEATURE = "fn:"
        const val PREFIX_RESOURCE = "resource:"
        const val PREFIX_OS = "os:"

        /** Tolerant decode: unknown prefix, empty payload or malformed resource id all yield null. */
        fun decode(targetId: String?): AppLaunchPanelRouteTarget? {
            val value = targetId ?: return null
            return when {
                value.startsWith(PREFIX_FEATURE) ->
                    value.removePrefix(PREFIX_FEATURE).takeIf { it.isNotEmpty() }?.let { Feature(it) }

                value.startsWith(PREFIX_RESOURCE) ->
                    value.removePrefix(PREFIX_RESOURCE).toLongOrNull()?.let { Resource(it) }

                value.startsWith(PREFIX_OS) ->
                    value.removePrefix(PREFIX_OS).takeIf { it.isNotEmpty() }?.let { OsShortcut(it) }

                else -> null
            }
        }
    }
}
