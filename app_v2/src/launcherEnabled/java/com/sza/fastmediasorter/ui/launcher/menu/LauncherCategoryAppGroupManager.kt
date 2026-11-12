package com.sza.fastmediasorter.ui.launcher.menu

import com.sza.fastmediasorter.domain.model.launcher.AppCategory
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp

/**
 * S4530: the all-apps list as category groups.
 *
 * An app appears once in every category it is assigned to; an app with none goes to "Unknown", which is
 * always last and never moves. A category with no installed app is not drawn, yet stays in the catalog
 * so it can still be chosen for an app (strategic §3.1).
 */
class LauncherCategoryAppGroupManager {

    fun groupApps(
        apps: List<InstalledApp>,
        catalog: AppCategoryCatalog,
        expandedKeys: Set<String>,
        reorderTargetKey: String?,
        labelOf: (AppCategory) -> String,
        unknownLabel: String,
    ): List<LauncherAppGroupSection> {
        val membersById = HashMap<String, MutableList<InstalledApp>>()
        val unknown = mutableListOf<InstalledApp>()
        apps.forEach { app ->
            val ids = catalog.categoryIdsOf(app.packageName)
            if (ids.isEmpty()) unknown += app
            ids.forEach { membersById.getOrPut(it) { mutableListOf() } += app }
        }
        val sections = catalog.orderedCategories(labelOf).mapNotNull { category ->
            val members = membersById[category.id] ?: return@mapNotNull null
            section(KEY_PREFIX + category.id, labelOf(category), members, expandedKeys, reorderTargetKey)
        }
        val unknownSection = unknown.takeIf { it.isNotEmpty() }
            ?.let { section(KEY_UNKNOWN, unknownLabel, it, expandedKeys, reorderTargetKey) }
        return sections + listOfNotNull(unknownSection)
    }

    /** The category ids in drawn order, "Unknown" left out - the move use case reorders exactly these. */
    fun displayedCategoryIds(sections: List<LauncherAppGroupSection>): List<String> = sections
        .filter { it.isCategory && it.key != KEY_UNKNOWN }
        .map { it.key.removePrefix(KEY_PREFIX) }

    private fun section(
        key: String,
        title: String,
        members: List<InstalledApp>,
        expandedKeys: Set<String>,
        reorderTargetKey: String?,
    ) = LauncherAppGroupSection(
        key = key,
        title = title,
        apps = members.map(::toItem),
        isExpanded = key in expandedKeys,
        isCategory = true,
        isReorderTarget = key == reorderTargetKey,
    )

    private fun toItem(app: InstalledApp) = LauncherAppGridAdapter.AppItem(
        id = app.packageName,
        label = app.label,
        iconFile = app.iconFile,
        iconVersion = app.lastUpdateTime,
    )

    companion object {
        const val KEY_PREFIX = "category:"

        /** No category id can collide with it: stored ids carry a "system:" or "user:" prefix. */
        const val KEY_UNKNOWN = "category:unknown"

        fun isCategoryKey(key: String): Boolean = key.startsWith(KEY_PREFIX)
    }
}
