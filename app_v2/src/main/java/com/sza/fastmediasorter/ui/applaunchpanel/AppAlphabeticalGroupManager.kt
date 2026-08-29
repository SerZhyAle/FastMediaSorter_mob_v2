package com.sza.fastmediasorter.ui.applaunchpanel

import com.sza.fastmediasorter.domain.model.launcher.InstalledApp

data class AppGroupSection(
    val key: String,
    val title: String,
    val isAllGroup: Boolean = false,
    val isExpanded: Boolean = true,
    val apps: List<InstalledApp>,
)

/**
 * Manages partitioning of installed applications into the top "All" ("Всё") block
 * and alphabetical letter groups (symbols/digits `#`, `A-Z`, `А-Я`).
 */
class AppAlphabeticalGroupManager {

    fun groupApps(
        apps: List<InstalledApp>,
        topAllRowsCount: Int = DEFAULT_TOP_ROWS,
        columnsCount: Int = DEFAULT_COLUMNS,
        expandedKeys: Set<String> = emptySet(),
    ): List<AppGroupSection> {
        if (apps.isEmpty()) return emptyList()

        val topAllLimit = maxOf(columnsCount, topAllRowsCount * columnsCount)
        val allAppsGroup = createAllGroup(apps.take(topAllLimit), expandedKeys)
        val letterMap = buildLetterMap(apps)
        val sortedGroupKeys = letterMap.keys.sortedWith(::compareGroupKeys)

        val letterGroups = sortedGroupKeys.map { key ->
            AppGroupSection(
                key = key,
                title = key,
                isAllGroup = false,
                isExpanded = expandedKeys.contains(key),
                apps = letterMap.getValue(key),
            )
        }

        return listOf(allAppsGroup) + letterGroups
    }

    private fun createAllGroup(apps: List<InstalledApp>, expandedKeys: Set<String>): AppGroupSection {
        return AppGroupSection(
            key = KEY_ALL,
            title = "Всё",
            isAllGroup = true,
            isExpanded = expandedKeys.contains(KEY_ALL) || expandedKeys.isEmpty(),
            apps = apps,
        )
    }

    private fun buildLetterMap(apps: List<InstalledApp>): Map<String, List<InstalledApp>> {
        val map = LinkedHashMap<String, MutableList<InstalledApp>>()
        for (app in apps) {
            val key = resolveGroupKey(app.label)
            map.getOrPut(key) { mutableListOf() }.add(app)
        }
        return map
    }

    private fun resolveGroupKey(label: String): String {
        val firstChar = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return when {
            firstChar in '0'..'9' || !firstChar.isLetter() -> "#"
            firstChar in 'A'..'Z' -> firstChar.toString()
            firstChar in 'А'..'Я' -> firstChar.toString()
            firstChar == 'Ё' -> "Е"
            else -> "#"
        }
    }

    private fun compareGroupKeys(k1: String, k2: String): Int = when {
        k1 == k2 -> 0
        k1 == "#" -> -1
        k2 == "#" -> 1
        k1.first() in 'A'..'Z' && k2.first() in 'А'..'Я' -> -1
        k1.first() in 'А'..'Я' && k2.first() in 'A'..'Z' -> 1
        else -> k1.compareTo(k2)
    }

    companion object {
        const val KEY_ALL = "group_all"
        private const val DEFAULT_TOP_ROWS = 2
        private const val DEFAULT_COLUMNS = 4
    }
}
