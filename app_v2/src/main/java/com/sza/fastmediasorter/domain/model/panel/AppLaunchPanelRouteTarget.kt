package com.sza.fastmediasorter.domain.model.panel

/**
 * The single parser/serializer for an [com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType.INTERNAL_ROUTE]
 * tile's `targetId`. One stored TEXT column carries feature, feature-section, resource and OS targets via
 * a namespace prefix
 * (strategic S0663, tactical INDEX "Storage decision"), so no schema migration is needed:
 *
 * - `fn:<routeKey>`   - one of our own features (calculator, game, ocr, streams, favorites).
 * - `fn-section:<routeKey>:<sectionKey>` - a feature section opened directly.
 * - `resource:<id>` - a specific resource opened directly (several such tiles allowed).
 * - `os:<targetKey>` - a curated OS system target (settings, wifi, ..).
 */
sealed interface AppLaunchPanelRouteTarget {

    fun encode(): String

    data class Feature(val routeKey: String) : AppLaunchPanelRouteTarget {
        override fun encode(): String = "$PREFIX_FEATURE$routeKey"
    }

    /** A feature tile whose entry point accepts a stable section key. */
    data class FeatureSection(val routeKey: String, val sectionKey: String) : AppLaunchPanelRouteTarget {
        override fun encode(): String = "$PREFIX_FEATURE_SECTION$routeKey:$sectionKey"
    }

    data class Resource(val resourceId: Long) : AppLaunchPanelRouteTarget {
        override fun encode(): String = "$PREFIX_RESOURCE$resourceId"
    }

    data class OsShortcut(val targetKey: String) : AppLaunchPanelRouteTarget {
        override fun encode(): String = "$PREFIX_OS$targetKey"
    }

    companion object {
        const val PREFIX_FEATURE = "fn:"
        const val PREFIX_FEATURE_SECTION = "fn-section:"
        const val PREFIX_RESOURCE = "resource:"
        const val PREFIX_OS = "os:"

        /** Tolerant decode: unknown prefix, empty payload or malformed resource id all yield null. */
        fun decode(targetId: String?): AppLaunchPanelRouteTarget? {
            val value = targetId ?: return null
            return when {
                value.startsWith(PREFIX_FEATURE_SECTION) -> {
                    val payload = value.removePrefix(PREFIX_FEATURE_SECTION)
                    val routeKey = payload.substringBefore(':').takeIf { it.isNotEmpty() }
                    val sectionKey = payload.substringAfter(':', "").takeIf { it.isNotEmpty() }
                    if (routeKey != null && sectionKey != null) FeatureSection(routeKey, sectionKey) else null
                }

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
