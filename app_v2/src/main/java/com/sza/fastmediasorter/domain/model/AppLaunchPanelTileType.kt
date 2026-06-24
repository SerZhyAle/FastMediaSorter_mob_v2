package com.sza.fastmediasorter.domain.model

/**
 * Kind of tile shown in the app-launch panel. [OWN_APP] is FastMediaSorter itself; [EXTERNAL_APP] is a
 * pinned external launcher package. [INTERNAL_ROUTE] and [RESERVED] are modelled-but-unused v1
 * extension points (strategic S0623 §5.3, §6.4) so future tile kinds need no schema migration.
 */
enum class AppLaunchPanelTileType {
    OWN_APP,
    EXTERNAL_APP,
    INTERNAL_ROUTE,
    RESERVED;

    companion object {
        /** Tolerant parser for the persisted type string: unknown/null degrades to [default]. */
        fun fromName(name: String?, default: AppLaunchPanelTileType): AppLaunchPanelTileType =
            entries.firstOrNull { it.name == name } ?: default
    }
}
