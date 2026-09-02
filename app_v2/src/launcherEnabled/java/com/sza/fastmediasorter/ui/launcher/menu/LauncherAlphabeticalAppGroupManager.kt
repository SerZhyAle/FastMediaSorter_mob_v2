package com.sza.fastmediasorter.ui.launcher.menu

import com.sza.fastmediasorter.domain.model.launcher.InstalledApp

data class LauncherAppGroupSection(
    val key: String,
    val title: String,
    val apps: List<LauncherAppGridAdapter.AppItem>,
    val isExpanded: Boolean,
    val isPreview: Boolean = false,
)

/** Keeps the launcher-specific alphabetical presentation beside the All Apps screen. */
class LauncherAlphabeticalAppGroupManager {

    fun groupApps(
        apps: List<InstalledApp>,
        columns: Int,
        expandedKeys: Set<String>,
    ): List<LauncherAppGroupSection> {
        if (apps.isEmpty()) return emptyList()
        val appItems = apps.map(::toItem)
        val previewCount = (columns.coerceAtLeast(1) * PREVIEW_ROWS).coerceAtMost(appItems.size)
        val letterGroups = appItems.groupBy { groupKey(it.label) }
            .toSortedMap(::compareGroupKeys)
            .map { (key, groupedApps) ->
                LauncherAppGroupSection(
                    key = key,
                    title = key,
                    apps = groupedApps,
                    isExpanded = key in expandedKeys,
                )
            }
        return listOf(
            LauncherAppGroupSection(
                key = KEY_PREVIEW,
                title = "",
                apps = if (KEY_PREVIEW in expandedKeys) appItems else appItems.take(previewCount),
                isExpanded = KEY_PREVIEW in expandedKeys,
                isPreview = true,
            ),
        ) + letterGroups
    }

    private fun toItem(app: InstalledApp) = LauncherAppGridAdapter.AppItem(
        id = app.packageName,
        label = app.label,
        iconFile = app.iconFile,
        iconVersion = app.lastUpdateTime,
    )

    private fun groupKey(label: String): String {
        val first = label.trim().firstOrNull()?.uppercaseChar() ?: return SYMBOL_GROUP
        return when {
            first in 'A'..'Z' -> first.toString()
            first in 'А'..'Я' -> first.toString()
            else -> SYMBOL_GROUP
        }
    }

    private fun compareGroupKeys(first: String, second: String): Int = when {
        first == second -> 0
        first == SYMBOL_GROUP -> -1
        second == SYMBOL_GROUP -> 1
        first[0] in 'A'..'Z' && second[0] in 'А'..'Я' -> -1
        first[0] in 'А'..'Я' && second[0] in 'A'..'Z' -> 1
        else -> first.compareTo(second)
    }

    companion object {
        /** S2304: read by the panel, which binds an expand route to a swipe. */
        const val KEY_PREVIEW = "preview"
        private const val PREVIEW_ROWS = 2
        private const val SYMBOL_GROUP = "#"
    }
}
